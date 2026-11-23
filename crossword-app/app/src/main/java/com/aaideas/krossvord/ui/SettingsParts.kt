package com.aaideas.krossvord.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/*
 * The building blocks of every settings-like list, so all of them look the same in every theme:
 * a section title, then one card (same shape and colour everywhere) holding rows of one kind.
 */

private val CardShape = RoundedCornerShape(20.dp)

/** Section: a title above one card. */
@Composable
fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().glassy(CardShape)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Title and a short grey line under it; [end] sits on the right. */
@Composable
fun SettingText(title: String, subtitle: String? = null, modifier: Modifier = Modifier, end: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        end()
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    SettingText(title, subtitle, Modifier.clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp))
    }

/** A tappable row that opens something: "Title ›". */
@Composable
fun SettingLink(title: String, subtitle: String? = null, onClick: () -> Unit) =
    SettingText(title, subtitle, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        GlyphIcon(Glyph.BACK, MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.padding(start = 12.dp).size(18.dp).graphicsLayerFlip())
    }

private fun Modifier.graphicsLayerFlip() = this.then(androidx.compose.ui.Modifier.graphicsLayer { scaleX = -1f })

/** Thin rule between rows of a card. */
@Composable
fun SettingDivider() = androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
