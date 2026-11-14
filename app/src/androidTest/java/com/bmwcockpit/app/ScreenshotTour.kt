package com.bmwcockpit.app

import android.content.Intent
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real app with UiAutomator (test tags are exposed as resource ids), exercises every screen and
 * writes screenshots to the app's external files dir (`files/review/<prefix>-NN-name.png`), which
 * tools/capture-review.ps1 / CI pull into review/ (outside the app resources).
 *
 * Instrumentation args: `prefix` (default "shot") and `full` ("true" captures every screen, otherwise only the
 * main dashboards — used for the extra resolutions).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val pkg = "com.bmwcockpit.app"
    private val args = InstrumentationRegistry.getArguments()
    private val prefix = args.getString("prefix") ?: "shot"
    private val full = (args.getString("full") ?: "true") == "true"
    private val outDir = File(inst.targetContext.getExternalFilesDir(null), "review").apply { mkdirs() }
    private var n = 0

    @Before fun launch() {
        device.pressHome()
        val intent = inst.targetContext.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        inst.targetContext.startActivity(intent)
        assertNotNull("app did not start", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 60_000))
        assertTrue("header not shown", device.wait(Until.hasObject(By.res("tab_PAINEL")), 60_000) == true)
        Thread.sleep(2_000) // first frames on a cold emulator
    }

    private fun obj(tag: String, timeout: Long = 15_000): UiObject2 =
        device.wait(Until.findObject(By.res(tag)), timeout) ?: throw AssertionError("element '$tag' not found")

    /**
     * Taps the centre of the element. Live instruments recompose many times per second, so the node is
     * re-found and its bounds re-read on every attempt; the tap itself is a plain coordinate tap.
     */
    private fun click(tag: String, settle: Long = 700) {
        var last: Throwable? = null
        repeat(8) {
            try {
                val c = obj(tag).visibleCenter
                device.click(c.x, c.y)
                Thread.sleep(settle)
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) { last = e; Thread.sleep(200) }
        }
        throw AssertionError("could not click '$tag'", last)
    }

    private fun shot(name: String) {
        Thread.sleep(400)
        n++
        val f = File(outDir, "%s-%02d-%s.png".format(prefix, n, name))
        assertTrue("screenshot failed: $f", device.takeScreenshot(f))
    }

    private fun text(tag: String): String = obj(tag).let { o -> o.text ?: o.children.joinToString("") { it.text ?: "" } }

    private fun goLayouts() {
        if (device.findObject(By.res("layout_M_PERFORMANCE")) != null) return
        // From the dashboard one tap on Painel opens Multitelas; from other tabs the first tap returns to the dashboard.
        click("tab_PAINEL", 1_000)
        if (device.wait(Until.hasObject(By.res("layout_M_PERFORMANCE")), 2_500) != true) click("tab_PAINEL", 1_000)
        obj("layout_M_PERFORMANCE", 10_000)
    }

    private fun useLayout(id: String) {
        goLayouts()
        // scroll the row until the card is visible
        repeat(4) { if (device.findObject(By.res("layout_$id")) == null) click("layouts_next", 500) }
        click("layout_$id", 1_200)
    }

    @Test fun tour() {
        // Make sure the demo source is active and running.
        click("tab_CONFIG")
        obj("conn_status")
        device.findObject(By.text("Demonstração (simulador)"))?.click()
        Thread.sleep(800)

        useLayout("M_PERFORMANCE")
        obj("dash_m_performance")
        // Two instants: values and needles must change.
        shot("m-performance-t0")
        val s0 = text("rpm_value") + "|" + text("speed_value")
        Thread.sleep(2_500)
        shot("m-performance-t1")
        val s1 = text("rpm_value") + "|" + text("speed_value")
        assertNotEquals("instruments did not update", s0, s1)

        useLayout("RACE"); obj("dash_race"); shot("race-dashboard")
        useLayout("GT"); obj("dash_gt"); Thread.sleep(1_500); shot("gt-telemetry")
        useLayout("MINIMAL"); obj("dash_minimal"); shot("minimal")
        if (!full) { useLayout("M_PERFORMANCE"); return }

        goLayouts(); shot("multitelas")
        click("layout_M_PERFORMANCE", 1_000)

        // Configurable card: open the sensor picker and change card 1 to Throttle, then back to Boost.
        click("card_0"); obj("pick_THROTTLE"); shot("sensor-picker"); click("pick_THROTTLE")
        click("card_0"); click("pick_BOOST")

        // Demo controls dialog
        click("demo_badge"); shot("demo-dialog"); device.pressBack(); Thread.sleep(400)

        // Telemetry
        click("tab_TELEMETRIA"); obj("telemetry_chart"); Thread.sleep(2_000); shot("telemetria-graficos")
        click("chip_RPM"); click("chip_SPEED"); Thread.sleep(1_500); shot("telemetria-eixos-multiplos")
        click("chart_pause"); shot("telemetria-pausado"); click("chart_resume")
        click("chip_RPM"); click("chip_SPEED")
        click("rec_start", 2_500); click("rec_stop", 1_000)
        click("rail_tele/logs"); shot("telemetria-logs")

        // Performance (simulated launch from standstill)
        click("rail_tele/zero100"); click("perf_sim_launch")
        device.wait(Until.hasObject(By.res("perf_big").textContains(",")), 25_000)
        Thread.sleep(9_000)
        shot("performance-0-100")
        click("rail_tele/quarter"); shot("performance-quarto-milha")
        click("rail_tele/laps"); click("lap_start", 3_000); click("lap_mark", 2_000); shot("voltas")
        click("lap_stop")

        // Diagnostics (demo example, clearly separated)
        click("rail_tele/diag"); click("diag_demo", 1_000); shot("diagnostico")

        // Editor: select, drag, resize, style, colour, cancel / save
        click("tab_PERSONALIZAR"); obj("editor_area")
        val widget = device.findObject(By.res(java.util.regex.Pattern.compile("widget_w3")))
        assertNotNull(widget)
        widget.click(); Thread.sleep(400)
        val b0 = widget.visibleBounds
        widget.drag(Point(b0.centerX() + 60, b0.centerY() + 40), 600); Thread.sleep(600)
        val resize = obj("resize_w3")
        val rb = resize.visibleBounds
        resize.drag(Point(rb.centerX() + 50, rb.centerY() + 30), 600); Thread.sleep(600)
        click("style_CIRCULAR"); click("color_ff8a4bff")
        shot("editor-arraste-redimensionado")
        val moved = device.findObject(By.res("widget_w3")).visibleBounds
        assertTrue("widget did not move/resize: $b0 -> $moved", moved != b0)
        click("editor_cancel"); Thread.sleep(500)
        shot("editor-apos-cancelar")
        click("lib_IAT"); shot("editor-instrumento-adicionado")
        click("editor_restore")

        // Themes
        click("tab_PERSONALIZAR"); device.findObject(By.text("Temas e skins"))?.click(); Thread.sleep(600)
        obj("theme_RACE_RED"); shot("temas")
        click("theme_RACE_RED"); click("accent_ff2fd27a")
        click("tab_PAINEL", 1_200); shot("tema-race-red-aplicado")
        click("tab_PERSONALIZAR"); device.findObject(By.text("Temas e skins"))?.click(); Thread.sleep(500)
        click("theme_M_PERFORMANCE"); click("accent_default")

        // Settings
        click("tab_CONFIG"); shot("config-conexao")
        click("rail_settings/units"); shot("config-unidades")
        click("rail_settings/alarms"); shot("config-alarmes")
        click("rail_settings/about"); shot("config-sobre")

        // Back navigation returns to the dashboard
        device.pressBack(); Thread.sleep(800)
        obj("tab_PAINEL")
        assertNotNull("back did not return to the dashboard", device.findObject(By.res(java.util.regex.Pattern.compile("dash_.*"))))
        shot("voltar-painel")
    }
}
