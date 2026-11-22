package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.Greeting
import java.time.MonthDay

/** The dialog the app opens with, if any: profile on first run, birthday, or the daily greeting. */
@Composable
fun WelcomeHost(vm: GameViewModel) {
    when (vm.welcome) {
        GameViewModel.Welcome.PROFILE -> ProfileDialog(vm.playerName, vm.birthday, vm::saveProfile, vm::dismissWelcome)
        GameViewModel.Welcome.BIRTHDAY -> SimpleWelcome(
            "🎂 Ad gününüz mübarək" + vm.playerName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty() + "!",
            "Sizə hədiyyə: +1 can ❤️\nCan sayı: ${vm.lives}",
            vm::dismissWelcome,
        )
        GameViewModel.Welcome.DAILY -> {
            val lines = buildList {
                vm.holiday?.let { add("${it.icon} ${it.greeting}") }
                if (!vm.daily.guess.finished) add("Günün sözü sizi gözləyir.")
                add("Canlar: ${vm.lives} ❤️")
            }
            SimpleWelcome(vm.hello(), lines.joinToString("\n"), vm::dismissWelcome)
        }
        null -> Unit
    }
}

@Composable
private fun SimpleWelcome(title: String, text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { Button(onClick = onClose) { Text("Oynayaq!") } },
    )
}

/** Name and birthday, both optional; nothing leaves the phone. */
@Composable
fun ProfileDialog(name0: String, birthday0: MonthDay?, onSave: (String, MonthDay?) -> Unit, onSkip: () -> Unit) {
    val form = rememberProfileForm(name0, birthday0)
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Xoş gəlmisiniz!", fontWeight = FontWeight.Bold) },
        text = { ProfileFields(form) },
        confirmButton = { Button(onClick = { onSave(form.name, form.birthday()) }) { Text("Yadda saxla") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Keç") } },
    )
}

class ProfileForm(name0: String, birthday0: MonthDay?) {
    var name by mutableStateOf(name0)
    var withBirthday by mutableStateOf(birthday0 != null)
    var day by mutableIntStateOf(birthday0?.dayOfMonth ?: 1)
    var month by mutableIntStateOf(birthday0?.monthValue ?: 1)
    val maxDay get() = java.time.Month.of(month).maxLength()
    fun birthday(): MonthDay? = if (withBirthday) MonthDay.of(month, day.coerceAtMost(maxDay)) else null
}

@Composable
fun rememberProfileForm(name0: String, birthday0: MonthDay?) = remember { ProfileForm(name0, birthday0) }

@Composable
fun ProfileFields(f: ProfileForm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sizi necə çağıraq? (istəyə görə)", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(f.name, { f.name = it.take(24) }, singleLine = true, placeholder = { Text("Adınız") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(f.withBirthday, { f.withBirthday = it })
            Text("Ad günüm (o gün hədiyyə: +1 can)", style = MaterialTheme.typography.bodyMedium)
        }
        if (f.withBirthday) {
            val max = f.maxDay
            val d = f.day.coerceAtMost(max)
            Stepper(d.toString(), { f.day = if (d <= 1) max else d - 1 }, { f.day = if (d >= max) 1 else d + 1 })
            Stepper(Greeting.MONTHS[f.month - 1], { f.month = if (f.month <= 1) 12 else f.month - 1 }, { f.month = if (f.month >= 12) 1 else f.month + 1 })
        }
        Text("Bu məlumat yalnız telefonunuzda qalır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onMinus) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
        Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 120.dp))
        TextButton(onClick = onPlus) { Text("›", style = MaterialTheme.typography.headlineSmall) }
    }
}
