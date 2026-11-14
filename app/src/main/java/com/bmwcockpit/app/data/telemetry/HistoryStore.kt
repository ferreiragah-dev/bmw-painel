package com.bmwcockpit.app.data.telemetry

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId

/** One point of a time series. */
data class HistoryPoint(val t: Long, val v: Float, val origin: DataOrigin)

/**
 * Bounded in-memory history per sensor (ring buffers). Samples closer than [minIntervalMs] to the previous
 * stored sample are skipped, so the memory footprint is fixed: [capacity] points per sensor, which at the
 * default 200 ms interval keeps the last 10 minutes.
 */
class HistoryStore(
    val capacity: Int = DEFAULT_CAPACITY,
    private val minIntervalMs: Long = 200,
) {
    private class Ring(cap: Int) {
        val t = LongArray(cap)
        val v = FloatArray(cap)
        val o = arrayOfNulls<DataOrigin>(cap)
        var start = 0
        var size = 0
    }

    private val rings = HashMap<SensorId, Ring>()
    private val lock = Any()

    @Volatile
    var version: Long = 0
        private set

    fun clear() = synchronized(lock) {
        rings.clear()
        version++
    }

    fun addAll(readings: List<Reading>) = synchronized(lock) {
        var changed = false
        for (r in readings) {
            val value = r.value ?: continue
            if (r.origin == DataOrigin.UNAVAILABLE) continue
            val ring = rings.getOrPut(r.sensor) { Ring(capacity) }
            if (ring.size > 0) {
                val lastIdx = (ring.start + ring.size - 1) % capacity
                if (r.timestampMs - ring.t[lastIdx] < minIntervalMs) continue
            }
            val idx: Int
            if (ring.size < capacity) {
                idx = (ring.start + ring.size) % capacity
                ring.size++
            } else {
                idx = ring.start
                ring.start = (ring.start + 1) % capacity
            }
            ring.t[idx] = r.timestampMs
            ring.v[idx] = value
            ring.o[idx] = r.origin
            changed = true
        }
        if (changed) version++
    }

    /** Points of [sensor] with timestamp in [fromMs, toMs], oldest first. */
    fun range(sensor: SensorId, fromMs: Long, toMs: Long): List<HistoryPoint> = synchronized(lock) {
        val ring = rings[sensor] ?: return emptyList()
        val out = ArrayList<HistoryPoint>(minOf(ring.size, 1024))
        for (i in 0 until ring.size) {
            val idx = (ring.start + i) % capacity
            val t = ring.t[idx]
            if (t in fromMs..toMs) out += HistoryPoint(t, ring.v[idx], ring.o[idx] ?: DataOrigin.UNAVAILABLE)
        }
        out
    }

    fun size(sensor: SensorId): Int = synchronized(lock) { rings[sensor]?.size ?: 0 }

    fun oldestTimestamp(): Long? = synchronized(lock) {
        rings.values.filter { it.size > 0 }.minOfOrNull { it.t[it.start] }
    }

    companion object {
        const val DEFAULT_CAPACITY = 3_000
    }
}
