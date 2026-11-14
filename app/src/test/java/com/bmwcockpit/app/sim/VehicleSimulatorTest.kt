package com.bmwcockpit.app.sim

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.SensorId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VehicleSimulatorTest {

    @Test fun everyReadingIsSimulated() {
        val s = VehicleSimulator()
        s.step(5.0)
        assertTrue(s.readings(0).all { it.origin == DataOrigin.SIMULATED })
    }

    @Test fun launchFromStandstillIsPlausible() {
        val s = VehicleSimulator()
        s.launchFromStandstill(200.0)
        var t = 0.0
        var t100 = -1.0
        var started = -1.0
        var maxKmh = 0.0
        while (t < 20) {
            s.step(0.05); t += 0.05
            if (started < 0 && s.speedKmh > 0.5) started = t
            if (t100 < 0 && s.speedKmh >= 100) t100 = t - started
            maxKmh = maxOf(maxKmh, s.speedKmh)
        }
        // Stage 2 320i with 8HP: roughly 5–7.5 s to 100 km/h
        assertTrue("0-100 = $t100", t100 in 4.5..7.5)
        assertTrue("max $maxKmh km/h in 20 s", maxKmh > 170)
    }

    @Test fun rpmMatchesGearAndSpeedWhenLocked() {
        val s = VehicleSimulator(7)
        var checked = 0
        repeat(6000) {
            s.step(0.05)
            if (s.isConverterLocked()) {
                val implied = s.impliedSpeedKmh()
                assertTrue("speed ${s.speedKmh} vs implied $implied", abs(implied - s.speedKmh) / s.speedKmh < 0.10)
                checked++
            }
        }
        assertTrue(checked > 500)
    }

    @Test fun noIncoherentJumps() {
        val s = VehicleSimulator(11)
        var prev = s.readings(0).associate { it.sensor to it.value!! }
        repeat(4000) { i ->
            s.step(0.05)
            val cur = s.readings(i.toLong()).filter { it.value != null }.associate { it.sensor to it.value!! }
            assertTrue(abs(cur[SensorId.SPEED]!! - prev[SensorId.SPEED]!!) < 2.5f) // < 2.5 km/h per 50 ms
            assertTrue(abs(cur[SensorId.COOLANT_TEMP]!! - prev[SensorId.COOLANT_TEMP]!!) < 0.5f)
            assertTrue(cur[SensorId.RPM]!! in 600f..7100f)
            assertTrue(cur[SensorId.GEAR]!! in 1f..8f)
            assertTrue(cur[SensorId.SPEED]!! >= 0f)
            prev = cur
        }
    }

    @Test fun resetReturnsToInitialState() {
        val s = VehicleSimulator()
        s.step(60.0)
        s.reset()
        assertEquals(0.0, s.speedKmh, 0.0)
        assertEquals(1, s.gear)
    }
}
