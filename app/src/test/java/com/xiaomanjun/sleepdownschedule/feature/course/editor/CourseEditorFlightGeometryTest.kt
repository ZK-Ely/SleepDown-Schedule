package com.xiaomanjun.sleepdownschedule.feature.course.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import com.xiaomanjun.sleepdownschedule.CourseEntity
import org.junit.Assert.*
import org.junit.Test

class CourseEditorFlightGeometryTest {
    @Test fun closeTapersTowardItsDestinationAndSettlesAtBothEndpoints() {
        for (delta in listOf(-400f, 400f)) {
            for (step in 0..100) {
                val p = step / 100f
                assertEquals(-courseEditorOpeningTaper(p, delta, 600f),
                    courseEditorOpeningTaper(p, delta, 600f, closing = true), 0.00001f)
            }
            assertEquals(0f, courseEditorOpeningTaper(1f, delta, 600f, closing = true), 0f)
            assertEquals(0f, courseEditorOpeningTaper(0f, delta, 600f, closing = true), 0f)
        }
    }

    @Test fun editorLensCornerScalesWithTheSampleTextureDensity() {
        val shape = CourseEditorMorphCornerShape(112f, 112f, 0.2f, sourceDensity = 3.5f)
        val size = Size(700f, 1200f)
        assertEquals(112f, shape.topStart.toPx(size, Density(3.5f)), 0.001f)
        assertEquals(56f, shape.topStart.toPx(size * 0.5f, Density(1.75f)), 0.001f)
    }

    @Test fun strongerTaperPreservesPositiveLeadingAndTrailingWidths() {
        for (step in 0..100) {
            for (delta in listOf(-2000f, -300f, 0f, 300f, 2000f)) {
                val taper = courseEditorOpeningTaper(step / 100f, delta, 600f)
                assertTrue(taper.isFinite())
                assertTrue("Taper must not fold the shell inside out", kotlin.math.abs(taper) < 0.5f)
            }
        }
        assertTrue(kotlin.math.abs(courseEditorOpeningTaper(0.5f, 300f, 600f)) > 0.2f)
    }

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
