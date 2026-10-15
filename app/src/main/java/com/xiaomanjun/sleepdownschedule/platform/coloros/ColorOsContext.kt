package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects

@Composable
internal fun rememberColorOsSource(): ColorOsNativeUi.Source? {
    val host = LocalContext.current.applicationContext
    val enabled = LocalVisualEffects.current.usesNativeControls
    return remember(host, enabled) { if (enabled) ColorOsNativeUi.source(host) else null }
}

/** Keep the resource owner and class loader paired; hosts refresh dark mode in place. */
@Composable
internal fun rememberNativeContext(dark: Boolean): Pair<ColorOsNativeUi.Source, Context>? {
    val host = LocalContext.current
    val configuration = LocalConfiguration.current
    val source = rememberColorOsSource()
    return remember(source, host, configuration) {
        source?.let { native ->
            ColorOsNativeUi.prepare("create the native theme") { native to native.themedContext(host, dark) }
        }
    }
}
