package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Settings block: follow the phone, always light, or always dark (easier on the eyes at night). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit, dynamic: Boolean, onDynamic: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Görünüş", style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = current == mode,
                    onClick = { onPick(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Text(
            "«Sistem» telefonun ayarına uyğun dəyişir. «Qaranlıq» axşam gözləri yormur.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (supportsDynamicColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Telefonun rəngləri")
                    Text("Rənglər divar kağızından götürülür", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = dynamic, onCheckedChange = onDynamic)
            }
        }
    }
}

/**
 * Settings block: the app/grid theme. Each card is a little 3×3 grid painted in that theme,
 * so the choice is visible without opening a level. Locked gift themes say how to earn them.
 */
@Composable
fun SkinPicker(current: Skin, owned: Set<Skin>, onPick: (Skin) -> Unit, onUnlockAll: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ctx = LocalContext.current
    var askGlass by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mövzu", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skin.entries.forEach { skin ->
                SkinCard(skin, skin.palette(dark || skin.alwaysDark), selected = skin == current, locked = skin !in owned) {
                    if (skin.glass && isWeakForGlass(ctx)) askGlass = true else onPick(skin)
                }
            }
        }
        if (askGlass) GlassWarning(onYes = { askGlass = false; onPick(Skin.SUSE) }, onNo = { askGlass = false })
        if (current.glass) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Effektləri azalt", style = MaterialTheme.typography.titleMedium)
                Text("Kölgəsiz və bulanıqsız şüşə: zəif telefonda oyun daha rahat işləyir.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = GlassLite.isOn(ctx), onCheckedChange = { GlassLite.set(ctx, it) })
        }
        if (current.glass && glassBlurSupported && !GlassLite.isOn(ctx)) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Maksimum şüşə (sınaq)", style = MaterialTheme.typography.titleMedium)
                Text("Hər kart arxasındakı rəngli işığı bulandırır, işıq yavaş-yavaş hərəkət edir. Batareyanı bir az çox işlədir.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = GlassMax.isOn(ctx), onCheckedChange = { GlassMax.set(ctx, it) })
        }
        val lockedOnes = Skin.entries.filter { it !in owned }
        if (lockedOnes.isNotEmpty()) {
            Text("🔒 Hədiyyə mövzular oyunda qazanılır.", style = MaterialTheme.typography.bodySmall)
            if (isDebugBuild()) TextButton(onClick = onUnlockAll) { Text("Hamısını aç (test)") }
        }
    }
}

@Composable
private fun SkinCard(skin: Skin, p: SkinPalette, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val g = p.grid
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(112.dp).clip(RoundedCornerShape(20.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(20.dp))
            .then(if (skin.glass) Modifier.drawBehind { drawRect(Brush.linearGradient(listOf(Color(0xFFB69DF8), Color(0xFFFFE08A), Color(0xFF9FDDC2)))) } else Modifier.background(p.scheme.background))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(10.dp)
            .semantics { contentDescription = skin.label + if (locked) ", bağlıdır: ${skin.unlock.hint}" else if (selected) ", seçilib" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Column(Modifier.border(1.dp, g.line)) {
                // clue, letter, block / letter, active, letter / block, solved, letter
                val cells = listOf(
                    listOf('c', 'A', 'b'), listOf('Ç', '*', 'R'), listOf('b', 'S', 'Ö'),
                )
                cells.forEachIndexed { r, row ->
                    Row {
                        row.forEachIndexed { c, ch ->
                            val m = Modifier.size(28.dp).border(0.5.dp, g.line)
                            when (ch) {
                                'b' -> Box(m.blockCell(g))
                                'c' -> Box(m.background(g.clueCell), contentAlignment = Alignment.Center) { Text("→", color = g.clueText, fontSize = 12.sp) }
                                '*' -> Box(m.background(g.activeCell))
                                else -> Box(m.background(if (r == 2) g.solved else g.letterCell), contentAlignment = Alignment.Center) {
                                    Text("$ch", color = g.letter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            if (locked) Box(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Text("🔒", fontSize = 22.sp) }
        }
        Text(skin.label, color = p.scheme.onBackground, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (locked) Text(skin.unlock.hint, color = p.scheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 4)
    }
}

/** The prototype is a debug build; Play builds will not be. */
@Composable
private fun isDebugBuild(): Boolean =
    (LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
