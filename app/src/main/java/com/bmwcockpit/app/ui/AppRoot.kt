package com.bmwcockpit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.prefs.BrightnessMode
import com.bmwcockpit.app.ui.customize.EditorScreen
import com.bmwcockpit.app.ui.customize.LayoutPickerScreen
import com.bmwcockpit.app.ui.customize.ThemesScreen
import com.bmwcockpit.app.ui.dash.DashboardScreen
import com.bmwcockpit.app.ui.settings.SettingsScreen
import com.bmwcockpit.app.ui.telemetry.TelemetryScreen
import com.bmwcockpit.app.ui.theme.CockpitTheme
import com.bmwcockpit.app.ui.theme.Palettes
import kotlinx.coroutines.delay

object Routes {
    const val DASH = "dash"
    const val LAYOUTS = "custom/layouts"
    const val EDITOR = "custom/editor"
    const val THEMES = "custom/themes"
    const val TELE_CHARTS = "tele/charts"
    const val TELE_LOGS = "tele/logs"
    const val TELE_ZERO100 = "tele/zero100"
    const val TELE_QUARTER = "tele/quarter"
    const val TELE_LAPS = "tele/laps"
    const val TELE_DIAG = "tele/diag"
    const val SET_CONNECTION = "settings/connection"
    const val SET_GENERAL = "settings/general"
    const val SET_UNITS = "settings/units"
    const val SET_ALARMS = "settings/alarms"
    const val SET_BACKUP = "settings/backup"
    const val SET_ABOUT = "settings/about"

    fun tabOf(route: String?): TopTab = when {
        route == null || route == DASH -> TopTab.PAINEL
        route.startsWith("tele/") -> TopTab.TELEMETRIA
        route.startsWith("custom/") -> TopTab.PERSONALIZAR
        route.startsWith("settings/") -> TopTab.CONFIG
        else -> TopTab.PAINEL
    }
}

/** Navigates to a section, keeping the back stack short: back always returns to the dashboard. */
fun NavHostController.go(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Routes.DASH) { inclusive = false }
        launchSingleTop = true
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()

    // Re-evaluate the "Automático" brightness mode every minute.
    var hourTick by remember { mutableStateOf(0) }
    LaunchedEffect(settings.brightness) {
        while (settings.brightness == BrightnessMode.AUTO) { delay(60_000); hourTick++ }
    }
    val palette = remember(settings.theme, settings.accent, settings.brightness, hourTick) {
        Palettes.resolve(settings.theme, settings.accent, settings.brightness)
    }
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var demoDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    CockpitTheme(palette) {
        val insets = if (settings.immersive) WindowInsets.displayCutout else WindowInsets.safeDrawing
        BoxWithConstraints(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.background(palette.background).windowInsetsPadding(insets)) {
            val compact = maxWidth < 640.dp
            val showTitle = maxWidth >= 1000.dp
            val headerH = (maxHeight * 0.11f).coerceIn(44.dp, 64.dp)
            val status = HeaderStatus(snapshot.source, connection, settings.device?.name, recording)
            Column(Modifier.fillMaxSize()) {
                CockpitHeader(
                    selected = Routes.tabOf(route),
                    status = status,
                    height = headerH,
                    compact = compact,
                    showTitle = showTitle,
                    onTab = { t ->
                        if (t == TopTab.PAINEL && route == Routes.DASH) nav.go(Routes.LAYOUTS) else nav.go(t.route)
                    },
                    onConnectionClick = { nav.go(Routes.SET_CONNECTION) },
                    onDemoClick = { demoDialog = true },
                    onRecordingClick = { nav.go(Routes.TELE_CHARTS) },
                )
                if (snapshot.source == SourceKind.DEMO && compact) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        DemoBadge({ demoDialog = true })
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    NavHost(nav, startDestination = Routes.DASH) {
                        composable(Routes.DASH) { DashboardScreen(vm) }
                        composable(Routes.LAYOUTS) { LayoutPickerScreen(vm, nav) }
                        composable(Routes.EDITOR) { EditorScreen(vm, nav) }
                        composable(Routes.THEMES) { ThemesScreen(vm, nav) }
                        for (r in listOf(Routes.TELE_CHARTS, Routes.TELE_LOGS, Routes.TELE_ZERO100, Routes.TELE_QUARTER, Routes.TELE_LAPS, Routes.TELE_DIAG)) {
                            composable(r) { TelemetryScreen(vm, nav, r) }
                        }
                        for (r in listOf(Routes.SET_CONNECTION, Routes.SET_GENERAL, Routes.SET_UNITS, Routes.SET_ALARMS, Routes.SET_BACKUP, Routes.SET_ABOUT)) {
                            composable(r) { SettingsScreen(vm, nav, r) }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            if (demoDialog) DemoControlDialog(vm, onDismiss = { demoDialog = false }, onOpenConnection = { demoDialog = false; nav.go(Routes.SET_CONNECTION) })
        }
    }
}
