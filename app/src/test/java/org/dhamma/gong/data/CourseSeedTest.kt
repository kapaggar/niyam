package org.dhamma.gong.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The bundled centre calendar. Each guard here protects against a way this
 * feature could silently start or silently lose a course schedule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseSeedTest {

    private lateinit var db: GongDatabase
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GongDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedTypes() =
        SeedLoader.apply(db, SeedLoader.readAsset(context))

    private fun calendar() = requireNotNull(SeedLoader.readCoursesAsset(context)) {
        "the build must ship seed/courses.json"
    }

    // ------------------------------------------------------------ the asset

    @Test
    fun theBundledCalendarIsTheSudhaOne() {
        val seed = calendar()
        assertEquals("Dhamma Sudha", seed.centre)
        assertEquals(39, seed.courses.size)
    }

    @Test
    fun everyBundledCourseNamesAKnownTypeAndAValidDate() {
        // A course pointing at a missing type resolves to no schedule: it would
        // sit in the list looking real and ring nothing.
        val known = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        for (course in calendar().courses) {
            assertTrue(
                "unknown course_type_id ${course.courseTypeId}",
                course.courseTypeId in known,
            )
            LocalDate.parse(course.startDate)
        }
    }

    @Test
    fun noTwoCoursesShareAnArrivalDate() {
        // Two windows opening the same day is a permanent OVERLAP the dashboard
        // has to resolve every morning.
        val starts = calendar().courses.map { it.startDate }
        assertEquals(starts.size, starts.toSet().size)
    }

    @Test
    fun theCalendarCoversTwoFullYears() {
        val starts = calendar().courses.map { LocalDate.parse(it.startDate) }.sorted()
        assertEquals(2026, starts.first().year)
        assertEquals(2027, starts.last().year)
    }

    // ------------------------------------------------------------ applying

    @Test
    fun afreshInstallGetsTheWholeCalendar() = runTest {
        seedTypes()
        val inserted = SeedLoader.applyCourses(db, calendar())
        assertEquals(39, inserted)
        assertEquals(39, db.courses().count())
    }

    @Test
    fun applyingTwiceDoesNotDuplicate() = runTest {
        seedTypes()
        SeedLoader.applyCourses(db, calendar())
        val second = SeedLoader.applyCourses(db, calendar())
        assertEquals(0, second)
        assertEquals(39, db.courses().count())
    }

    @Test
    fun deletingEveryCourseDoesNotResurrectTheCalendar() = runTest {
        // Staff who empty the table have decided the calendar is wrong.
        // Re-adding thirty-nine courses on the next launch would be the
        // appliance arguing back, and each one silently starts a schedule.
        seedTypes()
        SeedLoader.applyCourses(db, calendar())
        for (row in db.courses().all()) db.courses().delete(row.id)
        assertEquals(0, db.courses().count())

        SeedLoader.applyCourses(db, calendar())
        assertEquals(0, db.courses().count())
    }

    @Test
    fun aTabletWithHandEnteredCoursesIsLeftAlone() = runTest {
        // Landing a calendar on top of existing courses would produce
        // overlapping windows and a dashboard that cannot say which is running.
        seedTypes()
        db.courses().insert(CourseEntity(courseTypeId = 1, startDate = "2026-08-07"))

        val inserted = SeedLoader.applyCourses(db, calendar())
        assertEquals(0, inserted)
        assertEquals(1, db.courses().count())
        // Marked anyway, so it is not reconsidered on every launch.
        assertNotNull(db.state().get(SeedLoader.COURSES_SEEDED_KEY))
    }

    @Test
    fun coursesWithAnUnknownTypeAreDroppedNotInserted() = runTest {
        seedTypes()
        val seed = SeedLoader.CourseSeed(
            source = "test",
            courses = listOf(
                SeedLoader.SeedCourse(1, "2026-08-05"),
                SeedLoader.SeedCourse(99, "2026-08-20"),
            ),
        )
        assertEquals(1, SeedLoader.applyCourses(db, seed))
        assertEquals(1, db.courses().count())
        assertEquals("2026-08-05", db.courses().all().single().startDate)
    }

    @Test
    fun theMarkerIsAbsentBeforeTheFirstApply() = runTest {
        seedTypes()
        assertNull(db.state().get(SeedLoader.COURSES_SEEDED_KEY))
    }

    @Test
    fun seededCoursesResolveThroughTheNormalCourseLookup() = runTest {
        // The point of seeding: a tablet switched on mid-course finds its day
        // without anyone touching the UI.
        seedTypes()
        SeedLoader.applyCourses(db, calendar())

        val rows = db.courses().all()
        val tenDay = rows.first { it.startDate == "2026-08-05" }
        assertEquals(1, tenDay.courseTypeId)
    }
}
