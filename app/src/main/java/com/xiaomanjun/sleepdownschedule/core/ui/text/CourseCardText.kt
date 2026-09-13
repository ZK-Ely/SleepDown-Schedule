package com.xiaomanjun.sleepdownschedule.core.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Keep the theme hue, lifting its brightness without a second glyph pass. */
@Composable
internal fun CourseCardText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    themeColor: Color?,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val foreground = remember(themeColor, color) {
        themeColor?.let {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(it.toArgb(), hsv)
            hsv[2] = (hsv[2] + (1f - hsv[2]) * 0.28f).coerceAtLeast(0.72f)
            Color(android.graphics.Color.HSVToColor(hsv))
        } ?: color
    }
    Text(
        text = text,
        modifier = modifier,
        color = foreground,
        style = style,
        fontWeight = if (themeColor != null) maxOf(fontWeight ?: style.fontWeight ?: FontWeight.Normal, FontWeight.Bold) else fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}
