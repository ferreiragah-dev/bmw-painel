package com.bmwcockpit.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bmwcockpit.app.CockpitApp
import com.bmwcockpit.app.transport.BluetoothPermissions
import com.bmwcockpit.app.transport.FoundDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Bluetooth device search (Classic discovery + BLE scan), limited to 12 s and cancellable. */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CockpitApp).container

    data class State(val scanning: Boolean = false, val devices: List<FoundDevice> = emptyList(), val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun scan() {
        if (!BluetoothPermissions.scanGranted(getApplication())) {
            _state.update { it.copy(error = "Permissão de Bluetooth necessária para buscar dispositivos.") }
            return
        }
        job?.cancel()
        _state.value = State(scanning = true)
        job = viewModelScope.launch {
            withTimeoutOrNull(12_000) {
                container.scanner.scan()
                    .catch { e -> _state.update { it.copy(error = e.message ?: "Falha na busca") } }
                    .collect { list -> _state.update { it.copy(devices = list) } }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(scanning = false) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        job?.cancel()
    }
}
