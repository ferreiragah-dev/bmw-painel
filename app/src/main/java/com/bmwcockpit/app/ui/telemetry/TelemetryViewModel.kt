package com.bmwcockpit.app.ui.telemetry

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bmwcockpit.app.CockpitApp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.telemetry.HistoryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Series(val sensor: SensorId, val points: List<HistoryPoint>)

data class ChartFrame(
    val startMs: Long = 0,
    val endMs: Long = 1,
    val series: List<Series> = emptyList(),
    val paused: Boolean = false,
    val windowMs: Long = 120_000,
    /** How far back from "live" the paused view is panned. */
    val panMs: Long = 0,
    val oldestMs: Long? = null,
)

/**
 * Telemetry charts. Pausing freezes only the VIEW: collection, the in-memory history and any session
 * recording keep running. While paused the user can pan back through the history (up to the retained
 * 10 minutes); "Retomar" jumps back to live.
 */
class TelemetryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CockpitApp).container
    private val _frame = MutableStateFlow(ChartFrame())
    val frame: StateFlow<ChartFrame> = _frame.asStateFlow()

    private var pausedAt: Long? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                rebuild()
                delay(if (_frame.value.paused) 500 else 200)
            }
        }
    }

    private fun rebuild() {
        val f = _frame.value
        val now = container.clock.nowMs()
        val end = (pausedAt ?: now) - f.panMs
        val start = end - f.windowMs
        val sensors = container.settingsState.value.chartSensors
        val h = container.hub.history
        _frame.value = f.copy(
            startMs = start, endMs = end,
            series = sensors.map { Series(it, h.range(it, start, end)) },
            oldestMs = h.oldestTimestamp(),
        )
    }

    fun setWindow(ms: Long) { _frame.update { it.copy(windowMs = ms) }; rebuild() }

    fun pause() {
        pausedAt = container.clock.nowMs()
        _frame.update { it.copy(paused = true, panMs = 0) }
        rebuild()
    }

    fun resume() {
        pausedAt = null
        _frame.update { it.copy(paused = false, panMs = 0) }
        rebuild()
    }

    /** Pans the paused view; positive [deltaMs] goes back in time. */
    fun pan(deltaMs: Long) {
        val f = _frame.value
        if (!f.paused) return
        val paused = pausedAt ?: return
        val maxPan = ((f.oldestMs?.let { paused - it } ?: 0L) - f.windowMs / 2).coerceAtLeast(0L)
        _frame.update { it.copy(panMs = (it.panMs + deltaMs).coerceIn(0L, maxPan)) }
        rebuild()
    }

    fun toggleSensor(s: SensorId) {
        val cur = container.settingsState.value.chartSensors
        val next = if (s in cur) cur - s else (cur + s).takeLast(6)
        if (next.isEmpty()) return
        viewModelScope.launch { container.settings.update { it.copy(chartSensors = next) } }
    }

    // ---- sessions / CSV
    val recording = container.recorder.state
    val sessions = container.recorder.sessions

    fun exportSession(id: String, uri: Uri) = export(uri) { out -> container.recorder.exportSession(id, out) }

    fun exportHistory(uri: Uri) = export(uri) { out ->
        container.recorder.exportHistory(container.hub.history, container.settingsState.value.chartSensors, out)
    }

    private fun export(uri: Uri, block: suspend (java.io.OutputStream) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("Destino indisponível")
                    out.use { block(it) }
                }
                container.notify("CSV exportado")
            } catch (e: Exception) {
                container.notify("Falha ao exportar CSV: ${e.message}")
            }
        }
    }

    fun deleteSession(id: String) = container.recorder.delete(id)
}
