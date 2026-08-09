package org.dhamma.gong.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dhamma.gong.data.CourseTypeEntity
import org.dhamma.gong.data.GongDatabase
import org.dhamma.gong.data.GongRepository
import org.dhamma.gong.data.MediaSlotEntity
import org.dhamma.gong.data.MediaSlotSource
import org.dhamma.gong.data.PlayLogEntity
import org.dhamma.gong.data.ScheduleEventEntity
import org.dhamma.gong.data.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhamma.gong.domain.ActiveCourse
import org.dhamma.gong.domain.ApplianceZone
import org.dhamma.gong.domain.Course
import org.dhamma.gong.domain.DohaPackMapper
import org.dhamma.gong.domain.PinCode
import org.dhamma.gong.domain.SettingsDefaults
import org.dhamma.gong.schedule.SchedulerEngine
import org.dhamma.gong.service.AppliancePermissions
import org.dhamma.gong.service.GongService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The UI's view of the appliance.
 *
 * Everything here is read from Room or from the service's state flows — the
 * activity never owns scheduling or playback state, so closing it changes
 * nothing (design doc §03).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = GongDatabase.get(app)
    private val repo = GongRepository(db)

    // ------------------------------------------------------------ service

    val service: StateFlow<GongService?> = GongService.running

    val schedulerState: StateFlow<SchedulerEngine.State> = service
        .flatMapLatest { it?.scheduler?.state ?: flowOf(SchedulerEngine.State()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulerEngine.State())

    val playerStatus = service
        .flatMapLatest {
            it?.player?.status ?: flowOf(org.dhamma.gong.player.PlayerEngine.Status())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            org.dhamma.gong.player.PlayerEngine.Status(),
        )

    /** One emission per strike, for the dashboard's expanding rings. */
    val strikes = service.flatMapLatest { it?.player?.strikes ?: flowOf() }

    // ------------------------------------------------------------ store

    val courseTypes: StateFlow<List<CourseTypeEntity>> = db.courseTypes().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Map<String, String>> = db.settings().observeAll()
        .map { rows -> SettingsDefaults.all + rows.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsDefaults.all)

    /** The appliance's zone — the `timezone` setting, never the device TZ. */
    val applianceZone: StateFlow<ZoneId> = settings
        .map { ApplianceZone.resolve(it["timezone"]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplianceZone.DEFAULT)

    val events: StateFlow<List<ScheduleEventEntity>> = db.scheduleEvents().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<PlayLogEntity>> = db.playLog().observeRecent(300)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mediaSlots: StateFlow<List<MediaSlotEntity>> = db.mediaSlots().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mappedDohaSlots: StateFlow<List<Int>> = db.mediaSlots().observeAll()
        .map { rows -> rows.map { it.slot } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The persisted SAF tree, or "" when no pack folder has been picked. */
    val dohaTreeUri: StateFlow<String> = settings
        .map { it["doha_tree_uri"].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Courses joined with their type and their status for the courses table. */
    data class CourseRow(
        val course: Course,
        val type: CourseTypeEntity?,
        val status: Status,
    ) {
        enum class Status { ACTIVE, OVERLAP, UPCOMING, PAST }
    }

    val courseRows: StateFlow<List<CourseRow>> = combine(
        db.courses().observeAll(),
        db.courseTypes().observeAll(),
        db.settings().observeAll(),
    ) { courses, types, settingRows ->
        // "Today" in the appliance's zone, matching the scheduler's resolution.
        val zone = ApplianceZone.resolve(settingRows.firstOrNull { it.key == "timezone" }?.value)
        val today = LocalDate.now(zone)
        val byId = types.associateBy { it.id }
        val domainTypes = types.associate { it.id to it.toDomain() }
        val pinned = settingRows.firstOrNull { it.key == "active_course_id" }
            ?.value?.takeIf { it.isNotBlank() }
        val domainCourses = courses.mapNotNull { it.toDomain() }
        val activeId = ActiveCourse.resolve(domainCourses, domainTypes, today, pinned)?.courseId

        domainCourses.map { c ->
            val type = byId[c.courseTypeId]
            val end = c.startDate.plusDays((type?.totalDays ?: 0).toLong())
            CourseRow(
                course = c,
                type = type,
                status = when {
                    c.id == activeId -> CourseRow.Status.ACTIVE
                    today.isBefore(c.startDate) -> CourseRow.Status.UPCOMING
                    today.isAfter(end) -> CourseRow.Status.PAST
                    // In window but not the resolved course: an overlap. Only
                    // ONE course may paint ACTIVE, or staff cannot tell which
                    // schedule is actually firing (FABLE-REVIEW B8).
                    else -> CourseRow.Status.OVERLAP
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when two courses claim today — the dashboard shows a warning. */
    val overlappingCourses: StateFlow<Boolean> = courseRows
        .map { rows -> rows.any { it.status == CourseRow.Status.OVERLAP } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ------------------------------------------------------------ appliance permissions (B6)

    private val _permissionStatus = MutableStateFlow(
        AppliancePermissions.Status(
            notificationsGranted = true,
            exactAlarmsAllowed = true,
            batteryUnrestricted = true,
        ),
    )
    val permissionStatus: StateFlow<AppliancePermissions.Status> = _permissionStatus.asStateFlow()

    fun updatePermissionStatus(status: AppliancePermissions.Status) {
        _permissionStatus.value = status
    }

    fun refreshPermissionStatus() {
        _permissionStatus.value = AppliancePermissions.status(getApplication())
    }

    // ------------------------------------------------------------ pin gate

    /**
     * The stored `admin_pin_hash` row. Null until the first DB emission, so
     * the gate can render *nothing* instead of flashing the dashboard while
     * it does not yet know whether a PIN exists.
     *
     * After the first Room emission the value is always a non-null String
     * (empty when no PIN is set). Mapping a missing row to `""` (not null)
     * is intentional — only the [stateIn] initial value means "still loading".
     */
    val pinHash: StateFlow<String?> = db.settings().observeAll()
        .map { rows -> rows.firstOrNull { it.key == "admin_pin_hash" }?.value.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Unlocked for the life of this ViewModel: opening the app fresh asks
     * again; a rotation does not. Never persisted.
     */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /**
     * Elapsed-realtime mark of the last time the UI went to the background,
     * or 0 when it has not been backgrounded since the last unlock.
     */
    private var backgroundedAt = 0L

    /** Call from the shell on ON_STOP. */
    fun onBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Call from the shell on ON_START. Re-locks when the UI has been away
     * for longer than [graceMs].
     *
     * The grace window exists because the dashboard health rows launch
     * system settings to grant permissions, which backgrounds us — an
     * immediate re-lock would ask staff for the PIN in the middle of a
     * grant. Anything longer than the window is someone walking away, and
     * this appliance runs a foreground service, so the process (and this
     * ViewModel) would otherwise stay unlocked indefinitely.
     */
    fun onForegrounded(graceMs: Long = 60_000) {
        val away = backgroundedAt
        backgroundedAt = 0L
        if (_unlocked.value && away != 0L &&
            SystemClock.elapsedRealtime() - away > graceMs
        ) {
            _unlocked.value = false
        }
    }

    /** @return true and unlocks when [pin] matches the stored hash. */
    suspend fun verifyAndUnlock(pin: String): Boolean {
        val stored = repo.setting("admin_pin_hash")
        val ok = withContext(Dispatchers.Default) { PinCode.verify(pin, stored) }
        if (ok) _unlocked.value = true
        return ok
    }

    /** Set a first PIN or change the existing one (current PIN required). */
    fun setOrChangePin(current: String, newPin: String, confirm: String) {
        viewModelScope.launch {
            val stored = repo.setting("admin_pin_hash")
            when {
                !PinCode.isValidPin(newPin) -> toast("PIN must be 4–8 digits")
                newPin != confirm -> toast("PINs do not match")
                PinCode.isSet(stored) &&
                    !withContext(Dispatchers.Default) { PinCode.verify(current, stored) } ->
                    toast("Current PIN is wrong")
                else -> {
                    val hash = withContext(Dispatchers.Default) { PinCode.hash(newPin) }
                    repo.putSetting("admin_pin_hash", hash)
                    _unlocked.value = true
                    toast("PIN saved")
                }
            }
        }
    }

    /** Remove the PIN entirely; the current PIN is required. */
    fun removePin(current: String) {
        viewModelScope.launch {
            val stored = repo.setting("admin_pin_hash")
            when {
                !PinCode.isSet(stored) -> toast("No PIN is set")
                !withContext(Dispatchers.Default) { PinCode.verify(current, stored) } ->
                    toast("Current PIN is wrong")
                else -> {
                    repo.putSetting("admin_pin_hash", "")
                    toast("PIN removed")
                }
            }
        }
    }

    // ------------------------------------------------------------ doha media pack

    /**
     * What the last scan of the pack folder found. UI-only state — the mapping
     * itself lives in `media_slots`; this is the report staff read beside it.
     */
    data class DohaPack(
        val folderLabel: String = "",
        val viaDohaChild: Boolean = false,
        val scanning: Boolean = false,
        val scannedOnce: Boolean = false,
        /** Set when the folder is unreadable or a re-pick was refused. */
        val banner: String? = null,
        val files: List<DohaPackMapper.ScannedFile> = emptyList(),
        val skipped: List<DohaPackMapper.Skipped> = emptyList(),
        val conflicts: List<DohaPackMapper.Conflict> = emptyList(),
        val unassigned: List<DohaPackMapper.ScannedFile> = emptyList(),
        /** Mapped slots whose URI could not be opened at the last check. */
        val unreadable: Set<Int> = emptySet(),
    )

    private val _dohaPack = MutableStateFlow(DohaPack())
    val dohaPack: StateFlow<DohaPack> = _dohaPack.asStateFlow()

    private val resolver get() = getApplication<Application>().contentResolver

    /**
     * A folder came back from `OpenDocumentTree`.
     *
     * Order is the whole point (design doc "Permission lifecycle on re-pick"):
     * take the new grant **first**, and only on success release the old one,
     * persist, and remap. A take that throws changes nothing at all — a failed
     * re-pick must leave a working appliance working.
     */
    fun onDohaFolderPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val took = runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (took.isFailure) {
                _dohaPack.value = _dohaPack.value.copy(
                    banner = "Android refused lasting access to that folder. " +
                        "Nothing changed — the previous folder and mapping are intact.",
                )
                return@launch
            }

            val previous = repo.setting("doha_tree_uri")
            if (previous.isNotBlank() && previous != uri.toString()) {
                // Persisted grants are a limited per-app resource; a season of
                // re-picks would otherwise leak them.
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(previous),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            repo.putSetting("doha_tree_uri", uri.toString())
            scanDohaTree(uri)
        }
    }

    /** Re-read the persisted folder. Safe to call on every screen entry. */
    fun rescanDohaFolder(announce: Boolean = true) {
        viewModelScope.launch {
            val stored = repo.setting("doha_tree_uri")
            if (stored.isBlank()) {
                _dohaPack.value = DohaPack()
                if (announce) toast("Pick a doha folder first")
                return@launch
            }
            scanDohaTree(Uri.parse(stored), announce)
        }
    }

    /** Staff assign a scanned file to a slot by hand; this outranks auto-map. */
    fun assignDohaSlot(slot: Int, file: DohaPackMapper.ScannedFile) {
        viewModelScope.launch {
            val stamp = if (openable(file.uri)) nowStamp() else null
            db.mediaSlots().put(
                MediaSlotEntity(slot, file.uri, file.name, MediaSlotSource.MANUAL, stamp),
            )
            refreshUnreadable()
            toast("Slot %02d set to %s".format(slot, file.name))
        }
    }

    /** Clearing a slot is always an explicit staff action. */
    fun clearDohaSlot(slot: Int) {
        viewModelScope.launch {
            db.mediaSlots().delete(slot)
            refreshUnreadable()
            toast("Slot %02d cleared".format(slot))
        }
    }

    fun dismissDohaBanner() {
        _dohaPack.value = _dohaPack.value.copy(banner = null)
    }

    private suspend fun scanDohaTree(treeUri: Uri, announce: Boolean = true) {
        _dohaPack.value = _dohaPack.value.copy(scanning = true, banner = null)
        val root = withContext(Dispatchers.IO) { readTree(treeUri) }
        if (root == null) {
            // Keep the last known rows — but stop claiming they are verified.
            val rows = db.mediaSlots().all()
            if (rows.isNotEmpty()) db.mediaSlots().putAll(rows.map { it.copy(verifiedAt = null) })
            _dohaPack.value = _dohaPack.value.copy(
                scanning = false,
                folderLabel = folderLabel(treeUri),
                banner = "That folder is not readable now. The slots below are the " +
                    "last known mapping and are unverified.",
                unreadable = rows.map { it.slot }.toSet(),
            )
            return
        }

        val target = DohaPackMapper.resolveScanTarget(root)
        // Held sources are read *before* the auto rows are cleared, so manual
        // and bundled slots stay protected across the rescan.
        val held = db.mediaSlots().all().associate { it.slot to it.source }
        val mapping = DohaPackMapper.classify(target.files, held)

        db.mediaSlots().deleteBySource(MediaSlotSource.AUTO)
        val stamp = nowStamp()
        val rows = withContext(Dispatchers.IO) {
            mapping.assigned.map { (slot, f) ->
                MediaSlotEntity(
                    slot = slot,
                    uri = f.uri,
                    filename = f.name,
                    source = MediaSlotSource.AUTO,
                    verifiedAt = if (openable(f.uri)) stamp else null,
                )
            }
        }
        if (rows.isNotEmpty()) db.mediaSlots().putAll(rows)

        _dohaPack.value = DohaPack(
            folderLabel = folderLabel(treeUri),
            viaDohaChild = target.viaDohaChild,
            scannedOnce = true,
            files = target.files,
            skipped = mapping.skipped,
            conflicts = mapping.conflicts,
            unassigned = mapping.unassigned,
        )
        refreshUnreadable()
        service.value?.pokeScheduler("doha pack rescanned")
        if (announce) {
            toast(
                if (target.files.isEmpty()) "No D01…D11 files found in that folder"
                else "Mapped ${mapping.assigned.size} of 11 slots",
            )
        }
    }

    /** Re-check every mapped slot. "Mapped" is not the same claim as "will play". */
    private suspend fun refreshUnreadable() {
        val rows = db.mediaSlots().all()
        val bad = withContext(Dispatchers.IO) {
            rows.filterNot { openable(it.uri) }.map { it.slot }.toSet()
        }
        _dohaPack.value = _dohaPack.value.copy(scanning = false, unreadable = bad)
    }

    private fun nowStamp(): String =
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    private fun folderLabel(treeUri: Uri): String =
        runCatching { Uri.decode(treeUri.lastPathSegment).orEmpty() }
            .getOrDefault(treeUri.toString())
            .ifBlank { treeUri.toString() }

    private fun openable(uri: String): Boolean = runCatching {
        val assetPath = uri.removePrefix("asset:///")
        if (assetPath != uri) {
            getApplication<Application>().assets.open(assetPath).close()
        } else {
            resolver.openInputStream(Uri.parse(uri))?.close()
                ?: return false
        }
        true
    }.getOrDefault(false)

    /**
     * One level of SAF listing, plus the immediate children of a `doha/` child.
     * Nothing deeper is read — [DohaPackMapper.resolveScanTarget] decides which
     * of the two the pack actually is.
     *
     * @return null when the tree cannot be read at all.
     */
    private fun readTree(treeUri: Uri): DohaPackMapper.DirNode? {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrNull() ?: return null
        val root = listChildren(treeUri, rootId) ?: return null
        val dirs = root.second.map { (docId, name) ->
            if (name.equals(DohaPackMapper.DOHA_DIR, ignoreCase = true)) {
                DohaPackMapper.DirNode(name, listChildren(treeUri, docId)?.first.orEmpty())
            } else {
                DohaPackMapper.DirNode(name)
            }
        }
        return DohaPackMapper.DirNode(folderLabel(treeUri), root.first, dirs)
    }

    /** @return files to (docId, name) of child directories, or null on failure. */
    private fun listChildren(
        treeUri: Uri,
        docId: String,
    ): Pair<List<DohaPackMapper.ScannedFile>, List<Pair<String, String>>>? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val files = mutableListOf<DohaPackMapper.ScannedFile>()
        val dirs = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dirs += id to name
                } else {
                    files += DohaPackMapper.ScannedFile(
                        name,
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                    )
                }
            }
        } ?: return null
        files to dirs
    }.getOrNull()

    // ------------------------------------------------------------ toasts

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun toast(message: String) {
        _toast.value = message
    }

    fun clearToast() {
        _toast.value = null
    }

    // ------------------------------------------------------------ commands

    /** Every edit saves immediately and toasts — there is no save button. */
    fun setSetting(key: String, value: String, announce: String = "Settings saved") {
        viewModelScope.launch {
            repo.putSetting(key, value)
            service.value?.pokeScheduler("setting $key")
            toast(announce)
        }
    }

    fun toggle(key: String) {
        viewModelScope.launch {
            val next = if (repo.settingBool(key)) "0" else "1"
            repo.putSetting(key, next)
            service.value?.pokeScheduler("toggle $key")
            toast("Settings saved")
        }
    }

    fun addCourse(courseTypeId: Int, startDate: LocalDate?, note: String) {
        if (startDate == null) {
            toast("Pick a start date")
            return
        }
        viewModelScope.launch {
            repo.addCourse(courseTypeId, startDate, note)
            service.value?.pokeScheduler("course added")
            toast("Course added")
        }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch {
            repo.deleteCourse(id)
            service.value?.pokeScheduler("course deleted")
            toast("Course removed")
        }
    }

    fun addEvent(row: ScheduleEventEntity) {
        viewModelScope.launch {
            db.scheduleEvents().insert(row)
            service.value?.pokeScheduler("schedule edited")
            toast("Event saved")
        }
    }

    fun updateEvent(row: ScheduleEventEntity) {
        viewModelScope.launch {
            db.scheduleEvents().upsert(row)
            service.value?.pokeScheduler("schedule edited")
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            db.scheduleEvents().delete(id)
            service.value?.pokeScheduler("schedule edited")
            toast("Event removed")
        }
    }

    fun testGong() {
        viewModelScope.launch { service.value?.testGong() }
    }

    fun testDoha() {
        viewModelScope.launch {
            val slots = mappedDohaSlots.value
            if (slots.isEmpty()) {
                toast("No doha media installed — release builds are GONGS ONLY until a pack is added")
                return@launch
            }
            service.value?.testDoha(slots.first())
        }
    }

    fun stop() {
        viewModelScope.launch { service.value?.stopPlayback() }
    }

    fun confirmClock() {
        viewModelScope.launch {
            service.value?.confirmClock()
            toast("Clock confirmed")
        }
    }
}

fun CourseTypeEntity.toDomainType() = toDomain()
