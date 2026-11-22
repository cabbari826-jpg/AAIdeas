package com.aaideas.krossvord.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Reports where this part of the screen is, only while the coach marks are showing. */
fun Modifier.tutorialAnchor(vm: GameViewModel, spot: TutorialSpot): Modifier =
    if (vm.tutorialStep != TUTORIAL_COACH) this
    else this.onGloballyPositioned { vm.tutorialRects[spot] = it.boundsInRoot() }

/** First level: ask if an explanation is wanted; if yes, point at each part with a bubble. */
@Composable
fun TutorialLayer(vm: GameViewModel) {
    when (vm.tutorialStep) {
        TUTORIAL_ASK -> Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CardShell {
                Badge("🧩")
                Text("Skanvord oynamısınız?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "Oyunun qaydalarını qısa göstərək? Bir dəqiqə çəkir.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = vm::startCoach, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Bəli, göstər", fontSize = 18.sp) }
                TextButton(onClick = vm::finishTutorial) { Text("Xeyr, bilirəm", fontSize = 16.sp) }
            }
        }
        TUTORIAL_COACH -> CoachOverlay(vm)
    }
}

@Composable
private fun CoachOverlay(vm: GameViewModel) {
    // Skip marks whose part is not on this screen (no key word, hint inside the columns, ...).
    val (spot, text) = COACH_STEPS[vm.coachIndex]
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target: Rect? = vm.tutorialRects[spot]?.translate(-origin)
    if (target == null) {
        androidx.compose.runtime.LaunchedEffect(vm.coachIndex) {
            kotlinx.coroutines.delay(300)
            if (vm.tutorialRects[spot] == null) vm.nextCoach()
        }
    }
    val ring = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = vm::nextCoach),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.55f)
            if (target == null) { drawRect(dim); return@Canvas }
            val pad = 6.dp.toPx()
            val t = Rect(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
            // Darken everything around the target (four bands), then ring it.
            drawRect(dim, Offset.Zero, Size(size.width, t.top.coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, t.bottom), Size(size.width, (size.height - t.bottom).coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, t.top), Size(t.left.coerceAtLeast(0f), t.height))
            drawRect(dim, Offset(t.right, t.top), Size((size.width - t.right).coerceAtLeast(0f), t.height))
            drawRoundRect(ring, t.topLeft, t.size, CornerRadius(12.dp.toPx()), style = Stroke(4.dp.toPx()))
        }
        if (target != null) {
            val h = with(density) { maxHeight.toPx() }
            val below = target.center.y < h / 2
            val y = with(density) { (if (below) target.bottom + 14.dp.toPx() else target.top - 14.dp.toPx()).toDp() }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 480.dp)
                    .layoutAt(y, below),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${vm.coachIndex + 1}/${COACH_STEPS.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::finishTutorial) { Text("Keç") }
                        Button(onClick = vm::nextCoach) { Text(if (vm.coachIndex + 1 < COACH_STEPS.size) "Növbəti ›" else "Başa düşdüm") }
                    }
                }
            }
        }
    }
}

/** Places the bubble with its top at [y] (below the target) or its bottom at [y] (above it). */
private fun Modifier.layoutAt(y: androidx.compose.ui.unit.Dp, below: Boolean): Modifier =
    this.then(androidx.compose.ui.Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0))
        layout(p.width, p.height) {
            val top = if (below) y.roundToPx() else y.roundToPx() - p.height
            p.place(0, top.coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0)))
        }
    })
