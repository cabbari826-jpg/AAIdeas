package com.aaideas.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The blurred wallpaper every glass element shows through, and the window size it covers. */
class Backdrop(val blurred: ImageBitmap?, val window: IntSize)

/** Glass settings from Настройки → Стекло. All 0..1. */
data class GlassStyle(val whiteness: Float, val tintStrength: Float, val rim: Float = 0.75f)

/** Text colour on the wallpaper: dark (owner's choice for "Море") or light. */
data class Ink(val text: Color, val shadow: Color)

val LocalBackdrop = staticCompositionLocalOf { Backdrop(null, IntSize.Zero) }
val LocalGlass = staticCompositionLocalOf { GlassStyle(0.11f, 0.9f) }
val LocalInk = staticCompositionLocalOf { Ink(Color(0xFF1B1F24), Color(0x66FFFFFF)) }

val IconShape = RoundedCornerShape(30)

/**
 * Glass: the blurred wallpaper behind this element, a white sheen, an optional
 * app-colour tint, a highlight at the top-left and a light rim.
 *
 * The blurred wallpaper is drawn at the element's window position, so it lines up
 * with the real wallpaper and the element looks see-through. Position changes
 * (pages scrolling) only redraw, they never recompose.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color? = null,
    extraWhite: Float = 0f,
    tintScale: Float = 1f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val style = LocalGlass.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInWindow() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val white = (style.whiteness + extraWhite).coerceIn(0f, 0.9f)
            val sheen = Brush.linearGradient(
                listOf(Color.White.copy(alpha = (white + 0.08f).coerceAtMost(1f)), Color.White.copy(alpha = white * 0.4f), Color.White.copy(alpha = white * 0.8f)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val t = (style.tintStrength * tintScale).coerceIn(0f, 1f)
            val tintBrush = tint?.takeIf { t > 0f }?.let {
                Brush.radialGradient(
                    listOf(it.copy(alpha = t), it.copy(alpha = t * 0.55f)),
                    center = Offset(size.width / 2f, size.height * 0.65f),
                    radius = size.maxDimension * 0.8f,
                )
            }
            val highlight = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.42f * style.rim), Color.Transparent),
                center = Offset(size.width * 0.28f, size.height * 0.04f),
                radius = size.maxDimension * 0.62f,
            )
            val rim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f * style.rim), Color.White.copy(alpha = 0.22f * style.rim), Color.White.copy(alpha = 0.55f * style.rim)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val stroke = Stroke(width = 1.dp.toPx())
            onDrawBehind {
                clipPath(path) {
                    backdrop.blurred?.let { img ->
                        drawImage(
                            img,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(img.width, img.height),
                            dstOffset = IntOffset(-pos.x.roundToInt(), -pos.y.roundToInt()),
                            dstSize = backdrop.window,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    drawRect(sheen)
                    tintBrush?.let { drawRect(it) }
                    drawRect(highlight)
                }
                drawPath(path, rim, style = stroke)
            }
        }
}
