package com.bmwcockpit.app.data.telemetry

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.model.TelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface Clock {
    fun nowMs(): Long
}

/** A batch of readings published by the active source. */
data class SampleBatch(val source: SourceKind, val readings: List<Reading>)

/**
 * Single origin of telemetry for the whole application. Every dashboard, chart, recorder and performance
 * meter observes this hub, so all of them always show the same data.
 *
 * Sources obtain a token with [beginSource]; publishing with an old token is ignored. Switching source
 * clears every reading and the in-memory history, so values of the previous origin never leak into the new one.
 */
class TelemetryHub(
    private val clock: Clock,
    scope: CoroutineScope? = null,
    private val tickMs: Long = 250,
    historyCapacity: Int = HistoryStore.DEFAULT_CAPACITY,
) {
    val history = HistoryStore(historyCapacity)

    private val lock = Any()
    private var token = 0L
    private var source = SourceKind.NONE
    private val readings = HashMap<SensorId, Reading>()

    // Trip distance integrated from speed samples of the active source.
    private var tripKm = 0.0
    private var lastSpeedTs = -1L
    private var lastSpeedKmh = 0f

    private val _snapshot = MutableStateFlow(TelemetrySnapshot.EMPTY)
    val snapshot: StateFlow<TelemetrySnapshot> = _snapshot.asStateFlow()

    private val _samples = MutableSharedFlow<SampleBatch>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val samples: SharedFlow<SampleBatch> = _samples.asSharedFlow()

    init {
        scope?.launch {
            while (isActive) {
                delay(tickMs)
                refresh()
            }
        }
    }

    val activeSource: SourceKind get() = synchronized(lock) { source }

    /** Starts a new source session, discarding every value from the previous one. Returns the publish token. */
    fun beginSource(kind: SourceKind): Long = synchronized(lock) {
        token += 1
        source = kind
        readings.clear()
        history.clear()
        tripKm = 0.0
        lastSpeedTs = -1L
        lastSpeedKmh = 0f
        emitLocked()
        token
    }

    /** Ends the session identified by [sourceToken] (no-op if another source already took over). */
    fun endSource(sourceToken: Long) {
        synchronized(lock) {
            if (sourceToken != token) return
        }
        beginSource(SourceKind.NONE)
    }

    fun isCurrent(sourceToken: Long): Boolean = synchronized(lock) { sourceToken == token }

    /** Publishes readings. Returns false (and drops them) when [sourceToken] is not the active token. */
    fun publish(sourceToken: Long, batch: List<Reading>): Boolean {
        val out: SampleBatch
        synchronized(lock) {
            if (sourceToken != token || source == SourceKind.NONE) return false
            val enriched = ArrayList<Reading>(batch.size + 1)
            for (r in batch) {
                readings[r.sensor] = r
                enriched += r
                if (r.sensor == SensorId.SPEED) integrateTrip(r)?.let { trip ->
                    readings[SensorId.TRIP] = trip
                    enriched += trip
                }
            }
            history.addAll(enriched)
            emitLocked()
            out = SampleBatch(source, enriched)
        }
        _samples.tryEmit(out)
        return true
    }

    private fun integrateTrip(r: Reading): Reading? {
        val v = r.value ?: return null
        if (lastSpeedTs >= 0 && r.timestampMs > lastSpeedTs) {
            val dtS = (r.timestampMs - lastSpeedTs) / 1000.0
            // Ignore gaps longer than 5 s: integrating across a gap would invent distance.
            if (dtS <= 5.0) tripKm += ((v + lastSpeedKmh) / 2.0) * dtS / 3600.0
        }
        lastSpeedTs = r.timestampMs
        lastSpeedKmh = v
        val origin = if (r.origin == DataOrigin.SIMULATED) DataOrigin.SIMULATED else DataOrigin.DERIVED
        return Reading.of(SensorId.TRIP, tripKm.toFloat(), r.timestampMs, origin)
    }

    /** Re-evaluates staleness against the current clock. */
    fun refresh() = synchronized(lock) { emitLocked() }

    private fun emitLocked() {
        _snapshot.value = TelemetrySnapshot(source, clock.nowMs(), HashMap(readings))
    }
}
