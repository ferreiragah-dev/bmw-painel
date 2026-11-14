package com.bmwcockpit.app.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.bmwcockpit.app.data.model.SensorId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test fun persistsAcrossInstances() = runBlocking {
        val file = File(tmp.root, "s.preferences_pb")
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo1 = SettingsRepository(store(file, scope1))
        repo1.update {
            it.copy(
                layout = DashLayout.RACE, theme = ThemeId.BLUE_ICE, accent = 0xFF8A4BFFL,
                cardSensors = listOf(SensorId.SPEED, SensorId.RPM, SensorId.BOOST, SensorId.IAT, SensorId.THROTTLE, SensorId.ENGINE_LOAD),
                customLayout = EditorLayout("Pista", listOf(EditorWidget("a", SensorId.RPM, 0.1f, 0.1f, 0.3f, 0.3f, WidgetStyle.BAR, EditorDefaults.RED))),
            )
        }
        scope1.cancel()
        // "Process restart": a new DataStore on the same file
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val s = SettingsRepository(store(file, scope2)).settings.first()
        assertEquals(DashLayout.RACE, s.layout)
        assertEquals(ThemeId.BLUE_ICE, s.theme)
        assertEquals(0xFF8A4BFFL, s.accent)
        assertEquals(SensorId.ENGINE_LOAD, s.cardSensors[5])
        assertEquals("Pista", s.customLayout.name)
        assertEquals(WidgetStyle.BAR, s.customLayout.widgets.single().style)
        scope2.cancel()
    }

    @Test fun corruptedJsonFallsBackToDefaults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = store(File(tmp.root, "c.preferences_pb"), scope)
        ds.edit { it[SettingsRepository.KEY_SETTINGS] = "{ not json" }
        assertEquals(AppSettings(), SettingsRepository(ds).settings.first())
        scope.cancel()
    }

    @Test fun invalidValuesAreSanitized() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = store(File(tmp.root, "i.preferences_pb"), scope)
        ds.edit {
            it[SettingsRepository.KEY_SETTINGS] = """{"layout":"WARP_DRIVE","theme":"AMBER","cardSensors":["RPM"],
                "demoPace":99.0,"customLayout":{"name":"x","widgets":[{"id":"a","sensor":"RPM","x":5.0,"y":-3.0,"w":2.0,"h":0.01,"style":"BAR","color":0}]}}"""
        }
        val s = SettingsRepository(ds).settings.first()
        assertEquals(DashLayout.M_PERFORMANCE, s.layout) // unknown enum → default
        assertEquals(ThemeId.AMBER, s.theme)
        assertEquals(6, s.cardSensors.size)
        assertEquals(SensorId.RPM, s.cardSensors[0])
        assertEquals(4f, s.demoPace, 0f)
        val w = s.customLayout.widgets.single()
        assertTrue(w.x + w.w <= 1f && w.y >= 0f && w.h >= EditorDefaults.MIN_SIZE)
        scope.cancel()
    }

    @Test fun importRejectsGarbageAndRoundTrips() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = SettingsRepository(store(File(tmp.root, "b.preferences_pb"), scope))
        repo.update { it.copy(theme = ThemeId.RACE_RED, layout = DashLayout.MINIMAL) }
        val json = repo.exportJson()
        try { repo.importJson("<html>"); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("inválido")) }
        repo.update { AppSettings() }
        repo.importJson(json)
        val s = repo.current()
        assertEquals(ThemeId.RACE_RED, s.theme)
        assertEquals(DashLayout.MINIMAL, s.layout)
        scope.cancel()
    }
}
