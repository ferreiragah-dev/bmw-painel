package com.bmwcockpit.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bmwcockpit.app.data.model.DataOrigin
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.TelemetrySnapshot
import com.bmwcockpit.app.prefs.AlertThresholds
import com.bmwcockpit.app.prefs.PressureUnit
import com.bmwcockpit.app.prefs.SpeedUnit
import com.bmwcockpit.app.prefs.TempUnit
import com.bmwcockpit.app.prefs.Units
import java.util.Locale

const val DASH = "—"

/** Converts a value from the canonical unit of [sensor] to the user's units. */
fun convert(sensor: SensorId, v: Float, u: Units): Float = when (sensor) {
    SensorId.SPEED -> if (u.speed == SpeedUnit.MPH) v * 0.621371f else v
    SensorId.TRIP -> if (u.speed == SpeedUnit.MPH) v * 0.621371f else v
    SensorId.OIL_TEMP, SensorId.COOLANT_TEMP, SensorId.IAT -> if (u.temp == TempUnit.F) v * 9f / 5f + 32f else v
    SensorId.BOOST, SensorId.OIL_PRESSURE -> when (u.pressure) {
        PressureUnit.BAR -> v
        PressureUnit.PSI -> v * 14.5038f
        PressureUnit.KPA -> v * 100f
    }
    else -> v
}

fun unitLabel(sensor: SensorId, u: Units): String = when (sensor) {
    SensorId.SPEED -> u.speed.label
    SensorId.TRIP -> if (u.speed == SpeedUnit.MPH) "mi" else "km"
    SensorId.OIL_TEMP, SensorId.COOLANT_TEMP, SensorId.IAT -> u.temp.label
    SensorId.BOOST, SensorId.OIL_PRESSURE -> u.pressure.label
    SensorId.RPM -> "rpm"
    else -> sensor.unit
}

fun decimals(sensor: SensorId, u: Units): Int = when (sensor) {
    SensorId.BOOST, SensorId.OIL_PRESSURE -> when (u.pressure) { PressureUnit.BAR -> 2; PressureUnit.PSI -> 1; PressureUnit.KPA -> 0 }
    else -> sensor.decimals
}

fun displayRange(sensor: SensorId, u: Units): Pair<Float, Float> = convert(sensor, sensor.min, u) to convert(sensor, sensor.max, u)

/** Formatted number, or "—" when unavailable/stale. */
fun formatValue(sensor: SensorId, value: Float?, u: Units): String {
    if (value == null) return DASH
    val v = convert(sensor, value, u)
    return when (sensor) {
        SensorId.RPM -> String.format(Locale("pt", "BR"), "%,.0f", (v / 10f).toInt() * 10f)
        SensorId.GEAR -> v.toInt().toString()
        else -> String.format(Locale("pt", "BR"), "%.${decimals(sensor, u)}f", v)
    }
}

data class SensorView(
    val sensor: SensorId,
    val raw: Float?,
    val text: String,
    val unit: String,
    val fraction: Float,
    val origin: DataOrigin,
    val stale: Boolean,
    val note: String?,
)

fun TelemetrySnapshot.view(sensor: SensorId, u: Units): SensorView {
    val v = value(sensor)
    val frac = if (v == null) 0f else ((v - sensor.min) / (sensor.max - sensor.min)).coerceIn(0f, 1f)
    return SensorView(
        sensor, v, formatValue(sensor, v, u), unitLabel(sensor, u), frac, origin(sensor), isStale(sensor),
        reading(sensor)?.note,
    )
}

fun sensorIcon(s: SensorId): ImageVector = when (s) {
    SensorId.SPEED -> Icons.Filled.Speed
    SensorId.RPM -> Icons.Filled.Timelapse
    SensorId.BOOST -> Icons.Filled.Cyclone
    SensorId.MAP, SensorId.BARO -> Icons.Filled.Compress
    SensorId.OIL_TEMP -> Icons.Filled.OilBarrel
    SensorId.COOLANT_TEMP -> Icons.Filled.Thermostat
    SensorId.OIL_PRESSURE -> Icons.Filled.WaterDrop
    SensorId.IAT -> Icons.Filled.Air
    SensorId.MODULE_VOLTAGE -> Icons.Filled.BatteryChargingFull
    SensorId.ADAPTER_VOLTAGE -> Icons.Filled.ElectricalServices
    SensorId.ENGINE_LOAD -> Icons.Filled.Whatshot
    SensorId.THROTTLE -> Icons.Filled.Tune
    SensorId.MAF -> Icons.Filled.Air
    SensorId.LAMBDA -> Icons.Filled.Map
    SensorId.GEAR -> Icons.Filled.Settings
    SensorId.POWER_EST -> Icons.Filled.Bolt
    SensorId.TORQUE_EST -> Icons.Filled.RotateRight
    SensorId.TRIP -> Icons.Filled.Route
}

/** Scale bar colour of each sensor card (fixed per sensor so cards stay recognizable). */
fun sensorColor(s: SensorId): Color = when (s) {
    SensorId.BOOST -> Color(0xFFFF5A2A)
    SensorId.OIL_TEMP -> Color(0xFFFF9F1C)
    SensorId.COOLANT_TEMP -> Color(0xFF2FD27A)
    SensorId.OIL_PRESSURE -> Color(0xFF7BDB3B)
    SensorId.MODULE_VOLTAGE, SensorId.ADAPTER_VOLTAGE -> Color(0xFF3BD6A0)
    SensorId.IAT -> Color(0xFF8A6BFF)
    SensorId.SPEED -> Color(0xFF1C8CFF)
    SensorId.RPM -> Color(0xFFFF2B2B)
    SensorId.ENGINE_LOAD -> Color(0xFFFFC23D)
    SensorId.THROTTLE -> Color(0xFF39C6FF)
    SensorId.MAF -> Color(0xFF6EC1FF)
    SensorId.POWER_EST -> Color(0xFFFF4F7B)
    SensorId.TORQUE_EST -> Color(0xFFFFA14F)
    else -> Color(0xFF9AA6B8)
}

/** Chart line colours. */
fun chartColor(s: SensorId): Color = when (s) {
    SensorId.OIL_TEMP -> Color(0xFFFF9F1C)
    SensorId.COOLANT_TEMP -> Color(0xFF2E9BFF)
    SensorId.IAT -> Color(0xFFC04BFF)
    SensorId.RPM -> Color(0xFFFF3B3B)
    SensorId.SPEED -> Color(0xFF3BE0FF)
    SensorId.BOOST -> Color(0xFFFF6A2A)
    SensorId.MODULE_VOLTAGE -> Color(0xFF3BD67A)
    SensorId.ADAPTER_VOLTAGE -> Color(0xFF9BE07B)
    SensorId.ENGINE_LOAD -> Color(0xFFFFD23D)
    SensorId.THROTTLE -> Color(0xFF7AA8FF)
    SensorId.MAF -> Color(0xFF56E6C9)
    SensorId.POWER_EST -> Color(0xFFFF4F7B)
    SensorId.OIL_PRESSURE -> Color(0xFF9BE03B)
    else -> Color(0xFFDDDDDD)
}

enum class AlertLevel { NONE, WARN, CRITICAL }

/** Alert level of a raw (canonical unit) value against the configured thresholds. */
fun alertLevel(sensor: SensorId, value: Float?, a: AlertThresholds): AlertLevel {
    if (!a.enabled || value == null) return AlertLevel.NONE
    fun high(max: Float, margin: Float) = when {
        value >= max -> AlertLevel.CRITICAL
        value >= max - margin -> AlertLevel.WARN
        else -> AlertLevel.NONE
    }
    return when (sensor) {
        SensorId.OIL_TEMP -> high(a.oilTempMax, 8f)
        SensorId.COOLANT_TEMP -> high(a.coolantMax, 5f)
        SensorId.IAT -> high(a.iatMax, 8f)
        SensorId.BOOST -> high(a.boostMax, 0.15f)
        SensorId.RPM -> high(a.shiftRpm, 400f)
        SensorId.MODULE_VOLTAGE, SensorId.ADAPTER_VOLTAGE -> when {
            value < a.voltageMin || value > a.voltageMax -> AlertLevel.CRITICAL
            value < a.voltageMin + 0.3f -> AlertLevel.WARN
            else -> AlertLevel.NONE
        }
        else -> AlertLevel.NONE
    }
}

/** Human-readable limit text shown next to alertable sensors. */
fun alertLimitText(sensor: SensorId, a: AlertThresholds, u: Units): String? = when (sensor) {
    SensorId.OIL_TEMP -> "lim ${formatValue(sensor, a.oilTempMax, u)}${unitLabel(sensor, u)}"
    SensorId.COOLANT_TEMP -> "lim ${formatValue(sensor, a.coolantMax, u)}${unitLabel(sensor, u)}"
    SensorId.IAT -> "lim ${formatValue(sensor, a.iatMax, u)}${unitLabel(sensor, u)}"
    SensorId.BOOST -> "lim ${formatValue(sensor, a.boostMax, u)} ${unitLabel(sensor, u)}"
    SensorId.MODULE_VOLTAGE, SensorId.ADAPTER_VOLTAGE -> "${"%.1f".format(a.voltageMin)}–${"%.1f".format(a.voltageMax)} V"
    else -> null
}.takeIf { a.enabled }

fun originBadge(origin: DataOrigin): String? = when (origin) {
    DataOrigin.SIMULATED -> "SIM"
    DataOrigin.DERIVED -> "EST"
    else -> null
}
