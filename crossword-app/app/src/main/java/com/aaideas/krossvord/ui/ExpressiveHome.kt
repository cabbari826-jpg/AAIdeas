package com.aaideas.krossvord.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.AzText

/** In-app brand title (the app's own text is Azerbaijani; the launcher name is localized in res). */
const val BRAND = "Açar Söz"

/** Every level is a rounded square, like a cell of the grid. State is shown by colour and mark, never by shape. */
private val levelShape: Shape = SquircleShape

/** Superellipse |x|^n + |y|^n = 1: smoother than a rounded square (iOS/Pixel-style icon outline). */
object SquircleShape : Shape by GenericShape({ size, _ ->
    val n = 4.0
    val a = size.width / 2; val b = size.height / 2
    val steps = 120
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val c = cos(t); val s = sin(t)
        val x = a + a * sign(c) * abs(c).pow(2 / n)
        val y = b + b * sign(s) * abs(s).pow(2 / n)
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
})

/** Gentle spring squash on press. */
@Composable
private fun Modifier.springPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return scale(s)
}

@Composable
fun ExpressiveHome(vm: GameViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(BRAND, style = MaterialTheme.typography.headlineMedium)

        vm.holiday?.let { HolidayCard(it, vm) }
        WordOfDayCard(vm.daily) { vm.open(Screen.WORD_OF_DAY) }

        ContinueCard(vm)

        // Level grid of the pack the player is on.
        val p = vm.packIndexOf(vm.continueIndex())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.packs[p].title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.selectedPack = p; vm.open(Screen.LEVELS) }) { Text("Hamısı ›") }
        }
        val levels = vm.packLevels(p)
        val current = vm.continueIndex()
        levels.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (i, level) ->
                    LevelBadge(
                        number = vm.numberInPack(i),
                        shape = levelShape,
                        done = level.id in vm.completed,
                        current = i == current,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.startLevel(i) },
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (vm.packs.size > 1) {
            Text("Paketlər", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.packs.forEachIndexed { i, pack ->
                    val done = pack.levels.count { it.id in vm.completed }
                    PackCard(pack.title, done, pack.levels.size, selected = i == p) {
                        vm.selectedPack = i; vm.open(Screen.LEVELS)
                    }
                }
            }
        }

        if (vm.completed.isNotEmpty()) {
            TextButton(onClick = { confirmReset = true }, Modifier.align(Alignment.CenterHorizontally)) {
                Text("Hər şeyi yenidən başla", color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(BottomMenuHeight))
    }
    if (confirmReset) ResetAllDialog(onConfirm = { confirmReset = false; vm.resetAllProgress(); vm.startLevel(0) }, onDismiss = { confirmReset = false })
}

@Composable
private fun ContinueCard(vm: GameViewModel) {
    val cs = MaterialTheme.colorScheme
    val i = vm.continueIndex()
    val level = vm.levels[i]
    val allDone = vm.completed.size == vm.levels.size
    val started = vm.levelStarted(i)
    val keyLen = level.keyword?.let { AzText.letters(it.word).size } ?: 0
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = { vm.startLevel(i) },
        interactionSource = src,
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.primary,
        contentColor = cs.onPrimary,
        modifier = Modifier.fillMaxWidth().springPress(src),
    ) {
        Box {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${vm.levelTitle(i)} · ${vm.completed.size}/${vm.levels.size} həll olunub",
                    style = MaterialTheme.typography.labelLarge, color = cs.onPrimary.copy(alpha = 0.85f),
                )
                Text(
                    when {
                        allDone -> "Hamısı həll olunub!"
                        started -> "Davam et"
                        vm.completed.isEmpty() -> "Oyuna başla"
                        else -> "Növbəti səviyyə"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (keyLen > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Açar söz", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary.copy(alpha = 0.85f))
                        Spacer(Modifier.width(2.dp))
                        repeat(keyLen) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(cs.onPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = { vm.startLevel(i) },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.onPrimary, contentColor = cs.primary),
                ) { Text(if (started) "Davam et ▶" else "Oyna ▶") }
            }
        }
    }
}

@Composable
private fun LevelBadge(number: Int, shape: Shape, done: Boolean, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val grid = LocalGridColors.current
    val src = remember { MutableInteractionSource() }
    val dark = cs.background.luminance() < 0.5f
    val bg = when { current -> if (dark) Color(0xFFE9B949) else Color(0xFFFFC94D); done -> grid.solved; else -> cs.surfaceContainer }
    val fg = if (current) Color(0xFF2A2000) else cs.onSurface
    Box(
        modifier.aspectRatio(1f).springPress(src).clip(shape).background(bg)
            .border(BorderStroke(if (current) 3.dp else 1.dp, if (current) cs.primary else if (done) Color.Transparent else cs.outlineVariant), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (done) "✓" else "$number", style = MaterialTheme.typography.titleLarge, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PackCard(title: String, done: Int, total: Int, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = src,
        shape = MaterialTheme.shapes.large,
        color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        modifier = Modifier.width(140.dp).springPress(src),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text("$done / $total", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
        }
    }
}

val BottomMenuHeight: Dp = 88.dp

/**
 * Floating translucent menu ("glass"). Real backdrop blur is not available in Compose on all
 * Android versions, so it is a see-through tinted surface with a light rim and soft shadow.
 */
@Composable
fun GlassBottomMenu(screen: Screen, open: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth(),
        shape = CircleShape,
        // No shadow: it would show through the see-through fill as a smudge.
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(Screen.HOME, "▦", "Oyun"),
                Triple(Screen.LEVELS, "☰", "Səviyyələr"),
                Triple(Screen.STATS, "★", "Statistika"),
                Triple(Screen.SETTINGS, "⚙", "Ayarlar"),
            ).forEach { (target, icon, label) ->
                val on = screen == target
                val src = remember { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).springPress(src).clip(CircleShape)
                        .background(if (on) cs.primary else Color.Transparent)
                        .clickable(interactionSource = src, indication = null) { open(target) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(icon, fontSize = 16.sp, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
        }
    }
}
