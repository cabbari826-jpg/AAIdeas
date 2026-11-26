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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
    val springy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    // A capsule while thin; the same round feel as the bottom menu when open.
    val corner by animateDpAsState(if (open) 32.dp else 18.dp, springy, label = "islandCorner")
    val shape = RoundedCornerShape(corner)
    val src = remember { MutableInteractionSource() }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 32.dp
    // Exactly the bottom menu's glass in every state and theme (owner, 2026-11-24).
    Surface(
        modifier = Modifier.padding(top = 8.dp)
            .then(if (haze != null) Modifier.clip(shape).hazeEffect(haze, glassStyle(dark)) else Modifier)
            .liquidGlass(screenLayer, shape, dark, frost = 2.dp)
            .glassy(shape, lens = false)
            .springPress(src)
            .clip(shape)
            .clickable(src, indication = null, onClick = toggle),
        shape = shape,
        color = if (haze != null || screenLayer != null) Color.Transparent else if (glass) Color.White.copy(alpha = if (dark) 0.14f else 0.45f) else cs.surfaceContainerHigh,
        border = if (glass) null else BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f)),
    ) {
        // One shape that grows out of the pill and back, like an island: size springs, contents cross-fade.
        androidx.compose.animation.AnimatedContent(
            targetState = open,
            transitionSpec = {
                (fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 90)) + scaleIn(initialScale = 0.96f))
                    .togetherWith(fadeOut(androidx.compose.animation.core.tween(90)))
                    .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) })
            },
            contentAlignment = Alignment.TopCenter,
            label = "island",
        ) { isOpen ->
            if (!isOpen) Thin(vm) else Wide(vm, Modifier.width(wide), toggle)
        }
    }
}

private val islandLabel @Composable get() = MaterialTheme.typography.labelLarge.copy(fontFamily = MenuFont, fontWeight = FontWeight.ExtraBold)

@Composable
private fun Ring(done: Int, total: Int, size: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    CircularProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = Modifier.size(size),
        strokeWidth = size / 5,
        trackColor = cs.onSurface.copy(alpha = 0.15f),
        gapSize = 0.dp,
    )
}

/** Thin: the chosen pack, a small ring and its count. 36 dp tall, 14 dp on each side. */
@Composable
private fun Thin(vm: GameViewModel) {
    val cs = MaterialTheme.colorScheme
    val pack = vm.packs[vm.homePack]
    val done = pack.levels.count { it.id in vm.completed }
    Row(
        Modifier.height(36.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ring(done, pack.levels.size, 16.dp)
        Text(pack.title, style = islandLabel, color = cs.onSurface, maxLines = 1)
        Text("$done/${pack.levels.size}", style = islandLabel, color = cs.onSurface.copy(alpha = 0.7f), maxLines = 1)
    }
}

/**
 * Open: every pack as a row of the same height, a ring on the left, the name, and the count on the
 * right. The chosen pack sits in the same highlight as the chosen menu tab. 8 dp inset all round,
 * like the menu, so the highlight follows the card's corners.
 */
@Composable
private fun Wide(vm: GameViewModel, modifier: Modifier, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val dark = cs.background.luminance() < 0.5f
    val p = vm.homePack
    val packs = vm.packs.indices.filter { vm.packOpen(it) }
    Column(modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
        packs.forEach { i ->
            val pack = vm.packs[i]
            val done = pack.levels.count { it.id in vm.completed }
            val on = i == p
            val icon = com.aaideas.krossvord.model.Holidays.holidayFor(pack.packId)?.icon?.let { "$it " }
                ?: if (com.aaideas.krossvord.model.Weekly.isWeekly(pack.packId)) "🎁 " else ""
            val ink = if (on && !glass) cs.onPrimary else cs.onSurface
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
                    .background(if (on) (if (glass) Color.White.copy(alpha = if (dark) 0.22f else 0.6f) else cs.primary) else Color.Transparent)
                    .clickable { vm.selectedPack = i; close() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (on && !glass) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
                        CircularProgressIndicator(
                            progress = { if (pack.levels.isEmpty()) 0f else done.toFloat() / pack.levels.size },
                            modifier = Modifier.size(20.dp), strokeWidth = 4.dp, color = ink,
                            trackColor = ink.copy(alpha = 0.3f), gapSize = 0.dp,
                        )
                    }
                } else Ring(done, pack.levels.size, 20.dp)
                Text(icon + pack.title, style = islandLabel, color = ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text("$done / ${pack.levels.size}", style = islandLabel, color = ink.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}
