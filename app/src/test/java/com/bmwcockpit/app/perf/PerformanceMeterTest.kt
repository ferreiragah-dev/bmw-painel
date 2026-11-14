package com.bmwcockpit.app.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceMeterTest {

    private fun feedConstantAccel(m: PerformanceMeter, accelKmhPerS: Double, dtMs: Long, untilS: Double, startT: Long = 10_000): PerfState {
        var t = startT
        repeat(5) { m.onSample(t, 0f, false, 0); t += dtMs } // standstill
        m.arm()
        repeat(3) { m.onSample(t, 0f, false, 0); t += dtMs }
        var elapsed = 0.0
        var st: PerfState = m.state
        while (elapsed <= untilS) {
            elapsed += dtMs / 1000.0
            st = m.onSample(t + dtMs, (accelKmhPerS * elapsed).toFloat(), true, 0)
            t += dtMs
            if (st is PerfState.Finished || st is PerfState.Aborted) break
        }
        return st
    }

    @Test fun zeroToHundredInterpolated() {
        // 18 km/h/s → 100 km/h at exactly 5.556 s after leaving standstill
        val st = feedConstantAccel(PerformanceMeter(PerfKind.ZERO_100), 18.0, 200, 20.0)
        st as PerfState.Finished
        assertEquals(100.0 / 18.0, st.result.timeS, 0.01)
        assertTrue(st.result.simulated)
    }

    @Test fun quarterMile() {
        // a = 5 m/s² (18 km/h/s): d = a t²/2 → t = sqrt(2·402.336/5) = 12.69 s, v = 63.4 m/s = 228 km/h
        val st = feedConstantAccel(PerformanceMeter(PerfKind.QUARTER_MILE), 18.0, 100, 30.0)
        st as PerfState.Finished
        assertEquals(12.69, st.result.timeS, 0.05)
        assertEquals(228.4, st.result.trapSpeedKmh!!, 1.5)
    }

    @Test fun abortsOnExcessiveSampleGap() {
        val m = PerformanceMeter(PerfKind.ZERO_100, maxGapMs = 700)
        m.onSample(0, 0f, false, 0)
        m.arm()
        m.onSample(200, 0f, false, 0)
        m.onSample(400, 5f, false, 0)
        val st = m.onSample(1_600, 30f, false, 0)
        assertTrue(st is PerfState.Aborted)
        assertTrue((st as PerfState.Aborted).reason.contains("1200"))
    }

    @Test fun abortsOnDisconnection() {
        val m = PerformanceMeter(PerfKind.ZERO_100)
        m.onSample(0, 0f, false, 0); m.arm()
        m.onSample(200, 0f, false, 0)
        m.onSample(400, 10f, false, 0)
        assertTrue(m.onSample(600, null, false, 0) is PerfState.Aborted)
    }

    @Test fun waitsForStandstill() {
        val m = PerformanceMeter(PerfKind.ZERO_100)
        m.onSample(0, 40f, false, 0)
        m.arm()
        assertTrue(m.state is PerfState.WaitingStandstill)
        m.onSample(200, 0.4f, false, 0)
        assertTrue(m.state is PerfState.Armed)
    }

    @Test fun lapTimerUsesSampleTimestamps() {
        val l = LapTimer()
        l.onSample(0, 100f, false)
        l.start()
        var t = 0L
        repeat(60) { t += 1_000; l.onSample(t, 100f, false) }
        val lap = l.markLap()!!
        assertEquals(60.0, lap.timeS, 0.001)
        assertEquals(1666.7, lap.distanceM, 1.0)
        assertEquals(100.0, lap.maxKmh, 0.001)
    }
}
