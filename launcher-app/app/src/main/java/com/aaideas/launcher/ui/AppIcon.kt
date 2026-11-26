package com.aaideas.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.GlassIcon

/** Glass tile with the app's glyph; tinted with the app's colour (or the one picked in "Цвет стекла"). */
@Composable
fun GlassAppIcon(icon: GlassIcon?, label: String, size: Dp, customColor: Int?, modifier: Modifier = Modifier) {
    val color = customColor ?: icon?.color ?: 0xFF64748B.toInt()
    Box(
        modifier
            .size(size)
            .glass(IconShape, tint = Color(color), tint2 = icon?.color2?.takeIf { customColor == null }?.let { Color(it) }, tintScale = 1f),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null && !icon.whiteGlyph) {
            // An icon with no glyph to pull out (photo-like art): the original, in our shape.
            Image(icon.glyph, contentDescription = label, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(IconShape))
        } else if (icon != null) {
            Image(icon.glyph, contentDescription = label, modifier = Modifier.fillMaxSize())
        } else {
            // Until the icon is processed (first second after start): the first letter.
            Text(label.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** An app on the home screen: icon and (optionally) its name. Tap opens, long press opens the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppEntry,
    icon: GlassIcon?,
    customColor: Int?,
    iconSize: Dp,
    showLabel: Boolean,
    onOpen: (Rect) -> Unit,
    onMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bounds(): Rect = holder[0]?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
    Column(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onOpen(bounds()) },
            onLongClick = { onMenu(bounds()) },
            onClickLabel = app.label,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GlassAppIcon(icon, app.label, iconSize, customColor, Modifier.onGloballyPositioned { holder[0] = it })
        if (showLabel) {
            Text(
                app.label,
                style = labelStyle(ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

fun labelStyle(ink: Ink) = TextStyle(
    color = ink.text,
    fontSize = 12.5.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(ink.shadow, blurRadius = 4f),
)

/** The icon lifted above the dimmed screen while its menu is open. */
@Composable
fun LiftedIcon(icon: GlassIcon?, label: String, size: Dp, customColor: Int?) {
    GlassAppIcon(icon, label, size, customColor, Modifier.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; shadowElevation = 24f; shape = IconShape; clip = false })
}
