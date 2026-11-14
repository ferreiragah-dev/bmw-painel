package com.bmwcockpit.app.obd

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.transport.FakeTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Recorded responses of an ELM327 v1.5 clone on a CAN 11/500 car with ECM (7E8) and TCM (7E9). */
object RecordedF30 {
    val responses = mapOf(
        "ATZ" to "\r\rELM327 v1.5",
        "ATE0" to "ATE0\rOK", // echo still on when ATE0 is answered
        "ATL0" to "OK",
        "ATS0" to "OK",
        "ATH1" to "OK",
        "ATAT1" to "OK",
        "ATSP0" to "OK",
        "0100" to "SEARCHING...\r7E8064100BE3FA813\r7E906410098188011",
        "ATDPN" to "A6",
        "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)",
        "ATSH7E0" to "OK",
        "ATCRA7E8" to "OK",
        "0120" to "7E8064120A007B011",
        "0140" to "7E8064140FED08400",
        "0160" to "7E806416000000001",
        "010C" to "7E804410C1AF8",
        "010D" to "7E803410D32",
        "010B" to "7E803410BC8",
        "0111" to "7E8034111" + "40",
        "0110" to "7E80441104876",
        "0104" to "7E803410480",
        "0105" to "7E803410582",
        "010F" to "7E803410F46",
        "0142" to "7E80441423778",
        "0133" to "7E803413365",
        "0144" to "7E80441448000",
        "015C" to "NO DATA",
        "ATRV" to "14.1V",
        "0101" to "7E8064101820007E500",
        "03" to "7E806430203020171",
        "07" to "7E8024700",
        "0A" to "NO DATA",
    )
}

class Elm327SessionTest {

    @Test fun sessionStripsEchoSearchingAndPrompt() = runBlocking {
        val t = FakeTransport(responder = FakeTransport.fromMap(mapOf("0100" to "SEARCHING...\r7E8064100BE3FA813"), echo = true))
        val s = ElmSession(t)
        val r = s.send("0100")
        assertEquals(ElmStatus.OK, r.status)
        assertEquals(listOf("7E8064100BE3FA813"), r.lines)
    }

    @Test fun partialChunksAreJoinedUntilPrompt() = runBlocking {
        val t = FakeTransport(chunkSize = 3, responder = FakeTransport.fromMap(mapOf("010C" to "7E804410C1AF8")))
        val r = ElmSession(t).send("010C")
        assertEquals(listOf("7E804410C1AF8"), r.lines)
    }

    @Test fun noDataAndUnknown() = runBlocking {
        val t = FakeTransport(responder = FakeTransport.fromMap(mapOf("015C" to "NO DATA")))
        val s = ElmSession(t)
        assertEquals(ElmStatus.NO_DATA, s.send("015C").status)
        assertEquals(ElmStatus.UNKNOWN_COMMAND, s.send("ATXYZ").status)
    }

    @Test fun silentAdapterTimesOut() = runBlocking {
        val t = FakeTransport { null }
        val r = ElmSession(t).send("010C", 100)
        assertEquals(ElmStatus.TIMEOUT, r.status)
    }

    @Test fun droppedLinkThrows() = runBlocking {
        val t = FakeTransport { null }
        val s = ElmSession(t)
        t.drop()
        try {
            s.send("010C", 500)
            fail("expected exception")
        } catch (e: Exception) {
            assertTrue(e is LinkLostException || e is com.bmwcockpit.app.transport.TransportException)
        }
    }

    @Test fun initializerSelectsEngineEcuAndDiscoversPids() = runBlocking {
        val t = FakeTransport(responder = FakeTransport.fromMap(RecordedF30.responses))
        val info = Elm327Initializer(ElmSession(t)).initialize()
        assertEquals(ProtocolFamily.CAN_11, info.family)
        assertEquals("7E8", info.ecu)
        assertEquals(listOf("7E8", "7E9"), info.ecusSeen)
        assertTrue(info.headerFilterApplied)
        assertTrue(0x0C in info.supportedPids && 0x0D in info.supportedPids)
        assertTrue("0x42 module voltage discovered via 0140", 0x42 in info.supportedPids)
        assertTrue("ATSH7E0" in t.sent)
        // read-only: no service 04 (clear DTC) was ever sent
        assertFalse(t.sent.any { it == "04" })
    }

    @Test fun initializerReportsIgnitionOff() = runBlocking {
        val map = RecordedF30.responses + ("0100" to "SEARCHING...\rUNABLE TO CONNECT")
        val t = FakeTransport(responder = FakeTransport.fromMap(map))
        try {
            Elm327Initializer(ElmSession(t)).initialize()
            fail()
        } catch (e: ObdInitException) {
            assertTrue(e.message!!.contains("ignição", ignoreCase = true))
        }
    }

    @Test fun pollerQueriesOnlySupportedPidsAndDerivesBoost() = runBlocking {
        val t = FakeTransport(responder = FakeTransport.fromMap(RecordedF30.responses))
        val s = ElmSession(t)
        val info = Elm327Initializer(s).initialize()
        val published = mutableListOf<Reading>()
        var now = 0L
        val clock = Clock { now }
        val poller = ObdPoller(s, info, clock, { false }) { batch ->
            published += batch
            now += 1_000
            if (now > 40_000) throw CancellationException("done")
        }
        try { withTimeout(5_000) { poller.run() } } catch (_: CancellationException) {}
        val bySensor = published.groupBy { it.sensor }
        assertEquals(1726f, bySensor[SensorId.RPM]!!.last().value!!, 0.01f)
        assertEquals(50f, bySensor[SensorId.SPEED]!!.last().value!!, 0f)
        // MAP 200 kPa, BARO 101 kPa (0x65) → 0.99 bar
        val boost = bySensor[SensorId.BOOST]!!.last { it.value != null }
        assertEquals(0.99f, boost.value!!, 0.001f)
        assertEquals(DataOrigin.DERIVED, boost.origin)
        // unsupported sensors are explicitly unavailable, never invented
        assertNull(bySensor[SensorId.GEAR]!!.single().value)
        assertNull(bySensor[SensorId.OIL_PRESSURE]!!.single().value)
        assertTrue(t.sent.none { it == "0146" }) // ambient temp never requested (not used / not polled)
        val power = bySensor[SensorId.POWER_EST]!!.last()
        assertEquals(DataOrigin.DERIVED, power.origin)
        assertNotNull(power.value)
        assertEquals(14.1f, bySensor[SensorId.ADAPTER_VOLTAGE]!!.last().value!!, 0.001f)
        assertEquals(14.2f, bySensor[SensorId.MODULE_VOLTAGE]!!.last().value!!, 0.001f)
    }

    @Test fun pollerIgnoresAnswersFromOtherEcu() = runBlocking {
        // After init, a misbehaving adapter returns the TCM's answer for speed: it must not be used.
        val map = RecordedF30.responses + ("010D" to "7E903410D99")
        val t = FakeTransport(responder = FakeTransport.fromMap(map))
        val s = ElmSession(t)
        val info = Elm327Initializer(s).initialize()
        val published = mutableListOf<Reading>()
        var now = 0L
        val poller = ObdPoller(s, info, { now }, { false }) { batch ->
            published += batch; now += 500
            if (now > 5_000) throw CancellationException("done")
        }
        try { withTimeout(5_000) { poller.run() } } catch (_: CancellationException) {}
        assertTrue(published.none { it.sensor == SensorId.SPEED && it.value == 0x99.toFloat() })
    }

    @Test fun pollerDetectsLostAdapter() = runBlocking {
        var dead = false
        val base = FakeTransport.fromMap(RecordedF30.responses)
        val t = FakeTransport { cmd -> if (dead) null else base(cmd) }
        val s = ElmSession(t)
        val info = Elm327Initializer(s).initialize()
        dead = true
        var now = 0L
        val poller = ObdPoller(s, info, { now += 10; now }, { false }) { }
        try {
            withTimeout(30_000) { poller.run() }
            fail("expected ConnectionLostException")
        } catch (e: ConnectionLostException) {
            assertTrue(e.message!!.contains("TIMEOUT"))
        }
    }
}
