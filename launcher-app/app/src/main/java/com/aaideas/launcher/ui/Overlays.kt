package com.aaideas.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.GlassIcon
import kotlin.math.roundToInt

/** Light veil over the (blurred) home screen behind an overlay. Tap closes. */
@Composable
fun Veil(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color(0x33D2E4EA))
            .clickable(MutableInteractionSource(), null, onClick = onDismiss),
    )
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    icons: Map<String, GlassIcon>,
    colors: Map<String, Int>,
    iconSize: androidx.compose.ui.unit.Dp,
    onOpen: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    onWebSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalInk.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val found = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    Veil(onDismiss)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = SIDE)) {
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().height(54.dp).glass(RoundedCornerShape(27.dp), extraWhite = 0.2f).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(Glyphs.Search, ink.text, 22.dp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Поиск приложений", color = ink.text.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(ink.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        found.firstOrNull()?.let { onOpen(it, Rect.Zero) } ?: if (query.isNotBlank()) onWebSearch(query) else Unit
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (query.isNotBlank()) {
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp).glass(RoundedCornerShape(24.dp))
                    .clickable(MutableInteractionSource(), null) { onWebSearch(query) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlyphIcon(Glyphs.Globe, ink.text, 20.dp)
                Text("Искать «${query.trim()}» в интернете", color = ink.text, fontSize = 15.sp, maxLines = 1)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(found, key = { it.key }) { app ->
                AppCell(app, icons[app.key], colors[app.key], iconSize, true, { onOpen(app, it) }, { onMenu(app, it) })
            }
        }
    }
}

// ---------------------------------------------------------------- Long-press menu

val GlassColors = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFF22A35A, 0xFF10B981, 0xFF0EA5E9,
    0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899, 0xFFA16207, 0xFF64748B,
).map { it.toInt() }

@Composable
fun AppMenuOverlay(
    app: AppEntry,
    anchor: Rect,
    icon: GlassIcon?,
    customColor: Int?,
    iconSize: androidx.compose.ui.unit.Dp,
    shortcuts: List<ShortcutInfo>,
    onShortcut: (ShortcutInfo) -> Unit,
    onColor: (Int?) -> Unit,
    onDetails: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickColor by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Veil(onDismiss)
    Layout(
        content = {
            Box { LiftedIcon(icon, app.label, iconSize, customColor) }
            Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                if (pickColor) ColorPanel(customColor ?: icon?.color, onColor) else MenuPanel(app, shortcuts, onShortcut, { pickColor = true }, onDetails, onHide, onUninstall)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, c ->
        val side = with(density) { 24.dp.roundToPx() }
        val gap = with(density) { 14.dp.roundToPx() }
        val iconP = measurables[0].measure(Constraints())
        val menuP = measurables[1].measure(Constraints(maxWidth = c.maxWidth - 2 * side, maxHeight = c.maxHeight))
        layout(c.maxWidth, c.maxHeight) {
            val ix = anchor.left.roundToInt()
            val iy = anchor.top.roundToInt()
            iconP.place(ix, iy)
            val below = anchor.bottom.roundToInt() + gap
            val y = if (below + menuP.height <= c.maxHeight - gap) below
            else (anchor.top.roundToInt() - gap - menuP.height).coerceAtLeast(gap)
            menuP.place(side, y)
        }
    }
}

@Composable
private fun MenuPanel(
    app: AppEntry,
    shortcuts: List<ShortcutInfo>,
    onShortcut: (ShortcutInfo) -> Unit,
    onPickColor: () -> Unit,
    onDetails: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), extraWhite = 0.25f).padding(vertical = 6.dp)) {
        Text(app.label, color = ink.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp))
        if (shortcuts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (s in shortcuts) {
                    Box(
                        Modifier.weight(1f).height(60.dp).glass(RoundedCornerShape(20.dp), extraWhite = 0.15f)
                            .clickable(MutableInteractionSource(), null) { onShortcut(s) }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((s.shortLabel ?: s.longLabel ?: "").toString(), color = ink.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                }
            }
        }
        MenuRow(Glyphs.Palette, "Цвет стекла", ink.text, onPickColor)
        Sep()
        MenuRow(Glyphs.Info, "О приложении", ink.text, onDetails)
        Sep()
        MenuRow(Glyphs.Eye, "Скрыть", ink.text, onHide)
        Sep()
        MenuRow(Glyphs.Trash, "Удалить приложение", Color(0xFFB42318), onUninstall)
    }
}

@Composable
private fun ColorPanel(current: Int?, onColor: (Int?) -> Unit) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), extraWhite = 0.25f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Цвет стекла", color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        for (row in GlassColors.chunked(6)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in row) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .then(if (c == current) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { onColor(c) },
                    )
                }
            }
        }
        GlassButton("Как у приложения", { onColor(null) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlyphIcon(icon, color, 22.dp)
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = color.copy(alpha = 0.7f), fontSize = 14.sp) }
    }
}

@Composable
fun Sep() {
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(LocalInk.current.text.copy(alpha = 0.12f)))
}

@Suppress("unused")
@Composable
private fun Gap(w: Int) = Spacer(Modifier.width(w.dp))
