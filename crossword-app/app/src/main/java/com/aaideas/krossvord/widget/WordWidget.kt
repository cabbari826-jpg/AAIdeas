package com.aaideas.krossvord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aaideas.krossvord.R
import com.aaideas.krossvord.model.AzText
import com.aaideas.krossvord.model.DailyWords
import com.aaideas.krossvord.remind.DailyReminder
import com.aaideas.krossvord.ui.DailyWordGame
import com.aaideas.krossvord.ui.MainActivity

/** Home-screen widget: today's word-of-the-day status and the streak; a tap opens the word. */
class WordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = views(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** Text for the widget, from the same saved state the game uses. */
        fun text(context: Context): Pair<String, String> {
            val prefs = context.getSharedPreferences("daily_word", Context.MODE_PRIVATE)
            val today = DailyWords.dayNumber()
            val words = runCatching {
                context.assets.open("word_of_day.json").bufferedReader(Charsets.UTF_8).use { DailyWords.parse(it.readText()) }
            }.getOrDefault(emptyList())
            if (words.isEmpty()) return "Günün sözü" to ""
            val word = DailyWords.forDay(words, today)
            val sameDay = prefs.getLong(DailyWordGame.KEY_DAY, Long.MIN_VALUE) == today
            val solved = sameDay && prefs.getBoolean(DailyWordGame.KEY_SOLVED, false)
            val attempts = if (sameDay) prefs.getInt(DailyWordGame.KEY_ATTEMPTS, 0) else 0
            val last = prefs.getLong(DailyWordGame.KEY_LAST_SOLVED, Long.MIN_VALUE)
            val streak = if (last >= today - 1) prefs.getInt(DailyWordGame.KEY_STREAK, 0) else 0
            val title = "Günün sözü" + if (streak > 0) "  🔥 $streak" else ""
            val body = when {
                solved -> "✓ Tapıldı: ${word.word}"
                attempts >= DailyWords.MAX_ATTEMPTS -> "Sabah yeni söz"
                else -> "${AzText.letters(word.word).size} hərf. ${word.clue}"
            }
            return title to body
        }

        fun views(context: Context): RemoteViews {
            val (title, body) = text(context)
            val open = PendingIntent.getActivity(
                context, 7,
                Intent(context, MainActivity::class.java).putExtra(DailyReminder.EXTRA_OPEN, DailyReminder.OPEN_WORD_OF_DAY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_word).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_text, body)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }

        /** Called by the app when the word of the day changes state. Cheap when no widget is placed. */
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            val ids = m.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, views(context))
        }
    }
}
