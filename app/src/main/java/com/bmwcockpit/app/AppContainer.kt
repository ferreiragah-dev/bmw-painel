package com.bmwcockpit.app

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bmwcockpit.app.connection.ConnectionState
import com.bmwcockpit.app.connection.VehicleConnectionManager
import com.bmwcockpit.app.data.telemetry.Clock
import com.bmwcockpit.app.data.telemetry.TelemetryHub
import com.bmwcockpit.app.diag.DiagnosticsController
import com.bmwcockpit.app.perf.PerformanceController
import com.bmwcockpit.app.prefs.AppSettings
import com.bmwcockpit.app.prefs.DataSourcePref
import com.bmwcockpit.app.prefs.SettingsRepository
import com.bmwcockpit.app.service.RecordingService
import com.bmwcockpit.app.session.SessionRecorder
import com.bmwcockpit.app.sim.DemoController
import com.bmwcockpit.app.transport.BleTransport
import com.bmwcockpit.app.transport.BluetoothPermissions
import com.bmwcockpit.app.transport.BluetoothScanner
import com.bmwcockpit.app.transport.ClassicSppTransport
import com.bmwcockpit.app.transport.DeviceRef
import com.bmwcockpit.app.transport.LinkType
import com.bmwcockpit.app.transport.ObdTransport
import com.bmwcockpit.app.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-wide object graph (manual DI). Everything that produces or consumes telemetry lives here so
 * it survives Activity recreation and orientation changes.
 *
 * Background policy: when the app goes to background WITHOUT an active recording, the simulator and OBD
 * polling pause (the Bluetooth link is kept). While a session is being recorded, collection and recording
 * continue in background under [RecordingService] (foreground service with a visible notification).
 */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = Clock { SystemClock.elapsedRealtime() }

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile("cockpit_settings") },
    )
    val settings = SettingsRepository(dataStore)
    val settingsState: StateFlow<AppSettings> = settings.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val hub = TelemetryHub(clock, scope)
    val demo = DemoController(hub, clock, scope)
    val connection = VehicleConnectionManager(hub, clock, scope, ::createTransport)
    val recorder = SessionRecorder(File(context.filesDir, "sessions"), hub, scope, clock)
    val performance = PerformanceController(hub, settings, scope)
    val diagnostics = DiagnosticsController(connection, settings, scope)
    val scanner = BluetoothScanner(context)

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot user messages (snackbar). */
    val messages = _messages

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    fun notify(msg: String) { _messages.trySend(msg) }

    private fun createTransport(device: DeviceRef): ObdTransport {
        if (!BluetoothPermissions.connectGranted(context)) throw TransportException("Permissão de Bluetooth negada. Conceda a permissão 'Dispositivos próximos' nas configurações do Android.")
        val adapter = BluetoothPermissions.adapter(context) ?: throw TransportException("Este aparelho não possui Bluetooth")
        if (!adapter.isEnabled) throw TransportException("Bluetooth desligado. Ative o Bluetooth e tente novamente.")
        return when (device.type) {
            LinkType.CLASSIC -> ClassicSppTransport(adapter, device.address)
            LinkType.BLE -> BleTransport(context, adapter, device.address)
        }
    }

    fun start() {
        scope.launch {
            val s = settings.current()
            _settingsLoaded.value = true
            connection.autoReconnect = s.autoReconnect
            connection.economyMode = s.economy
            demo.setPace(s.demoPace)
            when (s.source) {
                DataSourcePref.DEMO -> demo.start(s.demoPace)
                DataSourcePref.VEHICLE -> {
                    val d = s.device
                    if (d != null && s.autoConnect && BluetoothPermissions.connectGranted(context)) {
                        connection.connect(DeviceRef(d.name, d.address, d.type))
                    }
                }
            }
        }
        scope.launch {
            settingsState.collect { s ->
                connection.autoReconnect = s.autoReconnect
                connection.economyMode = s.economy
                demo.setPace(s.demoPace)
            }
        }
        // End a recording when the data source changes, so sessions never mix origins.
        scope.launch {
            hub.snapshot.map { it.source }.distinctUntilChanged().collect { recorder.onSourceChanged() }
        }
        // Foreground service only while recording.
        scope.launch {
            recorder.state.map { it.recording }.distinctUntilChanged().collect { rec ->
                if (rec) RecordingService.start(context) else RecordingService.stop(context)
                applyBackgroundPolicy()
            }
        }
        scope.launch {
            connection.state.collect { st ->
                if (st is ConnectionState.Failed) notify("Conexão OBD2: ${st.message}")
            }
        }
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) { inBackground = false; applyBackgroundPolicy() }
                override fun onStop(owner: LifecycleOwner) { inBackground = true; applyBackgroundPolicy() }
            })
        }
    }

    @Volatile private var inBackground = false

    private fun applyBackgroundPolicy() {
        val pause = inBackground && !recorder.state.value.recording
        demo.setBackgroundPaused(pause)
        connection.pollingPaused = pause
    }

    fun useDemo() {
        scope.launch {
            connection.disconnect()
            settings.update { it.copy(source = DataSourcePref.DEMO) }
            demo.start(settings.current().demoPace)
        }
    }

    fun useVehicle(device: DeviceRef?) {
        scope.launch {
            demo.stop()
            settings.update { s ->
                s.copy(
                    source = DataSourcePref.VEHICLE,
                    device = device?.let { com.bmwcockpit.app.prefs.SavedDevice(it.name, it.address, it.type) } ?: s.device,
                )
            }
            val d = device ?: settings.current().device?.let { DeviceRef(it.name, it.address, it.type) }
            if (d != null) connection.connect(d) else hub.beginSource(com.bmwcockpit.app.data.model.SourceKind.NONE)
        }
    }

    suspend fun awaitSettings(): AppSettings = settings.settings.first()
}
