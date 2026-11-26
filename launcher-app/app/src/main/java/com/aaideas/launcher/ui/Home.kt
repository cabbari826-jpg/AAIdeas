package com.aaideas.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.GlassIcon
import com.aaideas.launcher.data.HomeLayout
import com.aaideas.launcher.data.WeatherNow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val COLUMNS = 4
const val ROWS_PER_PAGE = 5
val SIDE = 22.dp

private val ru = Locale("ru")

/** Everything the home screen shows and reports back. */
class HomeUi(
    val layout: HomeLayout,
    val icons: Map<String, GlassIcon>,
    val colors: Map<String, Int>,
    val iconSize: Dp,
    val labels: Boolean,
    val now: Long,
    val weather: WeatherNow?,
    val weatherNeedsPermission: Boolean,
    val showMakeDefault: Boolean,
    val onOpen: (AppEntry, Rect) -> Unit,
    val onMenu: (AppEntry, Rect) -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
    val onClock: () -> Unit,
    val onCalendar: () -> Unit,
    val onWeather: () -> Unit,
    val onMakeDefault: () -> Unit,
    val onDismissMakeDefault: () -> Unit,
)

@Composable
fun HomeScreen(ui: HomeUi, pager: PagerState, modifier: Modifier = Modifier) {
    val pages = remember(ui.layout) { ui.layout.pages(COLUMNS * ROWS_PER_PAGE) }
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { ui.onSettings() }) },
            ) {
                if (page == 0) FirstPage(ui) else AppGrid(pages.getOrNull(page - 1).orEmpty(), ui)
            }
        }
        PageDots(count = 1 + pages.size, current = pager.currentPage)
        SearchPill(onClick = ui.onSearch, modifier = Modifier.padding(horizontal = SIDE).padding(top = 14.dp, bottom = 16.dp))
        Dock(ui, Modifier.padding(horizontal = SIDE).padding(bottom = 18.dp))
    }
}

@Composable
private fun FirstPage(ui: HomeUi) {
    val ink = LocalInk.current
    val date = Date(ui.now)
    Column(Modifier.fillMaxSize().padding(horizontal = SIDE)) {
        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp).clickable(MutableInteractionSource(), null, onClick = ui.onClock),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(SimpleDateFormat("H:mm", ru).format(date), style = TextStyle(color = ink.text, fontSize = 84.sp, fontWeight = FontWeight.Light, shadow = Shadow(ink.shadow, blurRadius = 6f)))
            Text(capital(SimpleDateFormat("EEEE, d MMMM", ru).format(date)), style = TextStyle(color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, shadow = Shadow(ink.shadow, blurRadius = 4f)))
        }
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val w = ui.weather
            InfoCard(
                top = w?.city?.ifEmpty { "Погода" } ?: "Погода",
                big = w?.let { "${it.temp}°" } ?: "—",
                bottom = when {
                    w != null -> "${w.text} · ${w.max}°/${w.min}°"
                    ui.weatherNeedsPermission -> "Нажмите, чтобы включить"
                    else -> "Загрузка…"
                },
                onClick = ui.onWeather,
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                top = SimpleDateFormat("EEEE", ru).format(date).uppercase(ru),
                big = SimpleDateFormat("d", ru).format(date),
                bottom = capital(SimpleDateFormat("LLLL yyyy", ru).format(date)),
                onClick = ui.onCalendar,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(28.dp))
        AppRow(ui.layout.favourites, ui)
        if (ui.showMakeDefault) {
            Spacer(Modifier.weight(1f))
            MakeDefaultCard(ui)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun InfoCard(top: String, big: String, bottom: String, onClick: () -> Unit, modifier: Modifier) {
    val ink = LocalInk.current
    Column(
        modifier
            .height(160.dp)
            .glass(RoundedCornerShape(34.dp))
            .clickable(MutableInteractionSource(), null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(top, color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.3.sp)
        Text(big, color = ink.text, fontSize = 50.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(bottom, color = ink.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppRow(apps: List<AppEntry>, ui: HomeUi) {
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until COLUMNS) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                apps.getOrNull(i)?.let { app ->
                    AppCell(app, ui.icons[app.key], ui.colors[app.key], ui.iconSize, ui.labels, { ui.onOpen(app, it) }, { ui.onMenu(app, it) })
                }
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, ui: HomeUi) {
    Column(Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = 26.dp, bottom = 8.dp)) {
        for (r in 0 until ROWS_PER_PAGE) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AppRow(apps.drop(r * COLUMNS).take(COLUMNS), ui)
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape)
                    .background(ink.text.copy(alpha = if (i == current) 0.9f else 0.3f)),
            )
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Row(
        modifier.fillMaxWidth().height(52.dp)
            .glass(RoundedCornerShape(26.dp))
            .clickable(MutableInteractionSource(), null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(Glyphs.Search, ink.text, 22.dp)
        Text("Поиск", color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Dock(ui: HomeUi, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().height(ui.iconSize + 28.dp).glass(RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until COLUMNS) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ui.layout.dock.getOrNull(i)?.let { app ->
                        AppCell(app, ui.icons[app.key], ui.colors[app.key], ui.iconSize, false, { ui.onOpen(app, it) }, { ui.onMenu(app, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MakeDefaultCard(ui: HomeUi) {
    val ink = LocalInk.current
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), extraWhite = 0.18f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Сделать Şüşə главным экраном?", color = ink.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Тогда кнопка «Домой» будет открывать этот экран.", color = ink.text, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Не сейчас", ui.onDismissMakeDefault, Modifier.weight(1f))
            GlassButton("Сделать", ui.onMakeDefault, Modifier.weight(1f), strong = true)
        }
    }
}

fun capital(s: String) = s.replaceFirstChar { it.titlecase(ru) }
