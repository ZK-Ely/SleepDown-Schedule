package com.xiaomanjun.sleepdownschedule.glass.ui

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/** Each channel stays at/above SDR white: the highlight cannot turn pale wallpaper gray. */
internal fun courseHdrLightColor(color: Color): Color {
    val linear = color.convert(ColorSpaces.LinearExtendedSrgb)
    fun light(channel: Float) = 1f + channel.coerceIn(0f, 1f) * (CourseHdrHeadroom - 1f)
    return Color(light(linear.red), light(linear.green), light(linear.blue), 1f, ColorSpaces.LinearExtendedSrgb)
}

@RequiresApi(29)
internal fun courseHdrLightPaint(color: Color): Paint {
    val light = courseHdrLightColor(color)
    val packed = android.graphics.Color.pack(
        light.red, light.green, light.blue, light.alpha,
        android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.LINEAR_EXTENDED_SRGB)
    )
    return Paint().apply {
        // Compose Vertices encodes its colors through ARGB8. Use white vertex alpha as a mask
        // and put the >1 linear RGB in a native long-color shader so it survives that conversion.
        // Screen cannot brighten white and Plus clamps; SrcOver preserves the HDR values.
        blendMode = BlendMode.SrcOver
        shader = LinearGradient(0f, 0f, 1f, 0f, packed, packed, Shader.TileMode.CLAMP)
    }
}

/** One cached gradient mesh, clipped by the real card outline at the call site. */
internal fun courseEdgeGlowMesh(
    bounds: Rect,
    spread: Float,
    color: Color,
    strength: Float,
    bottomEdge: Boolean = false,
    peakAlpha: Float = 0.28f
): Vertices? {
    if (bounds.width <= 0f || bounds.height <= 0f || spread <= 0f || strength <= 0f) return null
    val topFeather = minOf(spread, bounds.width / 2f, bounds.height / 2f)
    val sideFeather = topFeather * 0.55f
    val xs = ((0..16).map { sideFeather * it / 16f } +
        (0..16).map { bounds.width - sideFeather * it / 16f }).distinct().sorted()
    val bottomYs = if (bottomEdge) (0..16).map { bounds.height - topFeather * it / 16f } else emptyList()
    val ys = ((0..16).map { topFeather * it / 16f } + bottomYs +
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
            // Smoothly join top/left/right without hard concentric stroke boundaries.
            val upperEdge = (1f - (1f - falloff(x, sideFeather)) *
                (1f - falloff(bounds.width - x, sideFeather)) * (1f - falloff(y, topFeather))
                ) * verticalFade
            val bottom = if (bottomEdge) falloff(bounds.height - y, topFeather) else 0f
            val edge = 1f - (1f - upperEdge) * (1f - bottom)
            positions += bounds.topLeft + Offset(x, y)
            colors += color.copy(alpha = (peakAlpha * strength * edge).coerceIn(0f, 1f))
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
