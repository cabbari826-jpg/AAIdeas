package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aaideas.krossvord.model.Greeting
import java.time.MonthDay

/** The dialog the app opens with, if any: profile on first run, birthday, or the daily greeting. */
@Composable
fun WelcomeHost(vm: GameViewModel) {
    if (vm.askReminder && vm.screen == Screen.HOME) {
        Dialog(onDismissRequest = vm::closeReminderAsk, properties = DialogProperties(usePlatformDefaultWidth = false)) { ReminderAsk(vm) }
    }
    val welcome = vm.welcome ?: return
    Dialog(onDismissRequest = vm::dismissWelcome, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WelcomeContent(vm, welcome)
    }
}

/** The card itself, also shown without a dialog in screenshot tests. */
@Composable
fun WelcomeContent(vm: GameViewModel, welcome: GameViewModel.Welcome) {
    when (welcome) {
        GameViewModel.Welcome.PROFILE -> ProfileCard(rememberProfileForm(vm.playerName, vm.birthday, vm.birthYear), vm::saveProfile, vm::dismissWelcome)
        GameViewModel.Welcome.BIRTHDAY -> WelcomeCard(
            emoji = "🎂",
            title = "Ad gününüz mübarək" + vm.playerName.takeIf { it.isNotBlank() }?.let { ",\n$it" }.orEmpty() + "!",
            lines = listOf("Sağlam, şad və uzun ömür arzulayırıq!", "Sizə kiçik hədiyyə: +1 can ❤️"),
            button = "Təşəkkürlər!",
            onClose = vm::dismissWelcome,
        )
        GameViewModel.Welcome.DAILY -> WelcomeCard(
            emoji = vm.holiday?.icon ?: "👋",
            title = vm.hello(),
            lines = buildList {
                vm.holiday?.let { add(it.greeting) }
                if (!vm.daily.guess.finished) add("Günün sözü sizi gözləyir")
                add("Canlar: ${vm.lives} ❤️")
            },
            button = "Oynayaq!",
            onClose = vm::dismissWelcome,
        )
    }
}

/** Shared look: a round emoji badge, a centred title, short lines and one wide button. */
@Composable
private fun WelcomeCard(emoji: String, title: String, lines: List<String>, button: String, onClose: () -> Unit) {
    CardShell {
        Badge(emoji)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(button, fontSize = 18.sp) }
    }
}

@Composable
internal fun CardShell(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.padding(20.dp).widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
internal fun Badge(emoji: String) {
    Box(
        Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 36.sp) }
}

class ProfileForm(name0: String, birthday0: MonthDay?, year0: Int?) {
    var name by mutableStateOf(name0)
    /** 0 = not chosen. */
    var day by mutableIntStateOf(birthday0?.dayOfMonth ?: 0)
    var month by mutableIntStateOf(birthday0?.monthValue ?: 0)
    var year by mutableStateOf(year0?.toString().orEmpty())
    fun birthday(): MonthDay? =
        if (day == 0 || month == 0) null else MonthDay.of(month, day.coerceAtMost(java.time.Month.of(month).maxLength()))
    fun birthYear(): Int? = year.toIntOrNull()?.takeIf { it in 1900..java.time.LocalDate.now().year }
}

@Composable
fun rememberProfileForm(name0: String, birthday0: MonthDay?, year0: Int?) = remember { ProfileForm(name0, birthday0, year0) }

/** Name and birthday; nothing leaves the phone. Later the name can come from the Google account. */
@Composable
fun ProfileCard(f: ProfileForm, onSave: (String, MonthDay?, Int?) -> Unit, onSkip: () -> Unit) {
    CardShell {
        Badge("👋")
        Text("Xoş gəlmisiniz!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Gəlin tanış olaq", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            f.name, { f.name = it.take(24) },
            label = { Text("Adınız") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Doğum tarixi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val maxDay = if (f.month == 0) 31 else java.time.Month.of(f.month).maxLength()
            Picker("Gün", if (f.day == 0) "—" else f.day.coerceAtMost(maxDay).toString(), Modifier.weight(1f),
                { f.day = if (f.day <= 1) maxDay else f.day.coerceAtMost(maxDay) - 1 },
                { f.day = if (f.day >= maxDay) 1 else f.day + 1 })
            Picker("Ay", if (f.month == 0) "—" else Greeting.MONTHS[f.month - 1], Modifier.weight(1.4f),
                { f.month = if (f.month <= 1) 12 else f.month - 1 },
                { f.month = if (f.month >= 12) 1 else f.month + 1 })
        }
        OutlinedTextField(
            f.year, { s -> f.year = s.filter(Char::isDigit).take(4) },
            label = { Text("İl") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Məlumat yalnız telefonunuzda saxlanılır", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onSave(f.name, f.birthday(), f.birthYear()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Davam et", fontSize = 18.sp)
        }
        TextButton(onClick = onSkip) { Text("Sonra") }
    }
}

/** A value with ‹ › on both sides, in a soft box: easier for older players than a scrolling wheel. */
@Composable
private fun Picker(label: String, value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMinus, modifier = Modifier.size(44.dp)) { Text("‹", fontSize = 22.sp) }
                Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                TextButton(onClick = onPlus, modifier = Modifier.size(44.dp)) { Text("›", fontSize = 22.sp) }
            }
        }
    }
}

/** Offer of the 10:00 word-of-the-day reminder; asks Android for permission when needed. */
@Composable
fun ReminderAsk(vm: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val remind = com.aaideas.krossvord.remind.DailyReminder
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> remind.set(context, granted); vm.closeReminderAsk() }
    CardShell {
        Badge("⏰")
        Text("Xatırladaq?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Hər gün saat ${remind.HOUR}:00-da günün sözünü xatırladaq. Söz artıq tapılıbsa, narahat etmərik.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            if (!remind.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else { remind.set(context, true); vm.closeReminderAsk() }
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Bəli, xatırlat", fontSize = 18.sp) }
        TextButton(onClick = vm::closeReminderAsk) { Text("Xeyr, sağ olun") }
        Text("Ayarlardan istənilən vaxt dəyişmək olar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
