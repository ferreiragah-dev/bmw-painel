package com.bmwcockpit.app.data

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.BoostDeriver
import com.bmwcockpit.app.data.telemetry.HistoryStore
import com.bmwcockpit.app.data.telemetry.PowerEstimator
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryHubTest {
    private var now = 1_000L
    private val hub = TelemetryHub({ now })

    @Test fun switchingSourceClearsPreviousData() {
        val demo = hub.beginSource(SourceKind.DEMO)
        hub.publish(demo, listOf(Reading.of(SensorId.SPEED, 120f, now, DataOrigin.SIMULATED)))
        assertEquals(120f, hub.snapshot.value.value(SensorId.SPEED))
        assertEquals(1, hub.history.size(SensorId.SPEED))

        val vehicle = hub.beginSource(SourceKind.VEHICLE)
        assertNull("demo value must not survive the switch", hub.snapshot.value.value(SensorId.SPEED))
        assertEquals(0, hub.history.size(SensorId.SPEED))
        assertEquals(SourceKind.VEHICLE, hub.snapshot.value.source)

        // late batch from the old source is rejected
        assertFalse(hub.publish(demo, listOf(Reading.of(SensorId.SPEED, 99f, now, DataOrigin.SIMULATED))))
        assertNull(hub.snapshot.value.value(SensorId.SPEED))
        assertTrue(hub.publish(vehicle, listOf(Reading.of(SensorId.SPEED, 50f, now, DataOrigin.VEHICLE))))
        assertEquals(DataOrigin.VEHICLE, hub.snapshot.value.origin(SensorId.SPEED))
    }

    @Test fun staleReadingsStopLookingCurrent() {
        val t = hub.beginSource(SourceKind.VEHICLE)
        hub.publish(t, listOf(Reading.of(SensorId.RPM, 3000f, now, DataOrigin.VEHICLE)))
        assertEquals(3000f, hub.snapshot.value.value(SensorId.RPM))
        now += SensorId.RPM.staleAfterMs + 1
        hub.refresh()
        assertNull(hub.snapshot.value.value(SensorId.RPM))
        assertTrue(hub.snapshot.value.isStale(SensorId.RPM))
        assertEquals(DataOrigin.UNAVAILABLE, hub.snapshot.value.origin(SensorId.RPM))
    }

    @Test fun unavailableReadingShowsNoValue() {
        val t = hub.beginSource(SourceKind.VEHICLE)
        hub.publish(t, listOf(Reading.unavailable(SensorId.OIL_PRESSURE, now, "sem fonte")))
        assertNull(hub.snapshot.value.value(SensorId.OIL_PRESSURE))
        assertEquals("sem fonte", hub.snapshot.value.reading(SensorId.OIL_PRESSURE)!!.note)
    }

    @Test fun endSourceOnlyEndsMatchingToken() {
        val a = hub.beginSource(SourceKind.DEMO)
        val b = hub.beginSource(SourceKind.VEHICLE)
        hub.endSource(a)
        assertEquals(SourceKind.VEHICLE, hub.activeSource)
        hub.endSource(b)
        assertEquals(SourceKind.NONE, hub.activeSource)
    }

    @Test fun tripIntegratesSpeedAndIgnoresGaps() {
        val t = hub.beginSource(SourceKind.DEMO)
        for (i in 0..36) { // 36 s at 100 km/h = 1 km
            hub.publish(t, listOf(Reading.of(SensorId.SPEED, 100f, now, DataOrigin.SIMULATED)))
            now += 1_000
        }
        assertEquals(1.0f, hub.snapshot.value.reading(SensorId.TRIP)!!.value!!, 0.01f)
        now += 60_000 // a gap must not add distance
        hub.publish(t, listOf(Reading.of(SensorId.SPEED, 100f, now, DataOrigin.SIMULATED)))
        assertEquals(1.0f, hub.snapshot.value.reading(SensorId.TRIP)!!.value!!, 0.01f)
        assertEquals(DataOrigin.SIMULATED, hub.snapshot.value.reading(SensorId.TRIP)!!.origin)
    }

    @Test fun historyIsBounded() {
        val h = HistoryStore(capacity = 50, minIntervalMs = 0)
        repeat(500) { h.addAll(listOf(Reading.of(SensorId.RPM, it.toFloat(), it.toLong(), DataOrigin.SIMULATED))) }
        assertEquals(50, h.size(SensorId.RPM))
        val pts = h.range(SensorId.RPM, 0, Long.MAX_VALUE)
        assertEquals(450f, pts.first().v)
        assertEquals(499f, pts.last().v)
    }

    @Test fun historyThinsByInterval() {
        val h = HistoryStore(capacity = 100, minIntervalMs = 200)
        repeat(100) { h.addAll(listOf(Reading.of(SensorId.RPM, 1f, it * 50L, DataOrigin.SIMULATED))) }
        assertEquals(25, h.size(SensorId.RPM))
    }
}

class EstimatorsTest {
    @Test fun boostNeedsMeasuredBaro() {
        val d = BoostDeriver()
        val r = d.boost(200f, 0)
        assertNull("no fictitious 101.3 kPa", r.value)
        d.onBaro(98f)
        assertEquals(1.02f, d.boost(200f, 0).value!!, 0.001f)
    }

    @Test fun baroCapturedWithEngineOffWhenPid33Missing() {
        val d = BoostDeriver()
        d.onMap(95f, rpm = 3000f, baroPidSupported = false)
        assertNull(d.baroKpa) // engine running: MAP is not ambient pressure
        d.onMap(95f, rpm = 0f, baroPidSupported = false)
        assertEquals(95f, d.baroKpa!!, 0f)
        assertTrue(d.baroFromEngineOff)
        assertEquals(-0.55f, d.boost(40f, 0).value!!, 0.001f)
    }

    @Test fun powerEstimateFormula() {
        // 200 g/s, λ 0.85 → fuel 16.0 g/s → 174.6 kW → 237 cv; torque at 6000 rpm ≈ 278 Nm
        val e = PowerEstimator.estimate(200f, 6000f, 0.85f, 95f)!!
        assertEquals(237.4f, e.powerCv, 1f)
        assertEquals(277.9f, e.torqueNm, 1f)
    }

    @Test fun powerUnavailableWithoutMaf() {
        assertNull(PowerEstimator.estimate(null, 3000f, null, null))
        val rs = PowerEstimator.readings(null, Reading.of(SensorId.RPM, 3000f, 0, DataOrigin.VEHICLE), null, null, 0)
        assertTrue(rs.all { it.value == null && it.origin == DataOrigin.UNAVAILABLE })
    }
}
