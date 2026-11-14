package com.bmwcockpit.app.perf

import kotlinx.serialization.Serializable

enum class PerfKind(val label: String) { ZERO_100("0–100 km/h"), QUARTER_MILE("1/4 de milha") }

@Serializable
data class PerfResult(
    val kind: PerfKind,
    val timestampEpochMs: Long,
    val timeS: Double,
    val trapSpeedKmh: Double? = null,
    val distanceM: Double? = null,
    val simulated: Boolean,
    val maxGapMs: Long,
    val samples: Int,
)

sealed class PerfState {
    data object Idle : PerfState()
    /** Armed but the car is moving or speed is unknown: stop the car to start. */
    data class WaitingStandstill(val reason: String) : PerfState()
    data object Armed : PerfState()
    data class Running(val elapsedS: Double, val speedKmh: Double, val distanceM: Double) : PerfState()
    data class Finished(val result: PerfResult) : PerfState()
    data class Aborted(val reason: String) : PerfState()
}

/**
 * Computes 0–100 km/h and quarter-mile times from timestamped speed samples of the active source.
 *
 * - Start: armed with the car stopped (< 1 km/h); the departure instant is extrapolated back from the first two
 *   moving samples (assuming constant acceleration), bounded by the last standstill sample.
 * - Finish: the crossing of 100 km/h (or 402.336 m) is linearly interpolated between the two samples around it.
 * - Distance: trapezoidal integration of speed.
 * - Abort: speed unavailable/stale (disconnection), a gap between samples above [maxGapMs], the car stopping
 *   again, or more than [timeoutS] seconds.
 */
class PerformanceMeter(
    val kind: PerfKind,
    private val maxGapMs: Long = 700,
    private val timeoutS: Double = 60.0,
) {
    var state: PerfState = PerfState.Idle
        private set

    private var lastT = -1L
    private var lastV = 0.0
    private var startT = 0L
    private var distance = 0.0
    private var worstGap = 0L
    private var samples = 0
    private var simulated = false

    // Departure refinement: the instant the car left standstill is extrapolated from the first two moving samples.
    private var refineStart = false
    private var t0 = 0L
    private var t1 = 0L
    private var v1 = 0.0

    fun arm() {
        state = if (lastT >= 0 && lastV < STANDSTILL_KMH) PerfState.Armed
        else PerfState.WaitingStandstill("Pare o veículo para armar a medição")
    }

    fun cancel() { state = PerfState.Idle }

    /** Feeds one sample. [speedKmh] null means unavailable/stale. [epochNow] is used only to timestamp results. */
    fun onSample(t: Long, speedKmh: Float?, isSimulated: Boolean, epochNow: Long): PerfState {
        val st = state
        if (speedKmh == null) {
            if (st is PerfState.Running) state = PerfState.Aborted("Velocidade indisponível (desconexão ou leitura vencida)")
            else if (st is PerfState.Armed) state = PerfState.WaitingStandstill("Aguardando leitura de velocidade")
            lastT = -1
            return state
        }
        val v = speedKmh.toDouble()
        val gap = if (lastT >= 0) t - lastT else 0L
        when (st) {
            PerfState.Idle, is PerfState.Finished, is PerfState.Aborted -> Unit
            is PerfState.WaitingStandstill -> if (v < STANDSTILL_KMH) state = PerfState.Armed
            PerfState.Armed -> if (v >= STANDSTILL_KMH && lastT >= 0) {
                if (gap > maxGapMs) {
                    state = PerfState.Aborted("Atraso excessivo entre amostras na largada ($gap ms)")
                } else {
                    startT = lastT
                    t0 = lastT; t1 = t; v1 = v; refineStart = true
                    distance = (lastV + v) / 2.0 / 3.6 * gap / 1000.0
                    worstGap = gap
                    samples = 2
                    simulated = isSimulated
                    state = PerfState.Running((t - startT) / 1000.0, v, distance)
                    checkFinish(t, v, epochNow)
                }
            }
            is PerfState.Running -> {
                samples++
                if (gap > maxGapMs) {
                    state = PerfState.Aborted("Atraso excessivo entre amostras ($gap ms > $maxGapMs ms)")
                } else {
                    worstGap = maxOf(worstGap, gap)
                    simulated = simulated || isSimulated
                    if (refineStart) {
                        refineStart = false
                        val tStar = if (v > v1) t1 - v1 * (t - t1) / (v - v1) else t0.toDouble()
                        val clamped = tStar.coerceIn(t0.toDouble(), t1.toDouble())
                        startT = clamped.toLong()
                        distance = v1 / 2.0 / 3.6 * (t1 - clamped) / 1000.0
                    }
                    val prevDistance = distance
                    distance += (lastV + v) / 2.0 / 3.6 * gap / 1000.0
                    state = PerfState.Running((t - startT) / 1000.0, v, distance)
                    checkFinish(t, v, epochNow, prevDistance)
                    val now = state
                    if (now is PerfState.Running) {
                        if (v < STANDSTILL_KMH) state = PerfState.Aborted("O veículo parou antes de concluir")
                        else if (now.elapsedS > timeoutS) state = PerfState.Aborted("Tempo limite de ${timeoutS.toInt()} s excedido")
                    }
                }
            }
        }
        lastT = t
        lastV = v
        return state
    }

    private fun checkFinish(t: Long, v: Double, epochNow: Long, prevDistance: Double = 0.0) {
        when (kind) {
            PerfKind.ZERO_100 -> if (v >= 100.0) {
                val frac = if (v > lastV) ((100.0 - lastV) / (v - lastV)).coerceIn(0.0, 1.0) else 1.0
                val tCross = lastT + frac * (t - lastT)
                finish((tCross - startT) / 1000.0, null, null, epochNow)
            }
            PerfKind.QUARTER_MILE -> if (distance >= QUARTER_M) {
                val seg = distance - prevDistance
                val frac = if (seg > 0) ((QUARTER_M - prevDistance) / seg).coerceIn(0.0, 1.0) else 1.0
                val tCross = lastT + frac * (t - lastT)
                val trap = lastV + frac * (v - lastV)
                finish((tCross - startT) / 1000.0, trap, QUARTER_M, epochNow)
            }
        }
    }

    private fun finish(timeS: Double, trap: Double?, dist: Double?, epochNow: Long) {
        state = PerfState.Finished(PerfResult(kind, epochNow, timeS, trap, dist, simulated, worstGap, samples))
    }

    companion object {
        const val STANDSTILL_KMH = 1.0
        const val QUARTER_M = 402.336
    }
}

data class Lap(val number: Int, val timeS: Double, val distanceM: Double, val maxKmh: Double, val simulated: Boolean)

/** Manual lap timer: start, mark laps, stop. Time base and distance come from the active source samples. */
class LapTimer {
    var running = false; private set
    var laps: List<Lap> = emptyList(); private set
    private var lapStartT = -1L
    private var lastT = -1L
    private var lastV = 0.0
    private var lapDist = 0.0
    private var lapMax = 0.0
    private var simulated = false
    var currentLapS: Double = 0.0; private set
    var currentLapDistanceM: Double = 0.0; private set

    fun start() {
        running = true
        laps = emptyList()
        lapStartT = lastT
        lapDist = 0.0; lapMax = 0.0
    }

    fun markLap(): Lap? {
        if (!running || lapStartT < 0 || lastT < 0) return null
        val lap = Lap(laps.size + 1, (lastT - lapStartT) / 1000.0, lapDist, lapMax, simulated)
        laps = laps + lap
        lapStartT = lastT
        lapDist = 0.0; lapMax = 0.0
        return lap
    }

    fun stop() {
        markLap()
        running = false
    }

    fun reset() {
        running = false
        laps = emptyList()
        currentLapS = 0.0
        currentLapDistanceM = 0.0
    }

    fun onSample(t: Long, speedKmh: Float?, isSimulated: Boolean) {
        if (speedKmh == null) return
        if (running && lastT >= 0 && t - lastT <= 2_000) {
            lapDist += (lastV + speedKmh) / 2.0 / 3.6 * (t - lastT) / 1000.0
        }
        if (running) {
            if (lapStartT < 0) lapStartT = t
            lapMax = maxOf(lapMax, speedKmh.toDouble())
            simulated = simulated || isSimulated
            currentLapS = (t - lapStartT) / 1000.0
            currentLapDistanceM = lapDist
        }
        lastT = t
        lastV = speedKmh.toDouble()
    }
}
