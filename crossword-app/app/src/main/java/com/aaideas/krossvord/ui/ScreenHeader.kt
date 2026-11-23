package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one top bar of every screen (same as the level screen's): a full-width pill with "‹ Title".
 * Tapping the left part goes back; [trailing] holds the screen's own extras (hearts, streak...).
 */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val ink = cs.onSurface
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainer).glassy(CircleShape)
            .height(52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(CircleShape).clickable(onClick = onBack).fillMaxHeight()
                .padding(start = 6.dp, end = 12.dp).semantics { contentDescription = "Geri: $title" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.BACK, ink, Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
        Spacer(Modifier.width(10.dp))
    }
}
