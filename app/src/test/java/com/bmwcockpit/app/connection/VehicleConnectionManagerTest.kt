package com.bmwcockpit.app.connection

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import com.bmwcockpit.app.obd.DtcResult
import com.bmwcockpit.app.obd.RecordedF30
import com.bmwcockpit.app.transport.DeviceRef
import com.bmwcockpit.app.transport.FakeTransport
import com.bmwcockpit.app.transport.LinkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleConnectionManagerTest {
    private val device = DeviceRef("Vgate iCar", "00:11:22:33:44:55", LinkType.CLASSIC)
    private val clock = { System.nanoTime() / 1_000_000 }

    private suspend fun waitUntil(ms: Long = 10_000, cond: () -> Boolean) = withTimeout(ms) { while (!cond()) delay(20) }

    @Test fun connectPollReadDtcAndDisconnectCleanly() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = TelemetryHub(clock)
        val transports = mutableListOf<FakeTransport>()
        val mgr = VehicleConnectionManager(hub, clock, scope, {
            FakeTransport(latencyMs = 2, responder = FakeTransport.fromMap(RecordedF30.responses)).also { transports += it }
        })
        mgr.connect(device)
        waitUntil { mgr.state.value is ConnectionState.Connected }
        assertEquals(SourceKind.VEHICLE, hub.activeSource)
        waitUntil { hub.snapshot.value.value(SensorId.RPM) != null }
        assertEquals(1726f, hub.snapshot.value.value(SensorId.RPM)!!, 0.01f)
        assertEquals(DataOrigin.VEHICLE, hub.snapshot.value.origin(SensorId.RPM))

        val report = mgr.readDtcs()
        assertEquals(listOf("P0302", "P0171"), (report.stored as DtcResult.Codes).codes.map { it.code })
        assertEquals(DtcResult.NoCodes, report.pending)
        assertTrue("0A NO DATA must not become 'no codes'", report.permanent is DtcResult.Unsupported)
        assertEquals(true, report.milOn)
        assertNull(report.warning)
        assertTrue(transports.single().sent.none { it == "04" })

        mgr.disconnect()
        waitUntil { mgr.state.value == ConnectionState.Idle }
        assertTrue("socket closed", transports.single().closed)
        assertEquals(SourceKind.NONE, hub.activeSource)
        assertNull(hub.snapshot.value.value(SensorId.RPM))
        scope.cancel()
    }

    @Test fun initialFailureReportsReadableError() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = TelemetryHub(clock)
        val mgr = VehicleConnectionManager(hub, clock, scope, { FakeTransport(failOpen = "Não foi possível abrir a porta serial") { null } })
        mgr.connect(device)
        waitUntil { mgr.state.value is ConnectionState.Failed }
        assertTrue((mgr.state.value as ConnectionState.Failed).message.contains("porta serial"))
        scope.cancel()
    }

    @Test fun reconnectsAfterLinkDrop() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = TelemetryHub(clock)
        val transports = mutableListOf<FakeTransport>()
        val mgr = VehicleConnectionManager(hub, clock, scope, {
            FakeTransport(latencyMs = 2, responder = FakeTransport.fromMap(RecordedF30.responses)).also { transports += it }
        })
        mgr.autoReconnect = true
        mgr.connect(device)
        waitUntil { mgr.state.value is ConnectionState.Connected }
        transports.last().drop()
        waitUntil { mgr.state.value is ConnectionState.Reconnecting }
        waitUntil(15_000) { mgr.state.value is ConnectionState.Connected && transports.size == 2 }
        assertTrue(transports.first().closed)
        mgr.disconnect()
        waitUntil { mgr.state.value == ConnectionState.Idle }
        scope.cancel()
    }
}
