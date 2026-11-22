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

    @Test fun reminderAskedOnlyFromSecondLaunch() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
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
