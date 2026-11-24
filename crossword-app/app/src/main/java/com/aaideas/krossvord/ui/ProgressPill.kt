package com.aaideas.krossvord.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect

/**
 * The owner's idea (2026-11-24): once the home list is scrolled down, a thin, narrow glass pill comes in
 * at the top with the chosen pack's progress. A tap opens it like a "dynamic island" into a card with the
 * progress of every pack; a tap on a pack picks it, a tap on the card or outside closes it again.
 * Same glass, font and shapes as the bottom menu, so it reads as one family with it.
 */
@Composable
fun HomeProgressPill(vm: GameViewModel, modifier: Modifier = Modifier, forceOpen: Boolean = false) {
    var open by remember { mutableStateOf(forceOpen) }
    LaunchedEffect(vm.homeScrolled) { if (!vm.homeScrolled && !forceOpen) open = false }
    Box(modifier.fillMaxSize()) {
        // Outside the card: a tap there closes it.
        if (open) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { open = false })
        AnimatedVisibility(
            visible = vm.homeScrolled || forceOpen,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 } + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + slideOutVertically { -it / 2 } + scaleOut(targetScale = 0.85f),
        ) {
            Island(vm, open) { open = !open }
        }
    }
}

@Composable
private fun Island(vm: GameViewModel, open: Boolean, toggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val screenLayer = LocalGlassScreen.current
    val haze = if (screenLayer != null) null else LocalHaze.current
    val glass = LocalGlass.current
    val dark = cs.background.luminance() < 0.5f
    val springy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    // A capsule while thin; a card with the bottom menu's round feel when open.
    val corner by animateDpAsState(if (open) 28.dp else 18.dp, springy, label = "islandCorner")
    val shape = RoundedCornerShape(corner)
    val src = remember { MutableInteractionSource() }
    val p = vm.homePack
    val packs = vm.packs.indices.filter { vm.packOpen(it) }
    Surface(
        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            .then(if (open) Modifier.fillMaxWidth() else Modifier)
            .then(if (haze != null) Modifier.clip(shape).hazeEffect(haze, glassStyle(dark)) else Modifier)
            .liquidGlass(screenLayer, shape, dark, frost = 2.dp)
            .glassy(shape, lens = false)
            .springPress(src)
            .clip(shape)
            .clickable(src, indication = null, onClick = toggle)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        shape = shape,
        // Open, it holds a list to read, so the glass gets as dense as the frosted dialogs.
        color = if (glass && open) cs.surface.copy(alpha = if (dark) 0.78f else 0.82f)
            else if (haze != null || screenLayer != null) Color.Transparent else if (glass) Color.White.copy(alpha = if (dark) 0.14f else 0.45f) else cs.surfaceContainerHigh,
        border = if (glass) null else BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f)),
    ) {
        val label = MaterialTheme.typography.labelLarge.copy(fontFamily = MenuFont, fontWeight = FontWeight.ExtraBold)
        if (!open) {
            // Thin: the chosen pack, a small ring and its count. 36 dp tall.
            val pack = vm.packs[p]
            val done = pack.levels.count { it.id in vm.completed }
            Row(
                Modifier.height(36.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    progress = { if (pack.levels.isEmpty()) 0f else done.toFloat() / pack.levels.size },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 3.dp,
                    trackColor = cs.onSurface.copy(alpha = 0.15f),
                    gapSize = 0.dp,
                )
                Text(pack.title, style = label, color = cs.onSurface, maxLines = 1)
                Text("$done/${pack.levels.size}", style = label, color = cs.onSurface.copy(alpha = 0.7f), maxLines = 1)
            }
        } else {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Paketlər", style = label.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    color = cs.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                packs.forEach { i ->
                    val pack = vm.packs[i]
                    val done = pack.levels.count { it.id in vm.completed }
                    val icon = com.aaideas.krossvord.model.Holidays.holidayFor(pack.packId)?.icon?.let { "$it " }
                        ?: if (com.aaideas.krossvord.model.Weekly.isWeekly(pack.packId)) "🎁 " else ""
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { vm.selectedPack = i; toggle() },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon + pack.title, style = label, color = if (i == p) cs.primary else cs.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("$done / ${pack.levels.size}", style = label, color = cs.onSurface.copy(alpha = 0.7f))
                        }
                        LinearProgressIndicator(
                            progress = { if (pack.levels.isEmpty()) 0f else done.toFloat() / pack.levels.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            trackColor = cs.onSurface.copy(alpha = 0.12f),
                            drawStopIndicator = {},
                            gapSize = 0.dp,
                        )
                    }
                }
            }
        }
    }
}
