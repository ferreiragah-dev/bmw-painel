package com.bmwcockpit.app.diag

import com.bmwcockpit.app.connection.DtcReport
import com.bmwcockpit.app.connection.VehicleConnectionManager
import com.bmwcockpit.app.obd.Dtc
import com.bmwcockpit.app.obd.DtcDescriptions
import com.bmwcockpit.app.obd.DtcResult
import com.bmwcockpit.app.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagUiState(
    val reading: Boolean = false,
    val last: DtcReport? = null,
    val error: String? = null,
)

/**
 * Read-only DTC diagnostics. Real readings come from the connected ECU; demo examples are generated
 * explicitly by the user, flagged simulated=true and listed separately.
 */
class DiagnosticsController(
    private val connection: VehicleConnectionManager,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(DiagUiState())
    val state: StateFlow<DiagUiState> = _state.asStateFlow()

    fun readVehicle() {
        if (_state.value.reading) return
        _state.update { it.copy(reading = true, error = null) }
        scope.launch {
            try {
                val report = connection.readDtcs()
                settings.addDiagReport(report)
                _state.update { it.copy(reading = false, last = report) }
            } catch (e: Exception) {
                _state.update { it.copy(reading = false, error = "Falha na leitura: ${e.message ?: e.javaClass.simpleName}. Nenhuma conclusão sobre códigos pode ser tirada.") }
            }
        }
    }

    fun generateDemoExample() {
        val report = DtcReport(
            timestampEpochMs = wallClock(),
            simulated = true,
            ecu = "DEMO",
            milOn = true,
            milCount = 2,
            stored = DtcResult.Codes(listOf(Dtc("P0302", DtcDescriptions.describe("P0302")), Dtc("P0171", DtcDescriptions.describe("P0171")))),
            pending = DtcResult.NoCodes,
            permanent = DtcResult.Unsupported("Exemplo de demonstração"),
        )
        scope.launch {
            settings.addDiagReport(report)
            _state.update { it.copy(last = report, error = null) }
        }
    }

    fun select(report: DtcReport) = _state.update { it.copy(last = report) }
    fun clearError() = _state.update { it.copy(error = null) }
}
