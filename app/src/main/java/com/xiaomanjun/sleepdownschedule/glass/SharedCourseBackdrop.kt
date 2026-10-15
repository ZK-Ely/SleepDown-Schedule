package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.backdrops.SharedBlurBackdrop

// The course-blur slider replaces this sampler during preview. Only actual consumers must
// recompose; a static local invalidates the whole home provider, including the native Dock.
internal val LocalSharedCourseBackdrop = compositionLocalOf<SharedBlurBackdrop?> { null }
