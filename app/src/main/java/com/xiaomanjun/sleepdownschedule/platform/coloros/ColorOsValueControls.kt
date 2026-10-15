package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*

@Composable
internal fun ColorOsSlider(request: NativeSliderRequest): Boolean {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val native = rememberNativeContext(dark) ?: return false
    val latest = rememberUpdatedState(request)
    val session = remember(native, request.range) { NativeSliderSession { latest.value } }
    val binding = remember(native, session) {
        ColorOsNativeUi.prepare("create native value slider") {
            native.first.updateTheme(native.second, dark)
            NativeSliderBinding(native.first, native.second, { latest.value }, session)
        }
    } ?: return false
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    key(binding.view) { AndroidView(factory = { binding.view }, modifier = request.modifier.fillMaxWidth().height(48.dp), update = {
        it.isEnabled = request.enabled
        binding.sync(request.value(), accent)
    }) }
    DisposableEffect(binding) { onDispose { binding.dispose() } }
    return true
}

private class NativeSliderBinding(source: ColorOsNativeUi.Source, context: Context,
    private val request: () -> NativeSliderRequest, private val session: NativeSliderSession) {
    private val type = source.load("com.coui.appcompat.seekbar.COUISeekBar")
    val view = type.getConstructor(Context::class.java).newInstance(context) as View
    private val listenerType = source.load("${type.name}\$OnSeekBarChangeListener")
    private val setListener = type.getMethod("setOnSeekBarChangeListener", listenerType)
    private val setProgress = type.getMethod("setProgress", Int::class.javaPrimitiveType)
    private val getProgress = type.getMethod("getProgress")
    private val setProgressColor = type.getMethod("setProgressColor", ColorStateList::class.java)
    private val marker = NativeSliderMarker(view, request)
    private val progressAdapter = NativeSliderProgressAdapter(request, session) { setProgress.invoke(view, it) }
    private val clickAnimation = type.getMethod("getMClickAnimHandler").invoke(view).let { handler ->
        handler.javaClass.getMethod("getClickAnim").invoke(handler)
    }
    private val animationUpdateType = source.load("com.coui.appcompat.animation.dynamicanimation.COUIDynamicAnimation\$OnAnimationUpdateListener")
    // COUI updates its own progress on every click-spring frame, but reports the public
    // onProgressChanged callback only at the endpoint. Observe the same spring for preview.
    private val clickUpdate = source.listener(animationUpdateType.name) { name, _ ->
        if (name == "onAnimationUpdate" && session.tracking) {
            progressAdapter.changed(getProgress.invoke(view) as Int, fromUser = true, snapToDefault = false)
        }
        null
    }
    private var cancelled = false
    private var color: Int? = null
    init {
        type.getMethod("setMax", Int::class.javaPrimitiveType).invoke(view, NativeSliderProgressAdapter.MaxProgress)
        clickAnimation.javaClass.getMethod("addUpdateListener", animationUpdateType).invoke(clickAnimation, clickUpdate)
        view.overlay.add(marker)
        view.addOnLayoutChangeListener(marker)
        setListener.invoke(view, source.listener(listenerType.name) { name, args ->
            when (name) {
                "onStartTrackingTouch" -> session.start()
                "onProgressChanged" -> if (args != null) progressAdapter.changed(args[1] as Int, args[2] == true)
                "onStopTrackingTouch" -> session.finish(cancelled)
            }
            null
        })
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) cancelled = false
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                cancelled = true
                view.post { if (cancelled) session.finish(true) }
            }
            false
        }
        source.bindFollowLightIntensity(view)
    }
    fun sync(value: Float, accent: Int) {
        marker.invalidateSelf()
        if (!session.tracking) {
            val progress = progressAdapter.progressFor(value)
            if (getProgress.invoke(view) != progress) setProgress.invoke(view, progress)
        }
        if (color != accent) {
            setProgressColor.invoke(view, nativeControlAccent(accent))
            color = accent
        }
    }
    fun dispose() {
        clickAnimation.javaClass.getMethod("removeUpdateListener", animationUpdateType).invoke(clickAnimation, clickUpdate)
        view.overlay.remove(marker)
        view.removeOnLayoutChangeListener(marker)
        setListener.invoke(view, null)
        view.setOnTouchListener(null)
        session.finish(true)
    }
}

/** COUI measures progress between its padded endpoints, not the AndroidView's outer edges. */
private class NativeSliderMarker(private val view: View,
    private val request: () -> NativeSliderRequest) : Drawable(), View.OnLayoutChangeListener {
    private val start = view.javaClass.getMethod("getProgressStart")
    private val length = view.javaClass.getMethod("getSeekBarLength")
    private val center = view.javaClass.getMethod("getSeekBarCenterY")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC7FFFFFF.toInt() }
    private val radius = 2.5f * view.resources.displayMetrics.density

    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        setBounds(0, 0, view.width, view.height)
    }

    override fun draw(canvas: Canvas) {
        val current = request()
        val snap = current.snapValue ?: return
        val span = current.range.endInclusive - current.range.start
        if (span <= 0f || view.width == 0) return
        val fraction = ((snap - current.range.start) / span).coerceIn(0f, 1f)
        val visual = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1f - fraction else fraction
        val x = (start.invoke(view) as Float) + (length.invoke(view) as Float) * visual
        canvas.drawCircle(x, (center.invoke(view) as Int).toFloat(), radius, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

@Composable
internal fun ColorOsSwitch(request: NativeSwitchRequest): Boolean {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val native = rememberNativeContext(dark) ?: return false
    val latest = rememberUpdatedState(request)
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val binding = remember(native) {
        ColorOsNativeUi.prepare("create native switch") {
            native.first.updateTheme(native.second, dark)
            NativeSwitchBinding(native.first, native.second) { latest.value }
        }
    } ?: return false
    key(binding.view) { AndroidView(factory = { binding.view }, modifier = request.modifier,
        update = { binding.sync(request, accent) }) }
    DisposableEffect(binding) { onDispose { binding.view.setOnCheckedChangeListener(null) } }
    return true
}

/** COUISwitch's preference style is non-clickable: standalone controls must enable input. */
internal fun bindStandaloneSwitch(view: CompoundButton, request: () -> NativeSwitchRequest) {
    view.isClickable = true
    view.isFocusable = true
    view.setOnCheckedChangeListener { _, checked ->
        val current = request()
        if (current.enabled && checked != current.checked) current.onCheckedChange(checked)
    }
}

private fun nativeControlAccent(accent: Int) = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
    intArrayOf((accent and 0x00FFFFFF) or (0x61 shl 24), accent)
)

private class NativeSwitchBinding(source: ColorOsNativeUi.Source, context: Context,
    private val request: () -> NativeSwitchRequest) {
    private val type = source.load("com.coui.appcompat.couiswitch.COUISwitch")
    val view = type.getConstructor(Context::class.java).newInstance(context) as CompoundButton
    private val setColor = type.getMethod("setBarCheckedColor", Int::class.javaPrimitiveType)
    private val setDisabledColor = type.getMethod("setBarCheckedDisabledColor", Int::class.javaPrimitiveType)
    private var color: Int? = null
    init { source.bindFollowLightIntensity(view) }
    fun sync(value: NativeSwitchRequest, accent: Int) {
        view.setOnCheckedChangeListener(null)
        view.isEnabled = value.enabled
        if (view.isChecked != value.checked) view.isChecked = value.checked
        if (color != accent) {
            setColor.invoke(view, accent)
            setDisabledColor.invoke(view, nativeControlAccent(accent).getColorForState(
                intArrayOf(-android.R.attr.state_enabled), accent))
            color = accent
        }
        bindStandaloneSwitch(view, request)
    }
}

@Composable
internal fun ColorOsSegments(request: NativeSegmentRequest): Boolean {
    if (request.labels.isEmpty()) return false
    val native = rememberNativeContext(request.dark) ?: return false
    val latest = rememberUpdatedState(request)
    val binding = remember(native, request.labels) {
        ColorOsNativeUi.prepare("create native segmented control") {
            val source = native.first
            val type = source.load("com.coui.appcompat.segmentbutton.COUISegmentButtonLayout")
            val view = type.getConstructor(Context::class.java).newInstance(native.second) as View
            type.getMethod("setSegmentButtons", Array<String>::class.java)
                .invoke(view, request.labels.toTypedArray() as Any)
            type.getMethod("setMaterialEffectEnabled", Boolean::class.javaPrimitiveType).invoke(view, true)
            type.getMethod("setSegmentHeight", Float::class.javaPrimitiveType).invoke(view, 42f)
            type.getMethod("selectSegmentAt", Int::class.javaPrimitiveType).invoke(view, request.selected.coerceIn(request.labels.indices))
            val listener = source.load("${type.name}\$OnSegmentSelectedListener")
            type.getMethod("setOnSegmentSelectedListener", listener).invoke(view, source.listener(listener.name) { name, args ->
                if (name == "onSegmentSelected") (args?.firstOrNull() as? Int)?.let {
                    if (it in latest.value.labels.indices && it != latest.value.selected) latest.value.onSelected(it)
                }
                null
            })
            source.bindFollowLightIntensity(view)
            Triple(type, view, listener)
        }
    } ?: return false
    key(binding.second) { AndroidView(factory = { binding.second }, modifier = request.modifier.height(42.dp), update = {
        val selected = request.selected.coerceIn(request.labels.indices)
        if (binding.first.getMethod("getSelectedSegmentPosition").invoke(it) != selected)
            binding.first.getMethod("selectSegmentAt", Int::class.javaPrimitiveType).invoke(it, selected)
    }) }
    DisposableEffect(binding) { onDispose {
        binding.first.getMethod("setOnSegmentSelectedListener", binding.third).invoke(binding.second, null)
    } }
    return true
}
