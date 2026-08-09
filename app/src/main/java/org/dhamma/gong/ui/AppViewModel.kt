package org.dhamma.gong.ui

import android.app.Application
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
import org.dhamma.gong.domain.ActiveCourse
import org.dhamma.gong.domain.Course
import org.dhamma.gong.domain.SettingsDefaults
import org.dhamma.gong.schedule.SchedulerEngine
import org.dhamma.gong.service.GongService
import java.time.LocalDate

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
        enum class Status { ACTIVE, UPCOMING, PAST }
    }

    val courseRows: StateFlow<List<CourseRow>> = combine(
        db.courses().observeAll(),
        db.courseTypes().observeAll(),
        db.settings().observeAll(),
    ) { courses, types, settingRows ->
        val today = LocalDate.now()
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
                    // In window but not chosen (an overlap the scheduler warned about).
                    else -> CourseRow.Status.ACTIVE
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when two courses claim today — the dashboard shows a warning. */
    val overlappingCourses: StateFlow<Boolean> = courseRows
        .map { rows -> rows.count { it.status == CourseRow.Status.ACTIVE } > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
                toast("No doha slots mapped — add files to /sdcard/DhammaGong/doha")
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
