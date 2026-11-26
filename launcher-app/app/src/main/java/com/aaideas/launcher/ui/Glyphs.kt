package com.aaideas.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Line icons for the launcher's own UI (24-unit grid, 1.9 stroke), drawn in the text colour. */
object Glyphs {
    private fun line(vararg d: String): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        for (p in d) addPath(
            pathData = PathParser().parsePathString(p).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Search = line("M11 4.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z", "M16 16l4.5 4.5")
    val Back = line("M15 6l-6 6 6 6")
    val Info = line("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v6", "M12 7.5v.5")
    val Trash = line("M5 7h14", "M10 7V4h4v3", "M7 7l1 13h8l1-13")
    val Eye = line("M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    val Palette = line("M12 3a9 9 0 0 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5 1-1.5 2-1.5h2a4 4 0 0 0 4-4c0-4.4-4-8-9-8z", "M7.5 11h.01", "M10 7h.01", "M15 7.5h.01")
    val Image = line("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M3 17l5-5 4 4 3-3 6 6")
    val Check = line("M5 12l5 5 9-10")
    val Globe = line("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M3 12h18", "M12 3c3 3.5 3 14.5 0 18", "M12 3c-3 3.5-3 14.5 0 18")
    val Home = line("M4 11l8-7 8 7v9h-5v-6h-6v6H4z")
    val Plus = line("M12 5v14", "M5 12h14")
    val Up = line("M12 19V5", "M6 11l6-6 6 6")
    val Down = line("M12 5v14", "M6 13l6 6 6-6")
    val Grow = line("M12 3v6", "M9 6l3-3 3 3", "M12 21v-6", "M9 18l3 3 3-3", "M5 12h14")
    val Shrink = line("M12 3v6", "M9 6l3 3 3-3", "M12 21v-6", "M9 18l3-3 3 3", "M5 12h14")
    val Widgets = line("M5 4h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M14 4h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M5 13h14a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z")
    val Refresh = line("M20 12a8 8 0 1 1-2.3-5.6", "M20 4v5h-5")
}

@Composable
fun GlyphIcon(v: ImageVector, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(v, contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = modifier.size(size))
}

/** Pill button in the same glass as everything else; [strong] = whiter, for the main action. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false) {
    val ink = LocalInk.current
    Box(
        modifier.height(46.dp)
            .glass(RoundedCornerShape(23.dp), extraWhite = if (strong) 0.32f else 0.1f)
            .clickable(MutableInteractionSource(), null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink.text, fontSize = 15.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}
