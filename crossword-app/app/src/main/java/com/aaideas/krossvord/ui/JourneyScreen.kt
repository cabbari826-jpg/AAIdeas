package com.aaideas.krossvord.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.City
import com.aaideas.krossvord.model.Journey

/**
 * "Yol xəritəsi": a road down the screen with a stop for each place. Open places show their
 * fact; the next one says how many levels are left; later ones are greyed. A plain lazy list
 * with a dashed line, so it scrolls smoothly on weak phones.
 */
@Composable
fun JourneyScreen(vm: GameViewModel) {
    val solved = vm.completed.size
    val open = Journey.opened(solved)
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize().belowStatusBar().padding(horizontal = 16.dp)) {
        ScreenHeader("Yol xəritəsi", vm::back, Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().topFade()) {
            item { MonthTripCard(vm) }
            item {
                Text("Böyük yol", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
            item {
                Text(
                    Journey.toNext(solved)?.let { "Hər ${Journey.STEP} səviyyədə yeni yer açılır. Növbətiyə $it səviyyə qaldı." }
                        ?: "Bütün yol keçildi! Əla səyahət idi.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(Journey.CITIES) { i, city ->
                Stop(city, i, open, Journey.toNext(solved), last = i == Journey.CITIES.lastIndex)
            }
            item { Spacer(Modifier.height(BottomMenuHeight)) }
        }
    }
}

@Composable
private fun Stop(city: City, i: Int, open: Int, toNext: Int?, last: Boolean) {
    val cs = MaterialTheme.colorScheme
    val reached = i < open
    val here = i == open - 1
    val next = i == open
    val road = if (reached) cs.primary else cs.outlineVariant
    Row(Modifier.fillMaxWidth().height(if (reached) 118.dp else 84.dp)) {
        // The road: a line through a round marker.
        Box(Modifier.width(56.dp).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!last) Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    road, Offset(size.width / 2, 28.dp.toPx()), Offset(size.width / 2, size.height + 2f),
                    strokeWidth = 5.dp.toPx(),
                    pathEffect = if (reached && i + 1 < open) null else PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                )
            }
            Box(
                Modifier.padding(top = 4.dp).size(48.dp).clip(CircleShape)
                    .background(if (here) Color(0xFFFFC94D) else if (reached) cs.primaryContainer else cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(if (reached) city.icon else "🔒", fontSize = 22.sp) }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (here) cs.tertiaryContainer else if (reached) cs.surfaceContainerHigh else cs.surfaceContainerLow,
            modifier = Modifier.weight(1f).padding(bottom = 10.dp).glassy(MaterialTheme.shapes.large),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    city.name + if (here) "  · siz buradasınız" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reached) cs.onSurface else cs.onSurfaceVariant,
                )
                when {
                    reached -> Text(city.fact, style = MaterialTheme.typography.bodyMedium)
                    next && toNext != null -> Text("$toNext səviyyə qaldı", style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                    else -> Text("Hələ bağlıdır", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Home card: where the player is on the road and how far to the next place. */
@Composable
fun JourneyCard(vm: GameViewModel) {
    val solved = vm.completed.size
    val open = Journey.opened(solved)
    val city = Journey.CITIES[open - 1]
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = { vm.open(Screen.MAP) },
        shape = MaterialTheme.shapes.large,
        color = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().glassy(MaterialTheme.shapes.large),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🗺", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Yol xəritəsi: ${city.icon} ${city.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    Journey.toNext(solved)?.let { "${Journey.CITIES[open].name} üçün $it səviyyə qaldı" } ?: "Bütün yol keçildi!",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("▶", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Short "Ayın yolu" for the "Bu gün" tab: the route close up and the progress; a tap opens the map screen. */
@Composable
fun MonthTripMini(vm: GameViewModel) {
    val c = com.aaideas.krossvord.model.Challenge
    val route = vm.route
    val n = vm.monthLevels
    val cs = MaterialTheme.colorScheme
    Surface(onClick = { vm.open(Screen.MAP) }, shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().glassy(MaterialTheme.shapes.extraLarge)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🚗 Ayın yolu", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Text(route.title, style = MaterialTheme.typography.titleMedium)
                }
                Text("▶", style = MaterialTheme.typography.titleMedium)
            }
            Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                AzMap(route.places, c.position(route, n), Modifier.fillMaxWidth(), close = true)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { n.coerceAtMost(c.GOAL).toFloat() / c.GOAL },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
            Text(
                if (n >= c.GOAL) "🏁 Çatdınız! Hədiyyə: ${c.REWARD_HINTS} pulsuz ipucu."
                else "$n / ${c.GOAL} səviyyə · indi: ${c.reached(route, n).name}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "Ayın yolu": this month's route on the map, how far the car is, and the reward. */
@Composable
private fun MonthTripCard(vm: GameViewModel) {
    val c = com.aaideas.krossvord.model.Challenge
    val route = vm.route
    val n = vm.monthLevels
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().glassy(MaterialTheme.shapes.extraLarge)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🚗 Ayın yolu", style = MaterialTheme.typography.labelLarge, color = cs.primary)
            Text(route.title, style = MaterialTheme.typography.titleLarge)
            var whole by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                AzMap(route.places, c.position(route, n), Modifier.fillMaxWidth(), close = !whole)
            }
            androidx.compose.material3.OutlinedButton(onClick = { whole = !whole }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (whole) "🔍 Yolu yaxından göstər" else "🗺 Bütün xəritəni göstər", style = MaterialTheme.typography.titleSmall)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { n.toFloat() / c.GOAL },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
            Text(
                if (n >= c.GOAL) "🏁 Çatdınız! Ayın ${vm.monthFinishDay}-də. Hədiyyə alındı: ${c.REWARD_HINTS} pulsuz ipucu."
                else "$n / ${c.GOAL} səviyyə · indi: ${c.reached(route, n).name} · növbəti dayanacağa ${c.toNextStop(route, n)} səviyyə",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Ay bitənə qədər çatsanız: ${c.REWARD_HINTS} pulsuz ipucu." +
                    (vm.bestFinishDay.takeIf { it > 0 }?.let { " Rekordunuz: ayın $it-i." } ?: "") +
                    " Hər ay yeni yol.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}
