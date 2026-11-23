package com.aaideas.krossvord.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.WordBuilder
import com.aaideas.krossvord.monetization.AdKind
import com.aaideas.krossvord.monetization.PlaceholderAdService

@Composable
fun HomeScreen(vm: GameViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Krossvord", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Azərbaycanca skandinav krossvordu", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("Həll olunub: ${vm.completed.size} / ${vm.levels.size}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        WordOfDayCard(vm.daily) { vm.open(Screen.WORD_OF_DAY) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.startLevel(vm.continueIndex()) }, Modifier.fillMaxWidth().height(56.dp)) { Text(vm.continueLabel()) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.open(Screen.LEVELS) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Səviyyə seç") }
        Spacer(Modifier.height(12.dp))
        if (vm.completed.isNotEmpty()) {
            OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth().height(56.dp)) { Text("Yenidən başla") }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = { vm.open(Screen.SETTINGS) }) { Text("Ayarlar") }
    }
    if (confirmReset) ResetAllDialog(onConfirm = { confirmReset = false; vm.resetAllProgress(); vm.startLevel(0) }, onDismiss = { confirmReset = false })
}

/** Asks before wiping all progress. */
@Composable
fun ResetAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hər şeyi sıfırla?") },
        text = { Text("Bütün həll olunmuş səviyyələr, canlar və pulsuz ipucları silinəcək. Ayarlar və premium qalır.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Sıfırla") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

@Composable
fun LevelsScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = vm::back) { Text("← Əsas səhifə") }
        // One chip per pack: the main pack first, then the themes.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.packs.forEachIndexed { p, pack ->
                if (!vm.packOpen(p)) return@forEachIndexed
                val done = pack.levels.count { it.id in vm.completed }
                val icon = com.aaideas.krossvord.model.Holidays.holidayFor(pack.packId)?.icon?.let { "$it " }.orEmpty()
                FilterChip(
                    selected = vm.homePack == p,
                    onClick = { vm.selectedPack = p },
                    label = { Text("$icon${pack.title} $done/${pack.levels.size}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.packLevels(vm.homePack)) { (i, level) ->
                val done = level.id in vm.completed
                Card(Modifier.fillMaxWidth().clickable { vm.startLevel(i) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Səviyyə ${vm.numberInPack(i)}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(if (done) "✓ həll olunub · yenidən oyna" else "${level.entries.size} söz")
                    }
                }
            }
        }
    }
}

/** Authors of the open word lists we use. Add a line for every new source (see content/SOURCES.md). */
val CREDITS = listOf(
    "Söz siyahısı: Azərbaycan dili üçün Hunspell lüğəti (Mozilla Azerbaijani L10n; " +
        "Metin Amiroff, Kevin Scannell, Orkhan Jafarov). Lisenziya: MPL-2.0, " +
        "github.com/mozillaz/spellchecker",
)

@Composable
fun SettingsScreen(vm: GameViewModel, isPremium: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = vm::back) { Text("← Əsas səhifə") }
        // First in the list, so it is easy to find for someone who can't read small text.
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔍 İri rejim", style = MaterialTheme.typography.titleMedium)
                    Text("Bütün yazılar böyüyür, seçilmiş ipucu şəbəkənin altında iri görünür. Telefonda iri şrift varsa, özü açılır.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.largeOn(androidx.compose.ui.platform.LocalConfiguration.current.fontScale), onCheckedChange = vm::changeLargeMode)
            }
        }
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🧸 Uşaq rejimi", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ən asan ipucu əvvəl göstərilir, səhvdə can getmir" + if (vm.kidsPack >= 0) ", yalnız uşaq sözləri" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = vm.kidsMode, onCheckedChange = vm::changeKidsMode)
            }
        }
        OutlinedButton(onClick = vm::replayTutorial, modifier = Modifier.fillMaxWidth()) { Text("❓ Oyunun qaydalarını yenidən göstər") }
        Surface(onClick = vm::editProfile, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("👤 Profil", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        vm.playerName.ifBlank { "Ad yazılmayıb" },
                        vm.birthday?.let { "Ad günü: " + com.aaideas.krossvord.model.Greeting.label(it) + (vm.birthYear?.let { y -> " $y" } ?: "") },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        com.aaideas.krossvord.update.UpdateSettingsRow()
        OutlinedButton(onClick = vm::openWhatsNew, modifier = Modifier.fillMaxWidth()) { Text("🆕 Yeniliklər") }
        if (!vm.kidsMode) OutlinedButton(onClick = { vm.open(Screen.PACKS) }, modifier = Modifier.fillMaxWidth()) { Text("📦 Yeni paketlər") }
        ThemePicker(vm.themeMode, vm::changeTheme, vm.dynamicColor, vm::changeDynamicColor)
        SkinPicker(vm.activeSkin(isPremium), vm.usableSkins(isPremium), vm::changeSkin, vm::unlockAllSkinsForTesting)
        AppIconPicker(isPremium)
        Text("Hərflərin daxil edilməsi", style = MaterialTheme.typography.headlineSmall)
        listOf(
            InputMode.COLUMNS to "Anaqram: sözü sütunlardakı hərflərdən yığ",
            InputMode.TILES to "Kartlar: hərfləri bir-bir xanaya qoy",
            InputMode.KEYBOARD to "Çətin rejim: tam klaviatura",
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setInput(mode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.inputMode == mode, onClick = { vm.setInput(mode) })
                Text(label)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Artıq hərflər")
                Text("Sütunlara sözdə olmayan ${WordBuilder.EXTRA_LETTERS} hərf əlavə olunur", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.extraLetters, onCheckedChange = vm::changeExtraLetters)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bir ipucu ilə başla")
                Text("Qalan ipucları istəyəndə açılır", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.hideClues, onCheckedChange = vm::changeHideClues)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Böyük ipucu")
                Text("Seçilmiş sözün ipucu şəbəkənin altında iri şriftlə də görünür. Telefonda iri şrift varsa, özü açılır.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.bigClues, onCheckedChange = vm::changeBigClues)
        }
        Column {
            Text("Köməkçi")
            Text("Çətin sözdə kömək təklif edir. «Yox» seçilsə, ipucu xanası sadəcə qızılı işıqlanır.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<StuckStyle?>(null) + MASCOTS).forEach { h ->
                    FilterChip(
                        selected = vm.helper == h,
                        onClick = { vm.changeHelper(h) },
                        label = { Text(when (h) { null -> "Yox"; StuckStyle.KEY_TALL -> "🔑 Açar"; StuckStyle.KEY -> "🗝 Dəyirmi açar"; StuckStyle.OWL -> "🦉 Bayquş"; else -> h.name }) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Səs")
                Text("Söz tapılanda və səviyyə həll olunanda", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.soundOn, onCheckedChange = vm::changeSound)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vibrasiya")
                Text("Düz sözdə telefon yüngülcə titrəyir", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.vibrateOn, onCheckedChange = vm::changeVibrate)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("«Bravo!» səsli tərif")
                Text("Düz sözdə tərif səslə deyilir (telefonda Azərbaycan səsi varsa)", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.praiseVoice, onCheckedChange = vm::changePraiseVoice)
        }
        ReminderSwitch()
        Spacer(Modifier.height(8.dp))
        Text("İrəliləyiş", style = MaterialTheme.typography.headlineSmall)
        Text("Həll olunub: ${vm.completed.size} / ${vm.levels.size}. Həll olunmuş səviyyəni siyahıdan yenidən oynamaq olar.")
        var confirmReset by remember { mutableStateOf(false) }
        BackupButtons()
        OutlinedButton(onClick = { confirmReset = true }) { Text("Bütün irəliləyişi sıfırla") }
        if (confirmReset) ResetAllDialog(onConfirm = { confirmReset = false; vm.resetAllProgress() }, onDismiss = { confirmReset = false })
        Spacer(Modifier.height(8.dp))
        Text("Premium", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (isPremium) "Premium aktivdir: reklam yoxdur, ipucları pulsuzdur."
            else "Premium: səviyyələr arasında reklam olmur, ipucları reklamsız açılır."
        )
        if (isPremium) OutlinedButton(onClick = vm.premium::resetForTesting) { Text("Premiumu söndür (test)") }
        else Button(onClick = vm.premium::purchase) { Text("Premium al (test, ödənişsiz)") }
        Text("Prototip: alış hələ Google Play Billing ilə bağlı deyil.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Proqram haqqında", style = MaterialTheme.typography.headlineSmall)
        Text("Mənbələr", style = MaterialTheme.typography.titleMedium)
        Text("Bu paketdəki sözlər və ipucları qaralamadır, redaktə gözləyir.", style = MaterialTheme.typography.bodyMedium)
        CREDITS.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Fake ad so the flow can be tried before AdMob is added. */
@Composable
fun AdPlaceholderHost(ads: PlaceholderAdService) {
    val req = ads.pending ?: return
    AlertDialog(
        onDismissRequest = { ads.finish(watched = false) },
        title = { Text(if (req.kind == AdKind.REWARDED) "Reklam (mükafatlı)" else "Reklam") },
        text = {
            Text(
                if (req.kind == AdKind.REWARDED) "Burada mükafatlı reklam olacaq. Sonunda: ${req.reason}."
                else "Burada səviyyələr arası reklam olacaq."
            )
        },
        confirmButton = {
            Button(onClick = { ads.finish(watched = true) }) {
                Text(if (req.kind == AdKind.REWARDED) "Baxdım, mükafatı al" else "Bağla")
            }
        },
        dismissButton = if (req.kind == AdKind.REWARDED) {
            { TextButton(onClick = { ads.finish(watched = false) }) { Text("İmtina") } }
        } else null,
    )
}

/**
 * Opt-in daily reminder for the word of the day. On Android 13+ it first asks for the
 * notification permission; if the player says no, the switch stays off and says why.
 */
@Composable
private fun ReminderSwitch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val remind = com.aaideas.krossvord.remind.DailyReminder
    var on by remember { mutableStateOf(remind.isOn(context) && remind.canNotify(context)) }
    var denied by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        denied = !granted
        on = granted
        remind.set(context, granted)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Günün sözü xatırlatması")
            Text(
                if (denied) "Bildiriş icazəsi verilmədi. Telefonun ayarlarından açmaq olar."
                else "Hər gün saat ${remind.HOUR}:00 civarı, söz hələ tapılmayıbsa",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = on, onCheckedChange = { want ->
            if (want && !remind.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33) {
                ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                on = want
                remind.set(context, want)
            }
        })
    }
}

/**
 * Progress to a file and back, for a new phone or after reinstalling. The phone's own file
 * picker chooses where (Downloads, Google Drive, sent by WhatsApp...). Restoring asks first,
 * then restarts the game with the saved progress.
 */
@Composable
private fun BackupButtons() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(com.aaideas.krossvord.data.ProgressBackup.export(context).toByteArray()) }
            "✓ Saxlanıldı. Yeni telefonda «Fayldan bərpa et» ilə açın."
        }.getOrElse { "Saxlamaq alınmadı." }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null || !text.contains(com.aaideas.krossvord.data.ProgressBackup.MARK)) note = "Bu fayl Açar Söz faylı deyil."
        else pending = text
    }
    OutlinedButton(onClick = { save.launch("acar-soz-${java.time.LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("💾 İrəliləyişi fayla saxla") }
    OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("📂 Fayldan bərpa et") }
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    pending?.let { text ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("İrəliləyiş bərpa olunsun?") },
            text = { Text("Bu telefondakı irəliləyiş fayldakı ilə əvəz olunacaq. Oyun yenidən açılacaq.") },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    if (com.aaideas.krossvord.data.ProgressBackup.import(context, text)) restartApp(context)
                    else note = "Fayl oxunmadı, heç nə dəyişmədi."
                }) { Text("Bərpa et") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("İmtina") } },
        )
    }
}

/** Starts the game again from scratch, so every screen reads the restored progress. */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
