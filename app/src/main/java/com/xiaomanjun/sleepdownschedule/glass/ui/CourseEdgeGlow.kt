package com.xiaomanjun.sleepdownschedule.glass.ui

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/** Scale linear RGB together: extra headroom must brighten the course hue, not add white. */
internal fun courseHdrLightColor(color: Color): Color {
    val linear = color.convert(ColorSpaces.LinearExtendedSrgb)
    val r = linear.red.coerceAtLeast(0f)
    val g = linear.green.coerceAtLeast(0f)
    val b = linear.blue.coerceAtLeast(0f)
    val peak = maxOf(r, g, b)
    val gain = if (peak > 0f) CourseHdrHeadroom / peak else 0f
    return Color(r * gain, g * gain, b * gain, 1f, ColorSpaces.LinearExtendedSrgb)
}

@RequiresApi(33)
internal fun courseHdrLightPaint(color: Color, bounds: Rect, radius: Float, feather: Float, strength: Float): Paint {
    val light = courseHdrLightColor(color)
    val packed = android.graphics.Color.pack(
        light.red, light.green, light.blue, light.alpha,
        android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)
    )
    val lightShader = RuntimeShader(CourseHdrLightShader).apply {
        setColorUniform("lightColor", packed)
        setFloatUniform("bounds", bounds.left, bounds.top, bounds.width, bounds.height)
        setFloatUniform("radius", radius.coerceIn(0f, minOf(bounds.width, bounds.height) / 2f))
        setFloatUniform("feather", minOf(feather, bounds.height / 2f).coerceAtLeast(1f))
        setFloatUniform("strength", strength.coerceIn(0f, 1f))
    }
    return Paint().apply {
        // Keep both color and coverage floating point: ARGB8 vertex alpha caused visible
        // steps where super-white light faded back to SDR. This shader samples no backdrop.
        // Lighten retains already-brighter backdrop channels. Unlike an opaque colored overlay
        // it cannot darken a pale wallpaper, and unlike Screen it can raise a white channel >1.
        blendMode = BlendMode.Lighten
        shader = lightShader
    }
}

private const val CourseHdrLightShader = """
    layout(color) uniform half4 lightColor;
    uniform float4 bounds;
    uniform float radius;
    uniform float feather;
    uniform float strength;

    float ease(float t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    half4 main(float2 xy) {
        float2 p = xy - bounds.xy;
        float2 halfSize = bounds.zw * 0.5;
        float2 q = abs(p - halfSize) - (halfSize - radius);
        float inside = radius - length(max(q, 0.0)) - min(max(q.x, q.y), 0.0);
        // Fade at the actual outer edge as well as inward; clipping must not cut bright pixels.
        float edgeFade = ease(inside / max(1.0, feather * 0.12));
        float vertical = 1.0 - ease(min(p.y, bounds.w - p.y) / feather);
        // End the top/bottom light gradually before it meets the SDR side edges and corners.
        float sideFade = ease(min(p.x, bounds.z - p.x) / max(1.0, min(radius + feather, halfSize.x)));
        float alpha = strength * edgeFade * vertical * sideFade;
        return lightColor * half(alpha);
    }
"""

/** One cached gradient mesh, clipped by the real card outline at the call site. */
internal fun courseEdgeGlowMesh(bounds: Rect, spread: Float, color: Color, strength: Float): Vertices? {
    if (bounds.width <= 0f || bounds.height <= 0f || spread <= 0f || strength <= 0f) return null
    val topFeather = minOf(spread, bounds.width / 2f, bounds.height / 2f)
    val sideFeather = topFeather * 0.55f
    val xs = ((0..16).map { sideFeather * it / 16f } +
        (0..16).map { bounds.width - sideFeather * it / 16f }).distinct().sorted()
    val ys = ((0..16).map { topFeather * it / 16f } +
        (0..16).map { bounds.height * it / 16f }).distinct().sorted()
    fun falloff(distance: Float, feather: Float): Float {
        val t = (distance / feather).coerceIn(0f, 1f)
        return (1f - t) * (1f - t) * (1f + 2f * t)
    }
    val positions = ArrayList<Offset>(xs.size * ys.size)
    val colors = ArrayList<Color>(xs.size * ys.size)
    for (y in ys) {
        val t = ((y / bounds.height - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val verticalFade = (1f - t) * (1f - t) * (1f + 2f * t)
        for (x in xs) {
            val edge = 1f - (1f - falloff(x, sideFeather)) *
                (1f - falloff(bounds.width - x, sideFeather)) * (1f - falloff(y, topFeather))
            positions += bounds.topLeft + Offset(x, y)
            colors += color.copy(alpha = (0.28f * strength * edge * verticalFade).coerceIn(0f, 1f))
        }
    }
    val indices = ArrayList<Int>()
    for (row in 0 until ys.lastIndex) for (column in 0 until xs.lastIndex) {
        val a = row * xs.size + column
        val b = a + 1
        val c = a + xs.size
        val d = c + 1
        // Leave the transparent center out of the draw entirely.
        if (colors[a].alpha == 0f && colors[b].alpha == 0f && colors[c].alpha == 0f && colors[d].alpha == 0f) continue
        indices.addAll(listOf(a, b, c, b, d, c))
    }
    return Vertices(VertexMode.Triangles, positions, positions, colors, indices)
}
