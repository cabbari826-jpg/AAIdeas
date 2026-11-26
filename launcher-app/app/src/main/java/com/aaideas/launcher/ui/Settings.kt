package com.aaideas.launcher.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.Prefs
import com.aaideas.launcher.data.WallpaperKind
import kotlin.math.roundToInt

class SettingsUi(
    val prefs: Prefs,
    val previews: Map<WallpaperKind, ImageBitmap>,
    val hiddenApps: List<AppEntry>,
    val isDefault: Boolean,
    val onPickPhoto: () -> Unit,
    val onMakeDefault: () -> Unit,
    val onBack: () -> Unit,
    val extra: @Composable () -> Unit,
)

@Composable
fun SettingsOverlay(ui: SettingsUi) {
    val ink = LocalInk.current
    val p = ui.prefs
    Veil(ui.onBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = SIDE).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).size(46.dp).glass(CircleShape, extraWhite = 0.12f)
                    .clickable(MutableInteractionSource(), null, onClick = ui.onBack),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyphs.Back, ink.text, 22.dp) }
            Text("Настройки", color = ink.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        Card("СТЕКЛО") {
            LabeledSlider("Размытие", p.blur, 0..24, "${p.blur}", p::changeBlur)
            LabeledSlider("Белизна", p.whiteness, 0..45, "${p.whiteness}%", p::changeWhiteness)
            LabeledSlider("Оттенок приложения", p.tintStrength, 0..90, "${p.tintStrength}%", p::changeTintStrength)
            LabeledSlider("Размер иконок", p.iconScale, 80..120, "${p.iconScale}%", p::changeIconScale)
        }

        Card("ОБОИ") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((kind, name) in listOf(WallpaperKind.SEA to "Море", WallpaperKind.SAND to "Песок", WallpaperKind.NIGHT to "Ночь", WallpaperKind.PHOTO to "Фото")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val selected = p.wallpaper == kind
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier.size(width = 62.dp, height = 110.dp).clip(shape)
                                .border(if (selected) 3.dp else 1.dp, if (selected) ink.text else Color.White.copy(alpha = 0.6f), shape)
                                .clickable { if (kind == WallpaperKind.PHOTO) ui.onPickPhoto() else p.changeWallpaper(kind) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val prev = ui.previews[kind]
                            if (prev != null) Image(prev, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else GlyphIcon(Glyphs.Image, ink.text, 26.dp)
                        }
                        Text(name, color = ink.text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        Card("ВИД") {
            SwitchRow("Подписи под иконками", p.labels, p::changeLabels)
            Sep()
            SwitchRow("Тёмный текст", p.darkInk, p::changeDarkInk)
        }

        if (ui.hiddenApps.isNotEmpty()) {
            Card("СКРЫТЫЕ ПРИЛОЖЕНИЯ") {
                ui.hiddenApps.forEachIndexed { i, app ->
                    if (i > 0) Sep()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, color = ink.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        GlassButton("Вернуть", { p.unhide(app.key) })
                    }
                }
            }
        }

        Card("ЛАУНЧЕР") {
            if (!ui.isDefault) {
                MenuRow(Glyphs.Home, "Сделать главным экраном", ink.text, ui.onMakeDefault)
                Sep()
            }
            ui.extra()
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), extraWhite = 0.23f).padding(vertical = 8.dp)) {
        Text(title, color = ink.text.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp))
        content()
    }
}

@Composable
private fun LabeledSlider(title: String, value: Int, range: IntRange, shown: String, onChange: (Int) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, color = ink.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(shown, color = ink.text.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ink.text, inactiveTrackColor = ink.text.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = ink.text, checkedThumbColor = Color.White))
    }
}
