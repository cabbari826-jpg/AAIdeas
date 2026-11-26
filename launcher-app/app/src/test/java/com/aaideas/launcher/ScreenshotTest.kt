package com.aaideas.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.GlassIcon
import com.aaideas.launcher.data.HomeLayout
import com.aaideas.launcher.data.Prefs
import com.aaideas.launcher.data.Wallpaper
import com.aaideas.launcher.data.WallpaperKind
import com.aaideas.launcher.data.WeatherNow
import com.aaideas.launcher.ui.AppMenuOverlay
import com.aaideas.launcher.ui.Backdrop
import com.aaideas.launcher.ui.GlassStyle
import com.aaideas.launcher.ui.HomeScreen
import com.aaideas.launcher.ui.HomeUi
import com.aaideas.launcher.ui.Ink
import com.aaideas.launcher.ui.LocalBackdrop
import com.aaideas.launcher.ui.LocalGlass
import com.aaideas.launcher.ui.LocalInk
import com.aaideas.launcher.ui.SettingsOverlay
import com.aaideas.launcher.ui.SettingsUi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots for the owner (no emulator here): ./gradlew testDebugUnitTest -Proborazzi.test.record=true */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h892dp-xxhdpi", sdk = [35])
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before fun zone() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Baku")) }

    private val glyphs = mapOf(
        "phone" to "M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z",
        "msg" to "M4 5h16v11H9l-5 4z",
        "web" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18",
        "cam" to "M3 8h4l2-3h6l2 3h4v11H3zM12 9.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z",
        "photo" to "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM3 17l5-5 4 4 3-3 6 6",
        "mail" to "M3 5h18v14H3zM3 7l9 6 9-6",
        "map" to "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 7.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z",
        "music" to "M9 18V5l11-2v13M6.5 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5zM17.5 13.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z",
        "note" to "M5 3h10l4 4v14H5zM9 10h6M9 14h6M9 18h4",
        "cal" to "M3 5h18v16H3zM3 10h18M8 3v4M16 3v4",
        "card" to "M3 6h18v12H3zM3 10h18M7 15h3",
        "video" to "M7 5h10a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4zM10 9l5 3-5 3z",
        "bag" to "M5 8h14l-1 13H6zM9 8a3 3 0 0 1 6 0",
        "star" to "M12 3l2.8 5.8 6.2.9-4.5 4.4 1 6.2L12 17.4 6.5 20.3l1-6.2L3 9.7l6.2-.9z",
        "heart" to "M12 20s-7-4.4-9-9a4.8 4.8 0 0 1 9-3 4.8 4.8 0 0 1 9 3c-2 4.6-9 9-9 9z",
        "book" to "M4 4h7v16H4zM13 4h7v16h-7z",
        "clock" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2",
        "folder" to "M3 6h6l2 2h10v11H3z",
        "calc" to "M5 3h14v18H5zM8 7h8M8 12h1M15 12h1M8 16h1M15 16h1",
        "gear" to "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3",
    )

    private fun glyph(name: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val path = PathParser.createPathFromPathData(glyphs.getValue(name))
        // Themed icons fill about the middle half of the tile.
        path.transform(Matrix().apply { setScale(size * 0.5f / 24f, size * 0.5f / 24f); postTranslate(size * 0.25f, size * 0.25f) })
        Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = size * 0.04f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
        return bmp
    }

    private val sample = listOf(
        Triple("Телефон", "phone", 0xFF22A35A), Triple("Сообщения", "msg", 0xFF3B82F6), Triple("Браузер", "web", 0xFF0EA5E9), Triple("Камера", "cam", 0xFF6B7280),
        Triple("Галерея", "photo", 0xFFF59E0B), Triple("Почта", "mail", 0xFFEF4444), Triple("Карты", "map", 0xFF10B981), Triple("Музыка", "music", 0xFFEC4899),
        Triple("Банк", "card", 0xFF0F766E), Triple("Видео", "video", 0xFFDC2626), Triple("Заметки", "note", 0xFFEAB308), Triple("Календарь", "cal", 0xFF6366F1),
        Triple("Маркет", "bag", 0xFF16A34A), Triple("Избранное", "star", 0xFFF59E0B), Triple("Здоровье", "heart", 0xFFE11D48), Triple("Книги", "book", 0xFFA16207),
        Triple("Часы", "clock", 0xFF334155), Triple("Файлы", "folder", 0xFF2563EB), Triple("Калькулятор", "calc", 0xFFF97316), Triple("Настройки", "gear", 0xFF64748B),
    )
    private val apps = sample.map { (n, k, _) -> AppEntry("p.$k/.A#0", n, ComponentName("p.$k", "p.$k.A"), null) }
    private val icons = sample.associate { (_, k, c) -> "p.$k/.A#0" to GlassIcon(glyph(k, 180).asImageBitmap(), c.toInt(), true) }
    private val layout = HomeLayout.arrange(apps, listOf("p.phone", "p.msg", "p.web", "p.cam"), listOf("p.photo", "p.mail", "p.map", "p.music"), emptySet())

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val w = 1236; val h = 2676
        val wall = Wallpaper.render(ctx, WallpaperKind.SEA, w, h)
        val blurred = Wallpaper.blurred(wall, 11, 3f)
        Box(Modifier.fillMaxSize()) {
            Image(wall.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            CompositionLocalProvider(
                LocalBackdrop provides Backdrop(blurred.asImageBitmap(), IntSize(w, h)),
                LocalGlass provides GlassStyle(0.11f, 0.9f),
                LocalInk provides Ink(Color(0xFF1B1F24), Color(0x59FFFFFF)),
            ) { content() }
        }
    }

    @Composable
    private fun HomeContent(page: Int = 0, showDefault: Boolean = false) {
        val pager = rememberPagerState(initialPage = page) { 1 + (layout.rest.size + 19) / 20 }
        HomeScreen(
            HomeUi(
                layout, icons, emptyMap(), 60.dp, true, 1795711200000L,
                WeatherNow("Баку", 12, 14, 8, "Облачно"), false, showDefault,
                { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
            ),
            pager,
        )
    }

    @Test fun home() {
        rule.setContent { Frame { HomeContent() } }
        rule.onRoot().captureRoboImage("screenshots/home.png")
    }

    @Test fun page2() {
        rule.setContent { Frame { HomeContent(page = 1) } }
        rule.onRoot().captureRoboImage("screenshots/page2.png")
    }

    @Test fun menu() {
        rule.setContent {
            Frame {
                HomeContent()
                AppMenuOverlay(apps[7], Rect(939f, 1086f, 1119f, 1266f), icons[apps[7].key], null, 60.dp, emptyList(), {}, {}, {}, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage("screenshots/menu.png")
    }

    @Test fun settings() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        val previews = listOf(WallpaperKind.SEA, WallpaperKind.SAND, WallpaperKind.NIGHT).associateWith { Wallpaper.render(ctx, it, 124, 220).asImageBitmap() }
        rule.setContent { Frame { SettingsOverlay(SettingsUi(prefs, previews, emptyList(), false, {}, {}, {}, {})) } }
        rule.onRoot().captureRoboImage("screenshots/settings.png")
    }

    @Suppress("unused") private fun block() = runBlocking { }
}
