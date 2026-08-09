package org.dhamma.gong.ui

import android.app.Application
import android.os.SystemClock
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
import org.dhamma.gong.data.PlayLogEntity
import org.dhamma.gong.data.ScheduleEventEntity
import org.dhamma.gong.data.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhamma.gong.domain.ActiveCourse
import org.dhamma.gong.domain.ApplianceZone
import org.dhamma.gong.domain.Course
import org.dhamma.gong.domain.PinCode
import org.dhamma.gong.domain.SettingsDefaults
import org.dhamma.gong.schedule.SchedulerEngine
import org.dhamma.gong.service.AppliancePermissions
import org.dhamma.gong.service.GongService
import java.time.LocalDate
import java.time.ZoneId

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

    val mappedDohaSlots: StateFlow<List<Int>> = db.mediaSlots().observeAll()
        .map { rows -> rows.map { it.slot } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
