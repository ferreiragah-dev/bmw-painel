package com.bmwcockpit.app.obd

import com.bmwcockpit.app.data.model.SensorId

/** SAE J1979 mode 01 PIDs used by the cockpit, with their decoding formula. */
enum class Pid(val code: Int, val sensor: SensorId?, val bytes: Int, val decode: (IntArray) -> Float) {
    ENGINE_LOAD(0x04, SensorId.ENGINE_LOAD, 1, { it[0] * 100f / 255f }),
    COOLANT(0x05, SensorId.COOLANT_TEMP, 1, { it[0] - 40f }),
    MAP(0x0B, SensorId.MAP, 1, { it[0].toFloat() }),
    RPM(0x0C, SensorId.RPM, 2, { (it[0] * 256 + it[1]) / 4f }),
    SPEED(0x0D, SensorId.SPEED, 1, { it[0].toFloat() }),
    IAT(0x0F, SensorId.IAT, 1, { it[0] - 40f }),
    MAF(0x10, SensorId.MAF, 2, { (it[0] * 256 + it[1]) / 100f }),
    THROTTLE(0x11, SensorId.THROTTLE, 1, { it[0] * 100f / 255f }),
    BARO(0x33, SensorId.BARO, 1, { it[0].toFloat() }),
    MODULE_VOLTAGE(0x42, SensorId.MODULE_VOLTAGE, 2, { (it[0] * 256 + it[1]) / 1000f }),
    LAMBDA(0x44, SensorId.LAMBDA, 2, { (it[0] * 256 + it[1]) * 2f / 65536f }),
    OIL_TEMP(0x5C, SensorId.OIL_TEMP, 1, { it[0] - 40f });

    val hex: String get() = "%02X".format(code)

    companion object {
        fun byCode(code: Int): Pid? = entries.firstOrNull { it.code == code }
        fun forSensor(sensor: SensorId): Pid? = entries.firstOrNull { it.sensor == sensor }
    }
}

object PidDecoder {

    /**
     * Decodes a mode 01 response payload (starting at the 0x41 service byte). Returns null when the payload
     * is not a response to [pid] or is too short (incomplete answer).
     */
    fun decode(pid: Pid, payload: ByteArray): Float? {
        if (payload.size < 2 + pid.bytes) return null
        if ((payload[0].toInt() and 0xFF) != 0x41) return null
        if ((payload[1].toInt() and 0xFF) != pid.code) return null
        val data = IntArray(pid.bytes) { payload[2 + it].toInt() and 0xFF }
        return pid.decode(data)
    }

    /**
     * Parses a "PIDs supported" bitmap response (PID 00, 20, 40, …). Returns the supported PID numbers in
     * (base, base+0x20], or null when the payload is not a valid answer to [base].
     */
    fun supportedPids(base: Int, payload: ByteArray): Set<Int>? {
        if (payload.size < 6) return null
        if ((payload[0].toInt() and 0xFF) != 0x41 || (payload[1].toInt() and 0xFF) != base) return null
        val out = mutableSetOf<Int>()
        for (byteIdx in 0 until 4) {
            val b = payload[2 + byteIdx].toInt() and 0xFF
            for (bit in 0 until 8) {
                if (b and (0x80 shr bit) != 0) out += base + byteIdx * 8 + bit + 1
            }
        }
        return out
    }

    /** Parses PID 01 01: MIL status and number of confirmed DTCs. */
    fun monitorStatus(payload: ByteArray): Pair<Boolean, Int>? {
        if (payload.size < 3 || (payload[0].toInt() and 0xFF) != 0x41 || (payload[1].toInt() and 0xFF) != 0x01) return null
        val a = payload[2].toInt() and 0xFF
        return Pair(a and 0x80 != 0, a and 0x7F)
    }

    /** Parses "ATRV" output such as "12.6V" or "14.2 V". */
    fun adapterVoltage(lines: List<String>): Float? =
        lines.firstNotNullOfOrNull { l -> Regex("""(\d{1,2}[.,]\d)\s*V""", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1) }
            ?.replace(',', '.')?.toFloatOrNull()
}
