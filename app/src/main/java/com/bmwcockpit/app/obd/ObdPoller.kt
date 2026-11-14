package com.bmwcockpit.app.obd

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.telemetry.BoostDeriver
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.data.telemetry.PowerEstimator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.IOException

class ConnectionLostException(message: String) : IOException(message)

/**
 * Polls only the PIDs the selected ECU reported as supported. RPM and speed are requested every cycle;
 * pressure/throttle/load more often than temperatures and voltage. Requests are strictly sequential
 * (one in flight), as ELM327 adapters require.
 */
class ObdPoller(
    private val session: ElmSession,
    private val info: VehicleSessionInfo,
    private val clock: Clock,
    private val economy: () -> Boolean,
    private val paused: () -> Boolean = { false },
    private val publish: (List<Reading>) -> Unit,
) {
    private data class Task(val pid: Pid?, val atCommand: String?, val normalMs: Long, val economyMs: Long) {
        var lastRun = Long.MIN_VALUE / 2
        var noDataCount = 0
    }

    private val boost = BoostDeriver()
    private val latest = HashMap<SensorId, Reading>()
    private var consecutiveFailures = 0

    private val tasks: List<Task> = buildList {
        fun pid(p: Pid, n: Long, e: Long) { if (p.code in info.supportedPids) add(Task(p, null, n, e)) }
        pid(Pid.RPM, 0, 0)
        pid(Pid.SPEED, 0, 0)
        pid(Pid.MAP, 200, 600)
        pid(Pid.THROTTLE, 200, 600)
        pid(Pid.MAF, 300, 800)
        pid(Pid.ENGINE_LOAD, 400, 1_000)
        pid(Pid.LAMBDA, 1_000, 3_000)
        pid(Pid.COOLANT, 2_000, 5_000)
        pid(Pid.IAT, 2_000, 5_000)
        pid(Pid.OIL_TEMP, 2_000, 5_000)
        pid(Pid.MODULE_VOLTAGE, 3_000, 8_000)
        pid(Pid.BARO, 30_000, 60_000)
        add(Task(null, "ATRV", 5_000, 10_000))
    }

    /** Readings for sensors this vehicle/ECU cannot provide (published once at session start). */
    fun unavailableAtStart(): List<Reading> {
        val ts = clock.nowMs()
        val out = ArrayList<Reading>()
        for (p in Pid.entries) {
            val s = p.sensor ?: continue
            if (p.code !in info.supportedPids) out += Reading.unavailable(s, ts, "PID ${p.hex} não suportado pela ECU ${info.ecu}")
        }
        out += Reading.unavailable(SensorId.GEAR, ts, "Marcha real não disponível no OBD2 genérico (requer protocolo BMW específico)")
        out += Reading.unavailable(SensorId.OIL_PRESSURE, ts, "Pressão do óleo não disponível no OBD2 genérico")
        if (Pid.MAP.code !in info.supportedPids) out += Reading.unavailable(SensorId.BOOST, ts, "MAP (PID 0B) não suportado")
        if (Pid.MAF.code !in info.supportedPids) {
            out += Reading.unavailable(SensorId.POWER_EST, ts, "Requer MAF (PID 10)")
            out += Reading.unavailable(SensorId.TORQUE_EST, ts, "Requer MAF (PID 10)")
        }
        return out
    }

    suspend fun run() {
        publish(unavailableAtStart())
        val baroSupported = Pid.BARO.code in info.supportedPids
        while (currentCoroutineContext().isActive) {
            if (paused()) { delay(250); continue } // app in background without recording: link kept, no polling
            val eco = economy()
            val cycleStart = clock.nowMs()
            val batch = ArrayList<Reading>()
            for (task in tasks) {
                val interval = if (eco) task.economyMs else task.normalMs
                val now = clock.nowMs()
                if (now - task.lastRun < interval) continue
                task.lastRun = now
                val r = execute(task) ?: continue
                batch += r
                latest[r.sensor] = r
                when (r.sensor) {
                    SensorId.BARO -> r.value?.let { boost.onBaro(it) }
                    SensorId.MAP -> r.value?.let { boost.onMap(it, latest[SensorId.RPM]?.value, baroSupported) }
                    else -> Unit
                }
            }
            val ts = clock.nowMs()
            if (batch.any { it.sensor == SensorId.MAP } && Pid.MAP.code in info.supportedPids) {
                batch += boost.boost(latest[SensorId.MAP]?.value, ts)
            }
            if (batch.any { it.sensor == SensorId.MAF || it.sensor == SensorId.RPM } && Pid.MAF.code in info.supportedPids) {
                batch += PowerEstimator.readings(
                    latest[SensorId.MAF], latest[SensorId.RPM], latest[SensorId.LAMBDA], latest[SensorId.ENGINE_LOAD], ts,
                )
            }
            if (batch.isNotEmpty()) publish(batch)
            if (eco) {
                val elapsed = clock.nowMs() - cycleStart
                if (elapsed < 250) delay(250 - elapsed)
            } else if (batch.isEmpty()) {
                delay(20) // nothing due (e.g. ECU without RPM/speed): avoid a busy loop
            }
        }
    }

    private suspend fun execute(task: Task): Reading? {
        val ts: Long
        if (task.atCommand != null) {
            val r = session.send(task.atCommand, 2_000)
            ts = clock.nowMs()
            checkHealth(r.status)
            val v = PidDecoder.adapterVoltage(r.lines) ?: return null
            return Reading.of(SensorId.ADAPTER_VOLTAGE, v, ts, DataOrigin.VEHICLE)
        }
        val pid = task.pid ?: return null
        val sensor = pid.sensor ?: return null
        val r = session.send("01${pid.hex}", 1_500)
        ts = clock.nowMs()
        checkHealth(r.status)
        if (r.status == ElmStatus.NO_DATA) {
            task.noDataCount++
            return if (task.noDataCount >= 3) Reading.unavailable(sensor, ts, "ECU deixou de responder ao PID ${pid.hex}") else null
        }
        if (!r.ok) return null
        val value = ObdFrameParser.parseFrom(r.lines, info.family, info.ecu)
            .filter { it.complete }
            .firstNotNullOfOrNull { PidDecoder.decode(pid, it.data) }
            ?: return null // incomplete/foreign answer: keep previous value, which will go stale
        task.noDataCount = 0
        return Reading.of(sensor, value, ts, DataOrigin.VEHICLE)
    }

    private fun checkHealth(status: ElmStatus) {
        if (status == ElmStatus.TIMEOUT || status == ElmStatus.BUS_ERROR || status == ElmStatus.UNABLE_TO_CONNECT) {
            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                throw ConnectionLostException("Adaptador deixou de responder ($status em $consecutiveFailures pedidos seguidos)")
            }
        } else {
            consecutiveFailures = 0
        }
    }

    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 4
    }
}
