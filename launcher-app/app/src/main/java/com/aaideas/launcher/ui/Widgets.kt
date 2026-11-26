package com.aaideas.launcher.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.launcher.data.PlacedWidget
import com.aaideas.launcher.data.WidgetChoice

val WidgetShape = RoundedCornerShape(34.dp)

/**
 * The page to the left of the home screen: the owner's widgets one under another,
 * the same width as the weather and calendar cards, and "Добавить виджет" at the end.
 * [widget] draws one real widget (an Android view); tests pass a stand-in.
 */
@Composable
fun WidgetPage(
    placed: List<PlacedWidget>,
    onAdd: () -> Unit,
    widget: @Composable (PlacedWidget, Modifier) -> Unit,
) {
    val ink = LocalInk.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SIDE).padding(top = 26.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (placed.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().glass(WidgetShape).padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlyphIcon(Glyphs.Widgets, ink.text, 40.dp)
                Text("Виджеты", color = ink.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Здесь можно держать виджеты любых приложений: заметки, музыку, календарь, часы.",
                    color = ink.text, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }
        for (w in placed) {
            widget(w, Modifier.fillMaxWidth().height(w.heightDp.dp).clip(WidgetShape))
        }
        AddWidgetPill(onAdd)
    }
}

@Composable
private fun AddWidgetPill(onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .glass(RoundedCornerShape(26.dp))
            .clickable(MutableInteractionSource(), null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(Glyphs.Plus, ink.text, 22.dp)
        Spacer(Modifier.size(10.dp))
        Text("Добавить виджет", color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Full-screen list of every widget the installed apps offer, grouped by app. */
@Composable
fun WidgetPickerOverlay(choices: List<WidgetChoice>?, onPick: (WidgetChoice) -> Unit, onBack: () -> Unit) {
    val ink = LocalInk.current
    Veil(onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = SIDE)) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp).height(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).size(46.dp).glass(CircleShape, extraWhite = 0.12f)
                    .clickable(MutableInteractionSource(), null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyphs.Back, ink.text, 22.dp) }
            Text("Виджеты", color = ink.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        when {
            choices == null -> Center("Загрузка…")
            choices.isEmpty() -> Center("Приложения на телефоне не предлагают виджетов")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                val groups = choices.groupBy { it.appLabel }.toList()
                for ((app, list) in groups) {
                    item(key = "h:$app") {
                        Text(
                            app.uppercase(), color = ink.text.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 18.dp, top = 6.dp),
                        )
                    }
                    items(list, key = { "w:$app:${it.label}:${it.info?.provider?.className}" }) { c -> ChoiceCard(c, onPick) }
                }
            }
        }
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        Text(text, color = LocalInk.current.text, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChoiceCard(c: WidgetChoice, onPick: (WidgetChoice) -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), extraWhite = 0.18f)
            .clickable(MutableInteractionSource(), null) { onPick(c) }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp), contentAlignment = Alignment.Center) {
            c.preview?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 150.dp).clip(RoundedCornerShape(18.dp))) }
                ?: GlyphIcon(Glyphs.Widgets, ink.text, 40.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(c.label, color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(c.cells, color = ink.text.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

/** Long press on a widget: move, resize, remove. Same panel style as the app menu. */
@Composable
fun WidgetMenuOverlay(
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onGrow: () -> Unit,
    onShrink: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalInk.current
    Veil(onDismiss)
    Box(Modifier.fillMaxSize().padding(horizontal = SIDE * 2), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), extraWhite = 0.3f).padding(vertical = 6.dp)) {
            if (canUp) { MenuRow(Glyphs.Up, "Переместить выше", ink.text, onUp); Sep() }
            if (canDown) { MenuRow(Glyphs.Down, "Переместить ниже", ink.text, onDown); Sep() }
            MenuRow(Glyphs.Grow, "Сделать выше", ink.text, onGrow)
            Sep()
            MenuRow(Glyphs.Shrink, "Сделать ниже", ink.text, onShrink)
            Sep()
            MenuRow(Glyphs.Trash, "Убрать виджет", Color(0xFFB42318), onRemove)
        }
    }
}
