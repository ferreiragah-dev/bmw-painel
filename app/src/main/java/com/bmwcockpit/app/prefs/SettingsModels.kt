package com.bmwcockpit.app.prefs

import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.transport.LinkType
import kotlinx.serialization.Serializable

enum class DashLayout(val label: String) {
    M_PERFORMANCE("M Performance"),
    RACE("Race Dashboard"),
    GT("GT Telemetry"),
    MINIMAL("Minimal"),
    CUSTOM("Meu Painel"),
}

enum class ThemeId(val label: String) {
    M_PERFORMANCE("M Performance"),
    RACE_RED("Race Red"),
    DARK_CARBON("Dark Carbon"),
    BLUE_ICE("Blue Ice"),
    AMBER("Amber"),
}

enum class BrightnessMode(val label: String) { AUTO("Automático"), LIGHT("Claro"), DARK("Escuro") }

/** Purely visual profile badge (not read from the car). */
enum class DriveProfile(val label: String) { COMFORT("COMFORT"), SPORT("SPORT"), SPORT_PLUS("SPORT+") }

enum class SpeedUnit(val label: String) { KMH("km/h"), MPH("mph") }
enum class TempUnit(val label: String) { C("°C"), F("°F") }
enum class PressureUnit(val label: String) { BAR("bar"), PSI("psi"), KPA("kPa") }

@Serializable
data class Units(
    val speed: SpeedUnit = SpeedUnit.KMH,
    val temp: TempUnit = TempUnit.C,
    val pressure: PressureUnit = PressureUnit.BAR,
)

@Serializable
data class AlertThresholds(
    val enabled: Boolean = true,
    val oilTempMax: Float = 120f,
    val coolantMax: Float = 105f,
    val iatMax: Float = 60f,
    val voltageMin: Float = 12.0f,
    val voltageMax: Float = 15.2f,
    val boostMax: Float = 1.6f,
    val shiftRpm: Float = 6300f,
)

enum class WidgetStyle(val label: String) { CIRCULAR("Circular"), DIGITAL("Digital"), BAR("Barra") }

/** A widget of the visual editor. Position/size are normalized (0..1) to the composition area. */
@Serializable
data class EditorWidget(
    val id: String,
    val sensor: SensorId,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val style: WidgetStyle,
    val color: Long,
)

@Serializable
data class EditorLayout(val name: String, val widgets: List<EditorWidget>)

@Serializable
data class SavedDevice(val name: String, val address: String, val type: LinkType)

enum class DataSourcePref { DEMO, VEHICLE }

@Serializable
data class AppSettings(
    val layout: DashLayout = DashLayout.M_PERFORMANCE,
    val theme: ThemeId = ThemeId.M_PERFORMANCE,
    /** Accent colour override (ARGB). null = theme default. */
    val accent: Long? = null,
    val brightness: BrightnessMode = BrightnessMode.DARK,
    val profile: DriveProfile = DriveProfile.SPORT_PLUS,
    val cardSensors: List<SensorId> = DEFAULT_CARDS,
    val raceLeft: List<SensorId> = listOf(SensorId.SPEED, SensorId.BOOST, SensorId.OIL_TEMP, SensorId.COOLANT_TEMP),
    val raceRight: List<SensorId> = listOf(SensorId.POWER_EST, SensorId.TORQUE_EST, SensorId.MODULE_VOLTAGE, SensorId.IAT),
    val units: Units = Units(),
    val alerts: AlertThresholds = AlertThresholds(),
    val customLayout: EditorLayout = EditorDefaults.layout(),
    val savedLayouts: List<EditorLayout> = emptyList(),
    val source: DataSourcePref = DataSourcePref.DEMO,
    val device: SavedDevice? = null,
    val autoConnect: Boolean = true,
    val autoReconnect: Boolean = true,
    val economy: Boolean = false,
    val demoPace: Float = 1f,
    val keepScreenOn: Boolean = true,
    val immersive: Boolean = false,
    val chartSensors: List<SensorId> = listOf(SensorId.OIL_TEMP, SensorId.COOLANT_TEMP, SensorId.IAT),
) {
    companion object {
        val DEFAULT_CARDS = listOf(
            SensorId.BOOST, SensorId.OIL_TEMP, SensorId.COOLANT_TEMP,
            SensorId.OIL_PRESSURE, SensorId.MODULE_VOLTAGE, SensorId.IAT,
        )
    }
}

object EditorDefaults {
    const val BLUE = 0xFF1C8CFFL
    const val RED = 0xFFE5262BL
    const val WHITE = 0xFFFFFFFFL

    fun layout(): EditorLayout = EditorLayout(
        name = "Padrão",
        widgets = listOf(
            EditorWidget("w1", SensorId.SPEED, 0.02f, 0.05f, 0.30f, 0.62f, WidgetStyle.CIRCULAR, BLUE),
            EditorWidget("w2", SensorId.RPM, 0.68f, 0.05f, 0.30f, 0.62f, WidgetStyle.CIRCULAR, RED),
            EditorWidget("w3", SensorId.BOOST, 0.36f, 0.08f, 0.28f, 0.26f, WidgetStyle.DIGITAL, BLUE),
            EditorWidget("w4", SensorId.OIL_TEMP, 0.36f, 0.40f, 0.28f, 0.26f, WidgetStyle.DIGITAL, WHITE),
            EditorWidget("w5", SensorId.COOLANT_TEMP, 0.02f, 0.74f, 0.47f, 0.20f, WidgetStyle.BAR, BLUE),
            EditorWidget("w6", SensorId.MODULE_VOLTAGE, 0.51f, 0.74f, 0.47f, 0.20f, WidgetStyle.BAR, WHITE),
        ),
    )

    /** Keeps every widget inside the area and with a usable minimum size. */
    fun sanitize(layout: EditorLayout): EditorLayout = layout.copy(
        widgets = layout.widgets.map { w ->
            val ww = w.w.coerceIn(MIN_SIZE, 1f)
            val hh = w.h.coerceIn(MIN_SIZE, 1f)
            w.copy(w = ww, h = hh, x = w.x.coerceIn(0f, 1f - ww), y = w.y.coerceIn(0f, 1f - hh))
        },
    )

    const val MIN_SIZE = 0.10f
}
