package com.bmwcockpit.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bmwcockpit.app.CockpitApp
import com.bmwcockpit.app.connection.ConnectionState
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.TelemetrySnapshot
import com.bmwcockpit.app.prefs.AlertThresholds
import com.bmwcockpit.app.prefs.AppSettings
import com.bmwcockpit.app.prefs.BrightnessMode
import com.bmwcockpit.app.prefs.DashLayout
import com.bmwcockpit.app.prefs.DriveProfile
import com.bmwcockpit.app.prefs.ThemeId
import com.bmwcockpit.app.prefs.Units
import com.bmwcockpit.app.session.RecordingState
import com.bmwcockpit.app.sim.DemoController
import com.bmwcockpit.app.transport.DeviceRef
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Global UI state shared by every screen: settings, live telemetry, connection and demo controls. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as CockpitApp).container

    val settings: StateFlow<AppSettings> = container.settingsState
    val settingsLoaded: StateFlow<Boolean> = container.settingsLoaded
    val snapshot: StateFlow<TelemetrySnapshot> = container.hub.snapshot
    val connection: StateFlow<ConnectionState> = container.connection.state
    val demo: StateFlow<DemoController.State> = container.demo.state
    val recording: StateFlow<RecordingState> = container.recorder.state
    val messages = container.messages.receiveAsFlow()

    private fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { container.settings.update(t) }

    fun setLayout(l: DashLayout) = update { it.copy(layout = l) }
    fun setTheme(t: ThemeId) = update { it.copy(theme = t) }
    fun setAccent(c: Long?) = update { it.copy(accent = c) }
    fun setBrightness(m: BrightnessMode) = update { it.copy(brightness = m) }
    fun cycleProfile() = update { it.copy(profile = DriveProfile.entries[(it.profile.ordinal + 1) % DriveProfile.entries.size]) }
    fun setProfile(pr: DriveProfile) = update { it.copy(profile = pr) }
    fun setCardSensor(index: Int, s: SensorId) = update { st ->
        st.copy(cardSensors = st.cardSensors.toMutableList().also { if (index in it.indices) it[index] = s })
    }
    fun setRaceSensor(left: Boolean, index: Int, s: SensorId) = update { st ->
        if (left) st.copy(raceLeft = st.raceLeft.toMutableList().also { if (index in it.indices) it[index] = s })
        else st.copy(raceRight = st.raceRight.toMutableList().also { if (index in it.indices) it[index] = s })
    }
    fun setUnits(u: Units) = update { it.copy(units = u) }
    fun setAlerts(a: AlertThresholds) = update { it.copy(alerts = a) }
    fun setAutoConnect(b: Boolean) = update { it.copy(autoConnect = b) }
    fun setAutoReconnect(b: Boolean) = update { it.copy(autoReconnect = b) }
    fun setEconomy(b: Boolean) = update { it.copy(economy = b) }
    fun setKeepScreenOn(b: Boolean) = update { it.copy(keepScreenOn = b) }
    fun setImmersive(b: Boolean) = update { it.copy(immersive = b) }
    fun setDemoPace(p: Float) = update { it.copy(demoPace = p) }
    fun setChartSensors(list: List<SensorId>) = update { it.copy(chartSensors = list) }

    fun useDemo() = container.useDemo()
    fun connectVehicle(device: DeviceRef?) = container.useVehicle(device)
    fun disconnectVehicle() = container.connection.disconnect()

    fun demoRun(running: Boolean) = container.demo.setRunning(running)
    fun demoRestart() = container.demo.restart()
    fun demoLaunch() = container.demo.launchFromStandstill()

    fun startRecording() = container.recorder.start()
    fun stopRecording() = container.recorder.stop()
    fun clearPerfResults() = viewModelScope.launch { container.settings.clearPerfResults() }
}
