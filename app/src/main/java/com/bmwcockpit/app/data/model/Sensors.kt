package com.bmwcockpit.app.data.model

/**
 * Every value shown by the cockpit is a [Reading] of one [SensorId].
 *
 * [Reading.value] is null whenever the sensor is unavailable; the UI must then render "—".
 * [Reading.origin] tells where the value came from (vehicle ECU, a derivation/estimate, the demo simulator,
 * or nothing at all), so the interface can label estimates and simulated data honestly.
 */
enum class SensorId(
    val label: String,
    val shortLabel: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val decimals: Int,
    /** A reading older than this is considered stale and is no longer displayed as current. */
    val staleAfterMs: Long,
) {
    SPEED("Velocidade", "Velocidade", "km/h", 0f, 300f, 0, 2_500),
    RPM("Rotação", "RPM", "rpm", 0f, 8000f, 0, 2_500),
    BOOST("Turbo (relativo)", "Turbo", "bar", -1f, 2f, 2, 3_000),
    MAP("Pressão do coletor (abs.)", "MAP", "kPa", 0f, 300f, 0, 3_000),
    BARO("Pressão atmosférica", "Baro", "kPa", 60f, 110f, 0, 30_000),
    OIL_TEMP("Temp. Óleo", "Temp. Óleo", "°C", 0f, 150f, 0, 10_000),
    COOLANT_TEMP("Temp. Água", "Temp. Água", "°C", 0f, 130f, 0, 10_000),
    OIL_PRESSURE("Press. Óleo", "Press. Óleo", "bar", 0f, 8f, 1, 5_000),
    IAT("Temp. Admissão (IAT)", "IAT", "°C", -20f, 90f, 0, 10_000),
    MODULE_VOLTAGE("Tensão do módulo (ECU)", "Tensão ECU", "V", 10f, 16f, 1, 10_000),
    ADAPTER_VOLTAGE("Tensão na tomada OBD (adaptador)", "Tensão OBD", "V", 10f, 16f, 1, 12_000),
    ENGINE_LOAD("Carga do motor", "Carga", "%", 0f, 100f, 0, 3_000),
    THROTTLE("Posição do acelerador", "Acelerador", "%", 0f, 100f, 0, 3_000),
    MAF("Fluxo de ar (MAF)", "MAF", "g/s", 0f, 300f, 1, 3_000),
    LAMBDA("Lambda comandado", "Lambda", "λ", 0.6f, 1.4f, 2, 5_000),
    GEAR("Marcha", "Marcha", "", 0f, 8f, 0, 2_500),
    POWER_EST("Potência (estimada)", "Potência (est.)", "cv", 0f, 400f, 0, 3_000),
    TORQUE_EST("Torque (estimado)", "Torque (est.)", "Nm", 0f, 550f, 0, 3_000),
    TRIP("Distância (trip)", "Trip", "km", 0f, 9999f, 1, 5_000);

    companion object {
        /** Sensors the user can place on dashboard cards (oil pressure only renders when a source provides it). */
        val cardSelectable = listOf(
            SPEED, RPM, BOOST, OIL_TEMP, COOLANT_TEMP, OIL_PRESSURE, IAT,
            MODULE_VOLTAGE, ADAPTER_VOLTAGE, ENGINE_LOAD, THROTTLE, MAF, POWER_EST, TORQUE_EST,
        )

        /** Sensors offered by the telemetry charts. */
        val chartable = listOf(
            RPM, SPEED, BOOST, OIL_TEMP, COOLANT_TEMP, MODULE_VOLTAGE, ADAPTER_VOLTAGE, IAT,
            ENGINE_LOAD, THROTTLE, MAF, POWER_EST, OIL_PRESSURE,
        )

        fun fromNameOrNull(name: String?): SensorId? = entries.firstOrNull { it.name == name }
    }
}

enum class DataOrigin(val label: String) {
    VEHICLE("Veículo"),
    DERIVED("Derivado/estimado"),
    SIMULATED("Simulação"),
    UNAVAILABLE("Indisponível"),
}

enum class SourceKind(val label: String) {
    NONE("Sem fonte"),
    DEMO("Demonstração"),
    VEHICLE("Veículo (OBD2)"),
}

data class Reading(
    val sensor: SensorId,
    val value: Float?,
    val unit: String,
    /** Monotonic timestamp (SystemClock.elapsedRealtime domain) of the sample. */
    val timestampMs: Long,
    val origin: DataOrigin,
    /** Optional short explanation, e.g. why a sensor is unavailable. */
    val note: String? = null,
) {
    val available: Boolean get() = value != null && origin != DataOrigin.UNAVAILABLE

    companion object {
        fun of(sensor: SensorId, value: Float, ts: Long, origin: DataOrigin) =
            Reading(sensor, value, sensor.unit, ts, origin)

        fun unavailable(sensor: SensorId, ts: Long, note: String? = null) =
            Reading(sensor, null, sensor.unit, ts, DataOrigin.UNAVAILABLE, note)
    }
}

/**
 * Immutable view of every sensor at a given instant, already evaluated for staleness.
 * UI code only ever reads [value] / [display], which return null for missing or stale readings.
 */
data class TelemetrySnapshot(
    val source: SourceKind,
    val nowMs: Long,
    val readings: Map<SensorId, Reading>,
) {
    fun reading(sensor: SensorId): Reading? = readings[sensor]

    fun isFresh(sensor: SensorId): Boolean {
        val r = readings[sensor] ?: return false
        return r.available && nowMs - r.timestampMs <= sensor.staleAfterMs
    }

    fun isStale(sensor: SensorId): Boolean {
        val r = readings[sensor] ?: return false
        return r.available && nowMs - r.timestampMs > sensor.staleAfterMs
    }

    /** Value if available and fresh, otherwise null. */
    fun value(sensor: SensorId): Float? = if (isFresh(sensor)) readings[sensor]?.value else null

    fun origin(sensor: SensorId): DataOrigin =
        if (isFresh(sensor)) readings[sensor]?.origin ?: DataOrigin.UNAVAILABLE else DataOrigin.UNAVAILABLE

    companion object {
        val EMPTY = TelemetrySnapshot(SourceKind.NONE, 0L, emptyMap())
    }
}
