package com.bmwcockpit.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Persistence across a real process restart. tools/capture-review.sh runs [setUp], then
 * `adb shell am force-stop com.bmwcockpit.app`, then [verify] in a new process.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceCheck {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val pkg = "com.bmwcockpit.app"
    private val outDir = File(inst.targetContext.getExternalFilesDir(null), "review").apply { mkdirs() }

    private fun launch() {
        val intent = inst.targetContext.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        inst.targetContext.startActivity(intent)
        assertTrue(device.wait(Until.hasObject(By.res("tab_PAINEL")), 60_000) == true)
        Thread.sleep(2_000)
    }

    private fun tap(tag: String) {
        repeat(12) {
            try {
                val o = device.wait(Until.findObject(By.res(tag)), 15_000) ?: throw AssertionError("'$tag' not found")
                device.click(o.visibleCenter.x, o.visibleCenter.y); Thread.sleep(900); return
            } catch (_: androidx.test.uiautomator.StaleObjectException) { Thread.sleep(200) }
        }
        throw AssertionError("could not tap '$tag'")
    }

    /** Selects Race Dashboard + Blue Ice theme and leaves the app. */
    @Test fun setUp() {
        launch()
        tap("tab_PERSONALIZAR"); tap("custom_THEMES"); tap("theme_BLUE_ICE")
        tap("tab_PAINEL")
        repeat(6) {
            if (device.wait(Until.hasObject(By.res("dash_race")), 1_500) == true) return@repeat
            tap("dash_next")
        }
        assertTrue(device.hasObject(By.res("dash_race")))
        Thread.sleep(1_500) // let DataStore flush
    }

    /** Runs in a fresh process after force-stop: the layout and theme must have been restored. */
    @Test fun verify() {
        launch()
        assertTrue("layout not restored after restart", device.wait(Until.hasObject(By.res("dash_race")), 20_000) == true)
        device.takeScreenshot(File(outDir, "persistencia-apos-reiniciar-race-blue-ice.png"))
        // restore defaults for the next runs
        tap("tab_PERSONALIZAR"); tap("custom_THEMES"); tap("theme_M_PERFORMANCE")
        tap("tab_PAINEL")
        repeat(6) {
            if (device.wait(Until.hasObject(By.res("dash_m_performance")), 1_500) == true) return
            tap("dash_next")
        }
    }
}
