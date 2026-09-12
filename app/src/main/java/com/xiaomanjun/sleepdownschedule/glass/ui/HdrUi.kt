package com.xiaomanjun.sleepdownschedule.glass.ui

import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

internal const val CourseHdrHeadroom = 2f
internal val LocalCourseHdrUi = compositionLocalOf { false }

internal fun supportsCourseHdrUi(
    sdk: Int,
    hardwareAccelerated: Boolean,
    hdrDisplay: Boolean,
    wideColorWindow: Boolean,
    hdrSdrRatioAvailable: Boolean
): Boolean = sdk >= 35 && hardwareAccelerated && hdrDisplay && wideColorWindow && hdrSdrRatioAvailable

/**
 * Only the home window opts in. Other windows/previews keep their SDR drawing path.
 * API 35 lets us bound HDR headroom instead of asking the panel for maximum brightness.
 * This is a request: Android still tone maps to the headroom allowed by the current display.
 */
@Composable
internal fun ProvideCourseHdrUi(window: Window, enabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val wideColorWindow = Build.VERSION.SDK_INT >= 35 && configuration.isScreenWideColorGamut
    var active by remember(window, view) { mutableStateOf(false) }
    DisposableEffect(window, view, enabled, wideColorWindow) {
        if (Build.VERSION.SDK_INT < 35 || !enabled) {
            active = false
            onDispose { }
        } else {
            val originalColorMode = window.colorMode
            val originalHeadroom = window.desiredHdrHeadroom
            val displays = view.context.getSystemService(DisplayManager::class.java)
            var requested = false
            fun update() {
                val display = view.display
                val supported = supportsCourseHdrUi(
                    Build.VERSION.SDK_INT,
                    view.isHardwareAccelerated,
                    display?.isHdr == true,
                    wideColorWindow && display?.isWideColorGamut == true,
                    // ViewRootImpl demotes HDR to WCG when this capability is absent, even
                    // when the same screen advertises HDR video formats via isHdr().
                    display?.isHdrSdrRatioAvailable == true
                )
                if (supported == requested) return
                requested = supported
                // Never switch color mode with pager progress, blur suspension or morph frames.
                window.colorMode = if (supported) ActivityInfo.COLOR_MODE_HDR else originalColorMode
                window.desiredHdrHeadroom = if (supported) CourseHdrHeadroom else originalHeadroom
                active = supported
                Log.i("CourseHdrUi", "HDR outline requested=$supported, headroom=${if (supported) CourseHdrHeadroom else 1f}")
            }
            val displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = update()
                override fun onDisplayRemoved(displayId: Int) = update()
                override fun onDisplayChanged(displayId: Int) = update()
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = update()
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            view.addOnAttachStateChangeListener(attachListener)
            update()
            onDispose {
                displays.unregisterDisplayListener(displayListener)
                view.removeOnAttachStateChangeListener(attachListener)
                if (requested) {
                    window.colorMode = originalColorMode
                    window.desiredHdrHeadroom = originalHeadroom
                }
                active = false
            }
        }
    }
    CompositionLocalProvider(LocalCourseHdrUi provides (enabled && active), content = content)
}
