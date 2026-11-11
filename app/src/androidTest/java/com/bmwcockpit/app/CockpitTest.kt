package com.bmwcockpit.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlinx.coroutines.runBlocking

class CockpitTest {
    @get:Rule val app=createAndroidComposeRule<MainActivity>()
    private val vm get()=app.activity.vm
    private fun click(tag:String){app.onNodeWithTag(tag).performClick();app.waitForIdle()}
    private fun tap(s:String){app.onNodeWithText(s).performClick();app.waitForIdle()}
    private fun snap(name:String){
        app.waitForIdle();SystemClock.sleep(220)
        val ins=InstrumentationRegistry.getInstrumentation()
        val bitmap=ins.uiAutomation.takeScreenshot()
        val dir=File(ins.targetContext.filesDir,"review").apply{mkdirs()}
        File(dir,name+".png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    private fun orientation(portrait:Boolean){
        app.runOnUiThread{app.activity.requestedOrientation=if(portrait)ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        app.waitUntil(12000){val m=app.activity.resources.displayMetrics;(m.heightPixels>m.widthPixels)==portrait}
        app.waitUntil(12000){app.onAllNodesWithTag("tab_dash").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun setSize(width:Int,height:Int) {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("wm size ${height}x${width}")).use{it.readBytes()}
        app.waitUntil(12000){app.activity.resources.displayMetrics.widthPixels==width}
    }
    @Test fun exercise(){
        app.waitUntil(10000){vm.ready.value && vm.frame.value.time>0}
        orientation(false)
        app.runOnIdle{vm.playing.value=false}
        snap("01-m-performance")
        val v=vm.frame.value[Sensor.RPM].value
        app.runOnIdle{vm.playing.value=true}
        app.waitUntil(5000){vm.frame.value[Sensor.RPM].value!=v}
        app.waitUntil(5000){vm.frame.value[Sensor.SPEED].value!!>119}
        app.runOnIdle{vm.playing.value=false};snap("01b-moving")
        click("choose_layout");snap("04-layouts");click("pick_RACE");snap("02-race")
        click("choose_layout");click("pick_GT");snap("10-gt");click("choose_layout");click("pick_MIN");snap("11-minimal")
        click("tab_telemetry");snap("03-telemetry");tap("Pausar");tap("Retomar");tap("Gravar sessão")
        app.runOnIdle{vm.playing.value=true};app.waitUntil(5000){vm.frame.value.time>0};SystemClock.sleep(1100)
        tap("Finalizar sessão");tap("Sessões");app.waitUntil(6000){vm.files.value.any{it.count>0}};snap("12-sessions")
        tap("0–100 km/h");app.runOnIdle{vm.speedFactor.value=3f};tap("Iniciar");tap("Partir do zero")
        app.waitUntil(15000){vm.timing.value.results.isNotEmpty()};snap("07-performance")
        app.runOnIdle{vm.speedFactor.value=1f}
        tap("Diagnóstico");tap("Ler códigos");app.waitUntil(3000){vm.logs.value.any{it.demo}};snap("06-diagnostics")
        click("tab_editor");snap("05-editor");click("add_RPM")
        app.onNodeWithTag("widget_2").performTouchInput{swipe(center,center+androidx.compose.ui.geometry.Offset(58f,-25f),250)}
        click("save_layout");app.waitUntil(3000){vm.options.value.widgets.size==2 && vm.options.value.overlay}
        click("tab_editor");tap("Temas e skins");snap("08-themes");click("skin_AMBER")
        click("tab_settings");snap("09-settings");click("mode_live");app.waitUntil(4000){!vm.options.value.demo && vm.frame.value.values.isEmpty()}
        click("mode_demo");click("tab_dash");click("choose_layout");click("pick_M")
        orientation(true);snap("13-portrait-m")
        click("choose_layout");snap("14-portrait-layouts");click("pick_RACE");snap("15-portrait-race")
        click("tab_telemetry");snap("16-portrait-telemetry");click("tab_editor");snap("17-portrait-editor")
        tap("Temas e skins");snap("18-portrait-themes");click("tab_settings");snap("19-portrait-settings")
        orientation(false);click("tab_dash");click("choose_layout");click("pick_M")
        setSize(800,480)
        app.onNodeWithText("Turbo").assertIsDisplayed()
        app.onNodeWithText("IAT").assertIsDisplayed()
        snap("20-800x480")
        setSize(1920,720);snap("21-ultrawide");setSize(1280,720)
        click("choose_layout");click("pick_RACE")
        app.waitUntil(5000){runBlocking{Prefs(app.activity).get()}.layout==Layout.RACE}
    }
}
class RestartCheck{
    @get:Rule val app=createAndroidComposeRule<MainActivity>()
    @Test fun persisted(){
        app.waitUntil(10000){app.activity.vm.ready.value}
        val o=app.activity.vm.options.value
        assertEquals(Skin.AMBER,o.skin);assertEquals(Layout.RACE,o.layout);assertEquals(2,o.widgets.size);assertEquals(2,o.saved.getValue("Meu painel").size)
        assertTrue(o.widgets.any{it.sensor==Sensor.RPM && it.x!=.23f})
        val sessions=Sessions(app.activity)
        val session=sessions.list().first()
        val csv=java.io.ByteArrayOutputStream().also{sessions.export(session.file,it)}.toString("UTF-8").lines()
        assertTrue(csv.first().contains("SPEED_km/h;SPEED_source"))
        assertTrue(csv[1].contains(";DEMO;"))
        assertEquals(csv.first().count{it==';'},csv[1].count{it==';'})
    }
}
