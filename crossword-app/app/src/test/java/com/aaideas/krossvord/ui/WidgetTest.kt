package com.aaideas.krossvord.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaideas.krossvord.widget.WordWidget
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class WidgetTest {
    @Test fun widgetShowsTodaysWordStatus() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val (title, body) = WordWidget.text(app)
        assertTrue(title.startsWith("Günün sözü"))
        assertTrue(body, body.contains("hərf"))
        WordWidget.views(app)
        WordWidget.refresh(app)
    }

    @Test fun reminderIsOnByDefaultSoNobodyIsAsked() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(com.aaideas.krossvord.remind.DailyReminder.isOn(app))
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val vm = GameViewModel(app)
        vm.onAppOpen(); vm.dismissWelcome()
        vm.onAppOpen(); vm.dismissWelcome()
        assertTrue(!vm.askReminder)
    }

    /** Android 13+: on by default, but without the permission nothing would come, so it is asked once. */
    @Test fun reminderWithoutPermissionIsAskedOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        vm.onAppOpen(); vm.dismissWelcome()
        assertTrue(!vm.askReminder)
        vm.onAppOpen(); vm.dismissWelcome()
        assertTrue(vm.askReminder)
    }

    /** Turned off by the player (or an old install): asked once, from the second launch. */
    @Test fun reminderAskedOnlyFromSecondLaunch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("reminder", 0).edit().putBoolean("on", false).commit()
        val vm = GameViewModel(app)
        vm.onAppOpen()
        vm.dismissWelcome()
        assertTrue(!vm.askReminder)
        vm.onAppOpen()
        vm.dismissWelcome()
        assertTrue(vm.askReminder)
        vm.closeReminderAsk()
        vm.onAppOpen()
        assertTrue(!vm.askReminder)
    }
}
