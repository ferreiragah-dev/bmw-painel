package com.bmwcockpit.app.session

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class SessionRecorderTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun waitUntil(cond: () -> Boolean) = withTimeout(5_000) { while (!cond()) delay(10) }

    @Test fun recordsCsvWithOriginAndUnits() = runBlocking {
        var now = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = TelemetryHub({ now })
        val token = hub.beginSource(SourceKind.DEMO)
        val rec = SessionRecorder(tmp.root, hub, scope, { now }, { 1_700_000_000_000L + now }, Dispatchers.IO)
        rec.start()
        waitUntil { rec.state.value.recording }
        delay(50)
        repeat(10) {
            now += 100
            hub.publish(token, listOf(Reading.of(SensorId.SPEED, it * 10f, now, DataOrigin.SIMULATED), Reading.of(SensorId.BOOST, 0.5f, now, DataOrigin.SIMULATED)))
            delay(5)
        }
        waitUntil { rec.state.value.samples >= 20 }
        rec.stop()
        waitUntil { rec.sessions.value.isNotEmpty() }
        val meta = rec.sessions.value.single()
        assertEquals(SourceKind.DEMO, meta.source)
        val out = ByteArrayOutputStream()
        rec.exportSession(meta.id, out)
        val lines = out.toString(Charsets.UTF_8.name()).trim().lines()
        assertEquals(CsvFormat.HEADER, lines.first())
        assertTrue(lines.any { it.contains(",SPEED,90.00,km/h,SIMULATED") })
        assertTrue(lines.any { it.contains(",BOOST,0.50,bar,SIMULATED") })
        assertTrue(lines[1].startsWith("2023-11-14T22:13:20.100Z"))
        scope.cancel()
    }

    @Test fun recoversInterruptedSessionWithoutCorruption() = runBlocking {
        File(tmp.root, "sessao_x.csv.part").writeText(CsvFormat.HEADER + "\n2024-01-01T00:00:00.000Z,0,RPM,800.00,rpm,VEHICLE\n2024-01-01T00:00:00.100Z,100,RP")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rec = SessionRecorder(tmp.root, TelemetryHub({ 0 }), scope, { 0 }, { 0 }, Dispatchers.IO)
        waitUntil { rec.sessions.value.isNotEmpty() }
        val m = rec.sessions.value.single()
        assertTrue(m.interrupted)
        assertEquals(1L, m.samples)
        val text = File(tmp.root, "sessao_x.csv").readText()
        assertTrue(text.endsWith("VEHICLE\n"))
        assertTrue(!File(tmp.root, "sessao_x.csv.part").exists())
        scope.cancel()
    }

    @Test fun sourceChangeEndsSession() = runBlocking {
        var now = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = TelemetryHub({ now })
        val t = hub.beginSource(SourceKind.DEMO)
        val rec = SessionRecorder(tmp.root, hub, scope, { now }, { now }, Dispatchers.IO)
        rec.start()
        waitUntil { rec.state.value.recording }
        hub.publish(t, listOf(Reading.of(SensorId.RPM, 900f, 1, DataOrigin.SIMULATED)))
        hub.beginSource(SourceKind.VEHICLE)
        rec.onSourceChanged()
        waitUntil { !rec.state.value.recording && rec.sessions.value.isNotEmpty() }
        assertEquals("Fonte de dados alterada", rec.sessions.value.single().endReason)
        scope.cancel()
    }
}
