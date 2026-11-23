package com.aaideas.krossvord.remind

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aaideas.krossvord.model.DailyWords
import com.aaideas.krossvord.ui.MainActivity
import java.util.Calendar

/**
 * Opt-in daily reminder for the word of the day, at about 10:00 local time. An inexact
 * alarm (no exact-alarm permission needed); it is set again after a reboot or app update.
 */
object DailyReminder {
    const val HOUR = 10
    const val EXTRA_OPEN = "open"
    const val OPEN_WORD_OF_DAY = "word_of_day"
    private const val CHANNEL = "word_of_day"
    private const val PREFS = "reminder"
    private const val KEY_ON = "on"

    /** On unless the player turned it off (the owner wants the reminder to come by itself). */
    fun isOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    /** Xiaomi/Redmi (MIUI) stops alarms of apps without "Autostart"; the settings show a button for it. */
    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) || Build.BRAND.equals("Redmi", ignoreCase = true)

    /** Opens MIUI's Autostart list (or the app's own settings page if that screen is missing). */
    fun openAutostart(context: Context) {
        val miui = Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(miui) }.onFailure { runCatching { context.startActivity(app) } }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // One alarm at a time, set again each time it fires. "While idle" so a phone asleep in
        // Doze still gets it (a repeating inexact alarm could slip by many hours or a whole day).
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarmIntent(context))
        else am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarmIntent(context))
    }

    private fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(alarmIntent(context))
    }

    /** Today's word already solved or failed: no need to remind. */
    private fun doneToday(context: Context): Boolean {
        val p = context.getSharedPreferences("daily_word", Context.MODE_PRIVATE)
        if (p.getLong("day", Long.MIN_VALUE) != DailyWords.dayNumber()) return false
        return p.getBoolean("solved", false) || p.getInt("attempts", 0) >= DailyWords.MAX_ATTEMPTS
    }

    fun show(context: Context) {
        if (!isOn(context) || doneToday(context) || !canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Günün sözü", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_WORD_OF_DAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Günün sözü hazırdır")
            .setContentText("Bugünkü çətin sözü təsvirdən tapa bilərsənmi?")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) }
    }
}

/** Fires once a day (shows the reminder) and after reboot or an app update (sets the alarm again). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                if (DailyReminder.isOn(context)) DailyReminder.schedule(context)
            else -> { DailyReminder.show(context); if (DailyReminder.isOn(context)) DailyReminder.schedule(context) }
        }
    }
}
