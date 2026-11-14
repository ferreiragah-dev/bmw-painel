package com.bmwcockpit.app.data.telemetry

import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.Reading
import com.bmwcockpit.app.data.model.SensorId

/**
 * Engine power / torque ESTIMATE from the intake air mass flow.
 *
 *   fuel [g/s]   = MAF [g/s] / (AFR_stoich × λ)          AFR_stoich = 14.7 (gasoline; E-blends lower it,
 *                                                         which this estimate ignores)
 *   power [kW]   = fuel [g/s] × 3600 / BSFC [g/kWh]       BSFC assumed 330 g/kWh (turbo DI gasoline under load)
 *   power [cv]   = kW × 1.35962
 *   torque [Nm]  = kW × 9549 / RPM
 *
 * λ comes from PID 0x44 (commanded equivalence ratio) when supported; otherwise it is assumed 0.85 at
 * load ≥ 80 % (enrichment) and 1.0 below. Without MAF and RPM the estimate is unavailable.
 * This is an estimate at the crankshaft, not a dyno measurement.
 */
object PowerEstimator {
    const val AFR_STOICH = 14.7
    const val BSFC_G_PER_KWH = 330.0
    const val KW_TO_CV = 1.35962

    data class Estimate(val powerCv: Float, val torqueNm: Float)

    fun estimate(mafGs: Float?, rpm: Float?, lambda: Float?, loadPct: Float?): Estimate? {
        if (mafGs == null || rpm == null) return null
        if (rpm < 500f || mafGs <= 0f) return Estimate(0f, 0f)
        val l = (lambda ?: if ((loadPct ?: 0f) >= 80f) 0.85f else 1.0f).coerceIn(0.6f, 1.5f)
        val fuel = mafGs / (AFR_STOICH * l)
        val kw = fuel * 3600.0 / BSFC_G_PER_KWH
        val torque = kw * 9549.0 / rpm
        return Estimate((kw * KW_TO_CV).toFloat(), torque.toFloat())
    }

    /** Builds the two estimate readings, propagating SIMULATED when the inputs are simulated. */
    fun readings(
        maf: Reading?, rpm: Reading?, lambda: Reading?, load: Reading?, ts: Long,
    ): List<Reading> {
        val est = estimate(maf?.value, rpm?.value, lambda?.value, load?.value)
        if (est == null) {
            val note = "Requer MAF (PID 10) e RPM"
            return listOf(
                Reading.unavailable(SensorId.POWER_EST, ts, note),
                Reading.unavailable(SensorId.TORQUE_EST, ts, note),
            )
        }
        val origin = if (maf?.origin == DataOrigin.SIMULATED) DataOrigin.SIMULATED else DataOrigin.DERIVED
        return listOf(
            Reading.of(SensorId.POWER_EST, est.powerCv, ts, origin),
            Reading.of(SensorId.TORQUE_EST, est.torqueNm, ts, origin),
        )
    }
}

/**
 * Relative boost = manifold absolute pressure − barometric pressure.
 *
 * The barometric reference must be measured: PID 0x33 when the ECU supports it; otherwise the MAP read with
 * the engine stopped (RPM = 0, ignition on) during this connection, which equals the ambient pressure.
 * Without either reference the boost is reported unavailable — no fictitious 101.3 kPa is assumed.
 */
class BoostDeriver {
    var baroKpa: Float? = null
        private set
    var baroFromEngineOff = false
        private set

    fun reset() {
        baroKpa = null
        baroFromEngineOff = false
    }

    fun onBaro(kpa: Float) {
        baroKpa = kpa
        baroFromEngineOff = false
    }

    /** Call with each MAP sample; [rpm] is the latest RPM (null if unknown). */
    fun onMap(mapKpa: Float, rpm: Float?, baroPidSupported: Boolean) {
        if (!baroPidSupported && rpm != null && rpm < 1f) {
            baroKpa = mapKpa
            baroFromEngineOff = true
        }
    }

    fun boost(mapKpa: Float?, ts: Long): Reading {
        val baro = baroKpa
        if (mapKpa == null) return Reading.unavailable(SensorId.BOOST, ts, "MAP (PID 0B) indisponível")
        if (baro == null) {
            return Reading.unavailable(
                SensorId.BOOST, ts,
                "Sem pressão atmosférica medida (PID 33 não suportado; ligue a ignição com motor desligado para capturar)",
            )
        }
        return Reading.of(SensorId.BOOST, (mapKpa - baro) / 100f, ts, DataOrigin.DERIVED)
    }
}
