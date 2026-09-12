package com.xiaomanjun.sleepdownschedule.glass.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseHdrUiTest {
    @Test
    fun mediaHdrSupportAloneDoesNotEnableUnboundedOrSoftwareHdrUi() {
        assertFalse(supportsCourseHdrUi(34, true, true, true, true))
        assertFalse(supportsCourseHdrUi(35, false, true, true, true))
        assertFalse(supportsCourseHdrUi(35, true, true, false, true))
        assertFalse(supportsCourseHdrUi(35, true, true, true, false))
    }

    @Test
    fun movingToSdrDisplayFallsBackWhileNewerHdrDisplaysRemainSupported() {
        assertTrue(supportsCourseHdrUi(35, true, true, true, true))
        assertTrue(supportsCourseHdrUi(37, true, true, true, true))
        assertFalse(supportsCourseHdrUi(37, true, false, true, true))
        assertFalse(supportsCourseHdrUi(37, true, false, false, false))
    }

    @Test
    fun coloredCoreExceedsSdrWhiteWithoutDarkeningAnotherChannel() {
        val blue = courseHdrLightColor(Color.Blue)
        assertEquals(ColorSpaces.LinearExtendedSrgb, blue.colorSpace)
        assertEquals(1f, blue.red, 0.001f)
        assertEquals(1f, blue.green, 0.001f)
        assertEquals(CourseHdrHeadroom, blue.blue, 0.001f)
        assertEquals(1f, blue.alpha, 0.001f)
        val red = courseHdrLightColor(Color.Red)
        assertTrue(red.red > red.green && red.red > red.blue)
    }

    @Test
    fun extendedInputCannotExceedTheRequestedHeadroom() {
        val bright = courseHdrLightColor(Color(4f, -0.2f, 1f, 1f, ColorSpaces.LinearExtendedSrgb))
        assertEquals(CourseHdrHeadroom, bright.red, 0.001f)
        assertEquals(1f, bright.green, 0.001f)
        assertEquals(CourseHdrHeadroom, bright.blue, 0.001f)
    }
}
