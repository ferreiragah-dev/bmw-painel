package com.bmwcockpit.app.perf

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import com.bmwcockpit.app.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PerfUiState(
    val zero100: PerfState = PerfState.Idle,
    val quarter: PerfState = PerfState.Idle,
    val lapRunning: Boolean = false,
    val laps: List<Lap> = emptyList(),
    val currentLapS: Double = 0.0,
    val currentLapDistanceM: Double = 0.0,
)

/** Feeds the performance meters from the central telemetry hub and stores finished results. */
class PerformanceController(
    private val hub: TelemetryHub,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val zero = PerformanceMeter(PerfKind.ZERO_100)
    private val quarter = PerformanceMeter(PerfKind.QUARTER_MILE)
    private val laps = LapTimer()
    private val lock = Any()
    private val _state = MutableStateFlow(PerfUiState())
    val state: StateFlow<PerfUiState> = _state.asStateFlow()

    init {
        scope.launch {
            hub.samples.collect { batch ->
                val speed = batch.readings.firstOrNull { it.sensor == SensorId.SPEED } ?: return@collect
                feed(speed.timestampMs, speed.value, speed.origin == DataOrigin.SIMULATED)
            }
        }
        scope.launch {
            hub.snapshot.collect { snap ->
                // Disconnection / stale speed: abort running measurements instead of waiting forever.
                if (!snap.isFresh(SensorId.SPEED)) feed(snap.nowMs, null, false)
            }
        }
    }

    private fun feed(t: Long, v: Float?, sim: Boolean) {
        val finished = mutableListOf<PerfResult>()
        synchronized(lock) {
            for (m in listOf(zero, quarter)) {
                val before = m.state
                val after = m.onSample(t, v, sim, wallClock())
                if (after is PerfState.Finished && before !is PerfState.Finished) finished += after.result
            }
            laps.onSample(t, v, sim)
            publishLocked()
        }
        finished.forEach { r -> scope.launch { settings.addPerfResult(r) } }
    }

    private fun publishLocked() {
        _state.value = PerfUiState(zero.state, quarter.state, laps.running, laps.laps, laps.currentLapS, laps.currentLapDistanceM)
    }

    fun arm(kind: PerfKind) = synchronized(lock) { meter(kind).arm(); publishLocked() }
    fun cancel(kind: PerfKind) = synchronized(lock) { meter(kind).cancel(); publishLocked() }
    fun startLaps() = synchronized(lock) { laps.start(); publishLocked() }
    fun markLap() = synchronized(lock) { laps.markLap(); publishLocked() }
    fun stopLaps() = synchronized(lock) { laps.stop(); publishLocked() }
    fun resetLaps() = synchronized(lock) { laps.reset(); publishLocked() }

    private fun meter(kind: PerfKind) = if (kind == PerfKind.ZERO_100) zero else quarter
}
