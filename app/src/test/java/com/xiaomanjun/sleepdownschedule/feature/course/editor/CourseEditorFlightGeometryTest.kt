package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.runBlocking
import com.xiaomanjun.sleepdownschedule.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class CourseEditorFlightGeometryTest {
    @Test fun upperAndLowerSourcesHaveOppositeTrailingEdgesAndUndistortedEndpoints() {
        val upper = courseEditorOpeningTaper(0.4f, -400f, 600f)
        assertTrue(upper > 0f)
        assertEquals(-upper, courseEditorOpeningTaper(0.4f, 400f, 600f), 0.00001f)
        for (y in listOf(-500f, 0f, 500f)) {
            assertEquals(0f, courseEditorOpeningTaper(0f, y, 600f), 0f)
            assertEquals(0f, courseEditorOpeningTaper(1f, y, 600f), 0f)
        }
        assertEquals(0f, courseEditorOpeningTaper(0.4f, 0f, 600f), 0f)
    }

    @Test fun copyLandingFollowsDestinationColumnAndConfiguredPeriodOrder() {
        val source = CourseEntity(name = "课程", teacher = null, location = null, weekday = 2,
            periods = listOf(4, 6), weeks = listOf(1),
            weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL, note = null)
        val destination = source.copy(weekday = 4, periods = listOf(8, 10))
        val result = courseEditorWeekLandingBounds(
            Rect(100f, 210f, 180f, 366f), source, destination,
            listOf(2, 4, 6, 8, 10), rowHeight = 80f, gap = 4f
        )
        assertEquals(Rect(268f, 370f, 348f, 526f), result)
        assertNull(courseEditorWeekLandingBounds(Rect.Zero, source, destination, emptyList(), 80f, 4f))
    }

    @Test fun gridLandingHandlesNewWeekendColumnRtlAndSeparatePeriodRuns() = runBlocking {
        val course = CourseEntity(name = "课程", teacher = null, location = null, weekday = 7,
            periods = listOf(2, 3, 6), weeks = listOf(1),
            weekParity = com.xiaomanjun.sleepdownschedule.WeekParity.ALL, note = null)
        val grid = CourseEditorWeekGrid(Offset(20f, 100f), 700f, 80f, 4f,
            (1..8).toList(), null, 0, false)
        assertEquals(Rect(622f, 182f, 718f, 338f), grid.reveal(course, (1..7).toList()))
        assertEquals(Rect(22f, 182f, 118f, 338f), grid.copy(rightToLeft = true).reveal(course, (1..7).toList()))
        assertNull(grid.reveal(course, (1..5).toList()))
    }
}
