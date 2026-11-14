package com.bmwcockpit.app.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bmwcockpit.app.connection.DtcReport
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.perf.PerfResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists everything in a Preferences DataStore. Complex values are stored as JSON and decoded
 * tolerantly: unknown keys are ignored, invalid enum values fall back to defaults and a corrupted
 * document yields the default settings instead of crashing.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val settings: Flow<AppSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeSettings(it[KEY_SETTINGS]) }
        .distinctUntilChanged()

    val diagHistory: Flow<List<DtcReport>> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p -> decodeList(p[KEY_DIAG], DtcReport.serializer()) }

    val perfResults: Flow<List<PerfResult>> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p -> decodeList(p[KEY_PERF], PerfResult.serializer()) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val cur = decodeSettings(p[KEY_SETTINGS])
            p[KEY_SETTINGS] = json.encodeToString(AppSettings.serializer(), sanitize(transform(cur)))
        }
    }

    suspend fun addDiagReport(report: DtcReport) {
        store.edit { p ->
            val list = decodeList(p[KEY_DIAG], DtcReport.serializer())
            p[KEY_DIAG] = json.encodeToString(ListSerializer(DtcReport.serializer()), (listOf(report) + list).take(MAX_DIAG))
        }
    }

    suspend fun clearDiagHistory(simulatedOnly: Boolean) {
        store.edit { p ->
            val list = decodeList(p[KEY_DIAG], DtcReport.serializer())
            val kept = if (simulatedOnly) list.filterNot { it.simulated } else emptyList()
            p[KEY_DIAG] = json.encodeToString(ListSerializer(DtcReport.serializer()), kept)
        }
    }

    suspend fun addPerfResult(result: PerfResult) {
        store.edit { p ->
            val list = decodeList(p[KEY_PERF], PerfResult.serializer())
            p[KEY_PERF] = json.encodeToString(ListSerializer(PerfResult.serializer()), (listOf(result) + list).take(MAX_PERF))
        }
    }

    suspend fun clearPerfResults() {
        store.edit { it.remove(KEY_PERF) }
    }

    /** JSON backup of the settings (Configurações → Exportar/Importar). */
    suspend fun exportJson(): String = json.encodeToString(AppSettings.serializer(), current())

    /** Imports a backup; throws IllegalArgumentException with a readable message on invalid content. */
    suspend fun importJson(text: String) {
        val parsed = try {
            json.decodeFromString(AppSettings.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo inválido: não é um backup do BMW Digital Cockpit (${e.message?.take(80)})")
        }
        store.edit { it[KEY_SETTINGS] = json.encodeToString(AppSettings.serializer(), sanitize(parsed)) }
    }

    private fun decodeSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { sanitize(json.decodeFromString(AppSettings.serializer(), raw)) }.getOrElse { AppSettings() }
    }

    private fun <T> decodeList(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrElse { emptyList() }
    }

    companion object {
        val KEY_SETTINGS = stringPreferencesKey("settings_json")
        val KEY_DIAG = stringPreferencesKey("diag_history_json")
        val KEY_PERF = stringPreferencesKey("perf_results_json")
        const val MAX_DIAG = 50
        const val MAX_PERF = 60

        /** Repairs values that could break the UI (wrong list sizes, NaN thresholds, widgets outside the area). */
        fun sanitize(s: AppSettings): AppSettings {
            fun fix(list: List<SensorId>, size: Int, fallback: List<SensorId>): List<SensorId> {
                val l = list.filter { it in SensorId.cardSelectable || it == SensorId.SPEED }.take(size)
                return if (l.size == size) l else (l + fallback.filterNot { it in l }).take(size)
            }
            val a = s.alerts
            fun f(v: Float, d: Float, lo: Float, hi: Float) = if (v.isNaN()) d else v.coerceIn(lo, hi)
            val def = AlertThresholds()
            return s.copy(
                cardSensors = fix(s.cardSensors, 6, AppSettings.DEFAULT_CARDS),
                raceLeft = fix(s.raceLeft, 4, AppSettings().raceLeft),
                raceRight = fix(s.raceRight, 4, AppSettings().raceRight),
                chartSensors = s.chartSensors.filter { it in SensorId.chartable }.distinct().take(6)
                    .ifEmpty { AppSettings().chartSensors },
                demoPace = f(s.demoPace, 1f, 0.25f, 4f),
                alerts = a.copy(
                    oilTempMax = f(a.oilTempMax, def.oilTempMax, 60f, 150f),
                    coolantMax = f(a.coolantMax, def.coolantMax, 60f, 130f),
                    iatMax = f(a.iatMax, def.iatMax, 20f, 90f),
                    voltageMin = f(a.voltageMin, def.voltageMin, 9f, 14f),
                    voltageMax = f(a.voltageMax, def.voltageMax, 13f, 16f),
                    boostMax = f(a.boostMax, def.boostMax, 0.2f, 2.5f),
                    shiftRpm = f(a.shiftRpm, def.shiftRpm, 3000f, 7800f),
                ),
                customLayout = EditorDefaults.sanitize(s.customLayout),
                savedLayouts = s.savedLayouts.map { EditorDefaults.sanitize(it) }.distinctBy { it.name }.take(20),
            )
        }
    }
}
