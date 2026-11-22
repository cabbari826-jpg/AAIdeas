package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.DailyTasks
import com.aaideas.krossvord.model.Streak

/** Home-screen entry to today's tasks, streak and proverb. */
@Composable
fun TodayCard(vm: GameViewModel) {
    Card(
        onClick = { vm.open(Screen.DAILY) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bu gün", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Tapşırıqlar ${vm.tasksDone}/${vm.tasks.size}" + if (vm.proverbPick == null) " · Atalar sözü" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (vm.streakRun > 0) Text("🔥 ${vm.streakRun}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun DailyScreen(vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = vm::back) { Text("← Əsas səhifə") }
        Text("Bu gün", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        vm.dailyNote?.let { note ->
            Surface(onClick = { vm.dailyNote = null }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(note, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
        ProverbCard(vm)
        if (vm.miniToday != null) MiniCard(vm)
        TasksCard(vm)
        StreakCard(vm)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProverbCard(vm: GameViewModel) {
    val p = vm.proverb
    val pick = vm.proverbPick
    Section("📜 Günün atalar sözü") {
        val shown = if (pick == null) p.before + "_____" + p.after else p.full
        Text(shown, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (pick == null) {
            vm.proverbOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { w ->
                        OutlinedButton(onClick = { vm.pickProverb(w) }, modifier = Modifier.weight(1f)) { Text(w, fontSize = 17.sp, maxLines = 1) }
                    }
                }
            }
        } else {
            val right = pick == p.answer
            Text(
                if (right) "✓ Düzdür! +5 XP" else "Düzgün söz: «${p.answer}». Sabah yeni atalar sözü.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MiniCard(vm: GameViewModel) {
    Section("🧩 Mini krossvord") {
        if (vm.miniSolvedToday) {
            Text("✓ Bu günün mini krossvordu həll olunub. Sabah yenisi!", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("${vm.miniToday?.entries?.size ?: 0} söz. Tələsmədən həll edin.", style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.Button(onClick = vm::startMini, modifier = Modifier.fillMaxWidth()) { Text("Başla", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun TasksCard(vm: GameViewModel) {
    Section("✅ Gündəlik tapşırıqlar") {
        vm.tasks.forEach { t ->
            val n = vm.taskCount(t)
            val done = n >= t.target
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "✅" else "⬜", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge)
                    if (t.target > 1 && !done) LinearProgressIndicator(progress = { n / t.target.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (t.target > 1) "$n/${t.target}" else "+${DailyTasks.TASK_XP} XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Hamısını bitirənə: +1 can ❤️", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val DAY_NAMES = listOf("B.e", "Ç.a", "Ç", "C.a", "C", "Ş", "B")

@Composable
private fun StreakCard(vm: GameViewModel) {
    val played = vm.playedDays
    val today = vm.todayNum()
    val run = vm.streakRun
    Section("🔥 Ardıcıl günlər: $run") {
        Row(Modifier.fillMaxWidth()) {
            DAY_NAMES.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        Streak.calendar(today).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val on = d in played
                    val future = d > today
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                            .then(if (d == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            java.time.LocalDate.ofEpochDay(d).dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                on -> MaterialTheme.colorScheme.onPrimary
                                future -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        val left = Streak.GIFT_EVERY - run % Streak.GIFT_EVERY
        Text(
            if (run > 0 && run % Streak.GIFT_EVERY == 0 && today in played) "🎁 Bu günün hədiyyəsi alındı!"
            else "🎁 Hədiyyəyə $left gün qalıb (+2 can və 50 XP)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
