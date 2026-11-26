package com.aaideas.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.aaideas.launcher.data.IconProcessor
import com.aaideas.launcher.data.PlacedWidget
import com.aaideas.launcher.data.WidgetChoice
import com.aaideas.launcher.ui.GlassAppIcon
import com.aaideas.launcher.ui.HOME_PAGE
import com.aaideas.launcher.ui.WidgetPage
import com.aaideas.launcher.ui.WidgetPickerOverlay
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
    private fun HomeContent(page: Int = HOME_PAGE, showDefault: Boolean = false, widgetPage: @Composable () -> Unit = {}) {
        val pager = rememberPagerState(initialPage = page) { HOME_PAGE + 1 + (layout.rest.size + 19) / 20 }
        HomeScreen(
            HomeUi(
                layout, icons, emptyMap(), 60.dp, true, 1795711200000L,
                WeatherNow("Баку", 12, 14, 8, "Облачно"), false, null, showDefault,
                { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                widgetPage = widgetPage,
            ),
            pager,
        )
    }

    @Test fun home() {
        rule.setContent { Frame { HomeContent() } }
        rule.onRoot().captureRoboImage("screenshots/home.png")
    }

    @Test fun page2() {
        rule.setContent { Frame { HomeContent(page = HOME_PAGE + 1) } }
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

    /** Fake widgets drawn as plain cards: real widgets need real apps on a phone. */
    @Test fun widgets() {
        val placed = listOf(PlacedWidget(1, 170), PlacedWidget(2, 120))
        rule.setContent {
            Frame {
                HomeContent(page = 0) {
                    WidgetPage(placed, {}) { w, mod ->
                        Box(mod.background(if (w.id == 1) Color(0xFFF4F1EA) else Color(0xFF1F2937)).padding(20.dp)) {
                            androidx.compose.material3.Text(
                                if (w.id == 1) "Заметки\n• Купить хлеб\n• Позвонить маме" else "▶  Музыка — пауза",
                                color = if (w.id == 1) Color(0xFF1B1F24) else Color.White, fontSize = 17.sp,
                            )
                        }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("screenshots/widgets.png")
    }

    @Test fun widgetPicker() {
        fun preview(c: Long, w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.toInt() }
            cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 40f, 40f, p)
            p.color = 0x88FFFFFF.toInt(); cv.drawRoundRect(RectF(30f, 30f, w * 0.6f, 70f), 20f, 20f, p)
        }.asImageBitmap()
        val choices = listOf(
            WidgetChoice(null, "Заметки", "Список", preview(0xFFF4E7B5, 520, 260), "4×2"),
            WidgetChoice(null, "Заметки", "Одна заметка", preview(0xFFF4E7B5, 260, 260), "2×2"),
            WidgetChoice(null, "Музыка", "Плеер", preview(0xFF1F2937, 520, 130), "4×1"),
        )
        rule.setContent { Frame { WidgetPickerOverlay(choices, {}, {}) } }
        rule.onRoot().captureRoboImage("screenshots/widget-picker.png")
    }

    /**
     * The real icon processor on icons built like the ones from the owner's phone:
     * a black adaptive icon, a full-picture legacy icon, a white-background icon and one
     * with a themed (monochrome) layer. Top row: original, bottom row: on glass.
     */
    @Test fun iconProcessing() {
        val size = 216
        fun bmp(draw: (Canvas, Paint) -> Unit) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            draw(Canvas(this), Paint(Paint.ANTI_ALIAS_FLAG))
        }
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        fun d(b: Bitmap) = android.graphics.drawable.BitmapDrawable(res, b)
        val big = size * 3 / 2 // adaptive layers are 108dp for a 72dp icon
        fun layer(draw: (Canvas, Paint) -> Unit) = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888).apply { draw(Canvas(this), Paint(Paint.ANTI_ALIAS_FLAG)) }
        val black = android.graphics.drawable.AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(0xFF000000.toInt()),
            d(layer { c, p -> p.color = 0xFFFFFFFF.toInt(); c.drawCircle(big / 2f, big / 2f, big * 0.16f, p); p.color = 0xFFE11D48.toInt(); c.drawCircle(big / 2f, big / 2f, big * 0.07f, p) }),
        )
        val picture = d(bmp { c, p ->
            p.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF7C3AED.toInt(), 0xFFF97316.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 50f, 50f, p)
            p.shader = null; p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 16f
            c.drawRoundRect(RectF(size * 0.28f, size * 0.28f, size * 0.72f, size * 0.72f), 34f, 34f, p)
            p.style = Paint.Style.FILL; c.drawCircle(size * 0.5f, size * 0.5f, size * 0.09f, p)
        })
        val white = android.graphics.drawable.AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt()),
            d(layer { c, p -> p.color = 0xFF1A73E8.toInt(); c.drawRect(big * 0.36f, big * 0.36f, big * 0.64f, big * 0.64f, p) }),
        )
        val green = android.graphics.drawable.AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(0xFF0F9D58.toInt()),
            d(layer { c, p -> p.color = 0xFFFFFFFF.toInt(); val path = android.graphics.Path().apply { moveTo(big * 0.4f, big * 0.34f); lineTo(big * 0.68f, big * 0.5f); lineTo(big * 0.4f, big * 0.66f); close() }; c.drawPath(path, p) }),
        )
        val list = listOf("Чёрный фон" to black, "Картинка" to picture, "Белый фон" to white, "Зелёный" to green)
        val processed = list.map { (_, dr) -> IconProcessor.process(dr, size) }
        rule.setContent {
            Frame {
                Column(Modifier.fillMaxSize().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        for ((_, dr) in list) Image(dr.toBitmap(size, size).asImageBitmap(), null, Modifier.size(64.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        for ((i, p) in processed.withIndex()) GlassAppIcon(p, list[i].first, 64.dp, null, Modifier)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("screenshots/icons.png")
    }

    /**
     * Icons that 0.1 showed as plain pictures on the owner's phone (cut from their screenshot):
     * top row as they were, bottom row what 0.2 makes of them.
     */
    @Test fun realPictureIcons() {
        val dir = java.io.File(javaClass.classLoader!!.getResource("real-icons")!!.toURI())
        val files = dir.listFiles()!!.filter { f -> listOf("38-0-0", "abb", "aliexpress", "araz", "birbank", "birmarket").any { f.name.endsWith("-$it.png") } }.sortedBy { it.name }
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val originals = files.map { android.graphics.BitmapFactory.decodeFile(it.path) }
        val processed = originals.map { IconProcessor.process(android.graphics.drawable.BitmapDrawable(res, Bitmap.createScaledBitmap(it, 216, 216, true)), 216) }
        rule.setContent {
            Frame {
                Column(Modifier.fillMaxSize().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { for (b in originals) Image(b.asImageBitmap(), null, Modifier.size(52.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { for (p in processed) GlassAppIcon(p, "", 52.dp, null, Modifier) }
                }
            }
        }
        rule.onRoot().captureRoboImage("screenshots/real-icons.png")
    }

    /**
     * Icons built like the ones that went wrong in 0.2 on the owner's phone:
     * a solid block as the "monochrome" layer (Azercell, Azərişıq), a colour picture as the
     * monochrome layer (Araz), a purple card on white (atb360), a pale background (4PDA, Camera),
     * a many-coloured logo on white (Sketch). Top row: original, bottom row: 0.3.
     */
    @Test fun hardIcons() {
        val big = 324
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        fun layer(draw: (Canvas, Paint) -> Unit) = android.graphics.drawable.BitmapDrawable(res,
            Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888).apply { draw(Canvas(this), Paint(Paint.ANTI_ALIAS_FLAG)) })
        fun color(c: Long) = android.graphics.drawable.ColorDrawable(c.toInt())
        fun text(c: Canvas, p: Paint, t: String, size: Float, col: Long, y: Float = big / 2f + size / 3) {
            p.color = col.toInt(); p.textSize = size; p.isFakeBoldText = true; p.textAlign = Paint.Align.CENTER; c.drawText(t, big / 2f, y, p)
        }
        val m = big / 2f
        // Azercell-like: purple background, white card with the purple logo, card copied as "monochrome".
        val card = layer { c, p -> p.color = 0xFFFFFFFF.toInt(); c.drawRect(m - 70, m - 70, m + 70, m + 70, p); p.color = 0xFF5B2C8F.toInt(); c.drawCircle(m, m, 38f, p); p.color = 0xFFFFFFFF.toInt(); c.drawCircle(m, m, 20f, p) }
        val azercell = android.graphics.drawable.AdaptiveIconDrawable(color(0xFF5B2C8F), card, card)
        // Araz-like: white background, green/orange word, the same picture as "monochrome".
        val word = layer { c, p -> text(c, p, "araz", 62f, 0xFF4CAF50); text(c, p, "15", 40f, 0xFFF57C00, m + 60) }
        val araz = android.graphics.drawable.AdaptiveIconDrawable(color(0xFFFFFFFF), word, word)
        // atb360-like: white background, purple rounded card with white text.
        val atb = android.graphics.drawable.AdaptiveIconDrawable(color(0xFFFFFFFF), layer { c, p ->
            p.color = 0xFF6D28D9.toInt(); c.drawRoundRect(RectF(m - 72, m - 60, m + 72, m + 60), 26f, 26f, p)
            text(c, p, "atb", 50f, 0xFFFFFFFF, m - 4); text(c, p, "360", 40f, 0xFFFFFFFF, m + 40)
        }, null)
        // 4PDA-like: pale blue-grey background, white "4".
        val pale = android.graphics.drawable.AdaptiveIconDrawable(color(0xFFC9D6E8), layer { c, p -> text(c, p, "4", 130f, 0xFFFFFFFF) }, null)
        // Sketch-like: white background, many-coloured fan.
        val fan = android.graphics.drawable.AdaptiveIconDrawable(color(0xFFFFFFFF), layer { c, p ->
            val cols = longArrayOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA)
            for ((i, col) in cols.withIndex()) {
                p.color = col.toInt()
                c.drawArc(RectF(m - 80, m - 70, m + 80, m + 90), 200f + i * 23.3f, 23.3f, true, p)
            }
            p.color = 0xFF333333.toInt(); c.drawRect(m - 22, m + 10, m + 22, m + 60, p)
        }, null)
        val list = listOf(azercell, araz, atb, pale, fan)
        val processed = list.map { IconProcessor.process(it, 216) }
        rule.setContent {
            Frame {
                Column(Modifier.fillMaxSize().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        for (d in list) Image(d.toBitmap(216, 216).asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(30)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { for (p in processed) GlassAppIcon(p, "", 56.dp, null, Modifier) }
                }
            }
        }
        rule.onRoot().captureRoboImage("screenshots/hard-icons.png")
    }

    @Suppress("unused") private fun block() = runBlocking { }
}
