package com.bmwcockpit.app.ui.telemetry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.session.CsvFormat
import com.bmwcockpit.app.ui.CockpitDialog
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.PulseDot
import com.bmwcockpit.app.ui.common.RailItem
import com.bmwcockpit.app.ui.common.SectionScaffold
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.common.SelectChip
import com.bmwcockpit.app.ui.common.chartColor
import com.bmwcockpit.app.ui.common.convert
import com.bmwcockpit.app.ui.common.decimals
import com.bmwcockpit.app.ui.common.unitLabel
import com.bmwcockpit.app.ui.common.view
import com.bmwcockpit.app.ui.go
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TelemetryRail = listOf(
    RailItem(Routes.TELE_CHARTS, "Gráficos", Icons.Filled.ShowChart),
    RailItem(Routes.TELE_LOGS, "Logs", Icons.AutoMirrored.Filled.List),
    RailItem(Routes.TELE_ZERO100, "0–100 km/h", Icons.Filled.Speed),
    RailItem(Routes.TELE_QUARTER, "1/4 de Milha", Icons.Filled.Flag),
    RailItem(Routes.TELE_LAPS, "Voltas", Icons.Filled.Timer),
    RailItem(Routes.TELE_DIAG, "Diagnóstico", Icons.Filled.BugReport),
)

@Composable
fun TelemetryScreen(vm: MainViewModel, nav: NavHostController, route: String) {
    SectionScaffold(TelemetryRail, route, { nav.go(it) }) {
        when (route) {
            Routes.TELE_CHARTS -> ChartsSection(vm)
            Routes.TELE_LOGS -> LogsSection(vm)
            Routes.TELE_ZERO100 -> PerformanceSection(vm, com.bmwcockpit.app.perf.PerfKind.ZERO_100)
            Routes.TELE_QUARTER -> PerformanceSection(vm, com.bmwcockpit.app.perf.PerfKind.QUARTER_MILE)
            Routes.TELE_LAPS -> LapsSection(vm)
            Routes.TELE_DIAG -> DiagnosticsSection(vm, nav)
        }
    }
}

private val WINDOWS = listOf(30_000L, 60_000L, 120_000L, 300_000L, 600_000L)
private fun windowLabel(ms: Long) = if (ms < 60_000) "${ms / 1000}s" else "${ms / 60_000} min"

@Composable
private fun ChartsSection(vm: MainViewModel, tvm: TelemetryViewModel = viewModel()) {
    val p = LocalPalette.current
    val frame by tvm.frame.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val rec by tvm.recording.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) tvm.exportHistory(uri) }
    val plan = planAxes(frame.series.map { it.sensor }, settings.units)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactH = maxHeight < 380.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Sensor selection
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in listOf(SensorId.RPM, SensorId.BOOST, SensorId.OIL_TEMP, SensorId.COOLANT_TEMP, SensorId.SPEED, SensorId.MODULE_VOLTAGE, SensorId.IAT, SensorId.ENGINE_LOAD, SensorId.THROTTLE, SensorId.POWER_EST, SensorId.ADAPTER_VOLTAGE)) {
                    SelectChip(s.shortLabel, s in settings.chartSensors, { tvm.toggleSensor(s) }, color = chartColor(s), modifier = Modifier.testTag("chip_${s.name}"))
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chartTitle(frame.series.map { it.sensor }, settings.units), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        frame.series.forEach { s ->
                            PulseOrDot(chartColor(s.sensor))
                            Text(" ${s.sensor.shortLabel}  ", color = p.textDim, fontSize = 12.sp)
                        }
                    }
                    Text(plan.title + if (frame.paused) " · PAUSADO (arraste para navegar no histórico)" else "", color = if (frame.paused) p.warning else p.textFaint, fontSize = 11.sp)
                    LineChart(
                        frame, settings.units, p,
                        Modifier.fillMaxWidth().weight(1f).testTag("telemetry_chart").pointerInput(frame.paused, frame.windowMs) {
                            detectHorizontalDragGestures { change, drag ->
                                change.consume()
                                tvm.pan((drag / size.width * frame.windowMs).toLong())
                            }
                        },
                    )
                    if (snap.source == SourceKind.DEMO) Text("Dados simulados (modo demonstração)", color = p.warning, fontSize = 10.sp)
                }
            }
            // Controls
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (frame.paused) CockpitButton("Retomar", { tvm.resume() }, icon = Icons.Filled.PlayArrow, kind = BtnKind.SUCCESS, tag = "chart_resume", compact = true)
                else CockpitButton("Pausar gráfico", { tvm.pause() }, icon = Icons.Filled.Pause, tag = "chart_pause", compact = true)
                Segmented(WINDOWS, frame.windowMs, { windowLabel(it) }, { tvm.setWindow(it) })
                if (rec.recording) CockpitButton("Finalizar sessão (${rec.samples})", { vm.stopRecording() }, icon = Icons.Filled.Stop, kind = BtnKind.DANGER, tag = "rec_stop", compact = true)
                else CockpitButton("Iniciar sessão", { vm.startRecording() }, icon = Icons.Filled.FiberManualRecord, kind = BtnKind.PRIMARY, enabled = snap.source != SourceKind.NONE, tag = "rec_start", compact = true)
                CockpitButton("Exportar CSV", { exportLauncher.launch("historico_${stamp()}.csv") }, icon = Icons.Filled.FileDownload, tag = "chart_export", compact = true)
            }
            if (!compactH) {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    frame.series.take(4).forEach { s ->
                        val v = snap.view(s.sensor, settings.units)
                        val pts = s.points
                        val mn = pts.minOfOrNull { it.v }
                        val mx = pts.maxOfOrNull { it.v }
                        Panel(Modifier.weight(1f).fillMaxHeight(), borderColor = chartColor(s.sensor).copy(alpha = 0.6f), padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                            Column {
                                Text(s.sensor.shortLabel, color = p.textDim, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(v.text, color = p.text, fontSize = 20.sp, fontFamily = CondensedFamily, fontStyle = FontStyle.Italic)
                                    Text(" ${v.unit}", color = p.textDim, fontSize = 11.sp)
                                    Spacer(Modifier.weight(1f))
                                    if (mn != null && mx != null) Text(
                                        "mín ${fmtv(s.sensor, mn, settings.units)} · máx ${fmtv(s.sensor, mx, settings.units)}",
                                        color = p.textFaint, fontSize = 10.sp, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            InfoNote("Pausar congela apenas a visualização: a coleta, o histórico em memória (últimos 10 min) e a gravação da sessão continuam.")
        }
    }
}

@Composable
private fun PulseOrDot(c: androidx.compose.ui.graphics.Color) = PulseDot(c, 8.dp)

private fun fmtv(s: SensorId, v: Float, u: com.bmwcockpit.app.prefs.Units) = String.format("%.${decimals(s, u)}f", convert(s, v, u))

private fun chartTitle(sensors: List<SensorId>, u: com.bmwcockpit.app.prefs.Units): String {
    val units = sensors.map { unitLabel(it, u) }.distinct()
    return when {
        sensors.all { it == SensorId.OIL_TEMP || it == SensorId.COOLANT_TEMP || it == SensorId.IAT } -> "Temperaturas (${units.joinToString()})"
        units.size == 1 -> "${sensors.joinToString(" / ") { it.shortLabel }} (${units[0]})"
        else -> "Telemetria"
    }
}

fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

private fun dateTime(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

@Composable
private fun LogsSection(vm: MainViewModel, tvm: TelemetryViewModel = viewModel()) {
    val p = LocalPalette.current
    val sessions by tvm.sessions.collectAsStateWithLifecycle()
    val rec by tvm.recording.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var exportId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val id = exportId
        if (uri != null && id != null) tvm.exportSession(id, uri)
        exportId = null
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (rec.recording) "Gravando sessão" else "Sessões de telemetria", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    InfoNote(
                        if (rec.recording) "${rec.samples} leituras gravadas desde ${dateTime(rec.startedEpochMs)}. A gravação continua em segundo plano (notificação visível)."
                        else "Cada sessão grava todas as leituras (horário, sensor, valor, unidade e origem) em CSV. Exporte para um local escolhido.",
                    )
                    rec.lastMessage?.let { InfoNote(it, color = p.ok) }
                }
                if (rec.recording) CockpitButton("Finalizar", { vm.stopRecording() }, icon = Icons.Filled.Stop, kind = BtnKind.DANGER, tag = "logs_stop")
                else CockpitButton("Iniciar sessão", { vm.startRecording() }, icon = Icons.Filled.FiberManualRecord, kind = BtnKind.PRIMARY, enabled = snap.source != SourceKind.NONE, tag = "logs_start")
            }
        }
        if (sessions.isEmpty()) InfoNote("Nenhuma sessão gravada.")
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sessions, key = { it.id }) { s ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateTime(s.startedEpochMs) + if (s.source == SourceKind.DEMO) "  · SIMULADA" else "  · ${s.source.label}", color = p.text, fontSize = 14.sp)
                            val dur = s.endedEpochMs?.let { (it - s.startedEpochMs) / 1000 } ?: 0
                            Text("${dur / 60} min ${dur % 60} s · ${s.samples} leituras" + (s.endReason?.let { " · $it" } ?: ""), color = if (s.interrupted) p.warning else p.textDim, fontSize = 12.sp)
                        }
                        CockpitButton("CSV", { exportId = s.id; launcher.launch("${s.id}.csv") }, icon = Icons.Filled.FileDownload, compact = true, tag = "export_${s.id}")
                        Spacer(Modifier.width(6.dp))
                        CockpitButton("Excluir", { confirmDelete = s.id }, icon = Icons.Filled.Delete, kind = BtnKind.DANGER, compact = true)
                    }
                }
            }
        }
        InfoNote("Formato CSV: ${CsvFormat.HEADER}. A coluna origin indica VEHICLE, DERIVED (estimado) ou SIMULATED.")
    }
    confirmDelete?.let { id ->
        CockpitDialog("Excluir sessão?", { confirmDelete = null }) {
            InfoNote("A sessão $id será apagada do armazenamento do app. Esta ação não pode ser desfeita.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Cancelar", { confirmDelete = null })
                CockpitButton("Excluir", { tvm.deleteSession(id); confirmDelete = null }, kind = BtnKind.DANGER)
            }
        }
    }
}

