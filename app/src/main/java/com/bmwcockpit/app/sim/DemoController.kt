package com.bmwcockpit.app.sim

import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs the [VehicleSimulator] in real time and publishes it to the [TelemetryHub] as the DEMO source. */
class DemoController(
    private val hub: TelemetryHub,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    data class State(
        val active: Boolean = false,
        val running: Boolean = true,
        val pace: Float = 1f,
        val backgroundPaused: Boolean = false,
    )

    val sim = VehicleSimulator()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var token = -1L

    @Synchronized
    fun start(pace: Float = _state.value.pace) {
        if (job?.isActive == true) return
        token = hub.beginSource(SourceKind.DEMO)
        _state.update { it.copy(active = true, pace = pace) }
        val myToken = token
        job = scope.launch(dispatcher) {
            var last = clock.nowMs()
            while (isActive && hub.isCurrent(myToken)) {
                delay(PUBLISH_INTERVAL_MS)
                val now = clock.nowMs()
                val dt = ((now - last).coerceIn(0, 250)) / 1000.0
                last = now
                val st = _state.value
                val readings = synchronized(sim) {
                    if (st.running && !st.backgroundPaused) sim.step(dt * st.pace)
                    sim.readings(now)
                }
                hub.publish(myToken, readings)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        if (token >= 0) hub.endSource(token)
        token = -1
        _state.update { it.copy(active = false) }
    }

    fun setRunning(running: Boolean) = _state.update { it.copy(running = running) }
    fun setPace(pace: Float) = _state.update { it.copy(pace = pace.coerceIn(0.25f, 4f)) }
    fun setBackgroundPaused(paused: Boolean) = _state.update { it.copy(backgroundPaused = paused) }

    fun restart() {
        synchronized(sim) { sim.reset() }
        setRunning(true)
    }

    fun launchFromStandstill() {
        synchronized(sim) { sim.launchFromStandstill() }
        setRunning(true)
    }

    companion object {
        const val PUBLISH_INTERVAL_MS = 50L
    }
}
