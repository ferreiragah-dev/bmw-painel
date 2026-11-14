package com.bmwcockpit.app.ui.telemetry

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.perf.PerfKind
import com.bmwcockpit.app.perf.PerfState
import com.bmwcockpit.app.ui.CockpitDialog
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.view
import com.bmwcockpit.app.ui.gauges.pxToSp
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtS(s: Double) = String.format(Locale("pt", "BR"), "%.2f", s)

@Composable
fun PerformanceSection(vm: MainViewModel, kind: PerfKind) {
    val p = LocalPalette.current
    val perf by vm.container.performance.state.collectAsStateWithLifecycle()
    val results by vm.container.settings.perfResults.collectAsStateWithLifecycle(emptyList())
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state = if (kind == PerfKind.ZERO_100) perf.zero100 else perf.quarter
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val demo = snap.source == SourceKind.DEMO

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 640.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.4f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Medições de Performance · ${kind.label}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Panel(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(kind.label, color = p.textDim, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                        val (big, sub, color) = when (state) {
                            PerfState.Idle -> Triple("—", "Toque em Armar para preparar a medição", p.textFaint)
                            is PerfState.WaitingStandstill -> Triple("PARE", state.reason, p.warning)
                            PerfState.Armed -> Triple("PRONTO", "Acelere: o cronômetro inicia quando o carro sair da imobilidade", p.ok)
                            is PerfState.Running -> Triple(fmtS(state.elapsedS) + " s", "${state.speedKmh.toInt()} km/h · ${state.distanceM.toInt()} m", p.text)
                            is PerfState.Finished -> Triple(fmtS(state.result.timeS) + " s", buildString {
                                state.result.trapSpeedKmh?.let { append("${it.toInt()} km/h na chegada · ") }
                                append("${state.result.samples} amostras, maior intervalo ${state.result.maxGapMs} ms")
                                if (state.result.simulated) append(" · SIMULADO")
                            }, p.accentBright)
                            is PerfState.Aborted -> Triple("ABORTADA", state.reason, p.danger)
                        }
                        Text(big, color = color, fontSize = pxToSp(with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, modifier = Modifier.testTag("perf_big"))
                        Text(sub, color = p.textDim, fontSize = 13.sp)
                        val speed = snap.view(SensorId.SPEED, settings.units)
                        Text("Velocidade atual: ${speed.text} ${speed.unit}", color = p.textFaint, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state is PerfState.Idle || state is PerfState.Finished || state is PerfState.Aborted) {
                                CockpitButton("Armar", { vm.container.performance.arm(kind) }, icon = Icons.Filled.PlayArrow, kind = BtnKind.SUCCESS, tag = "perf_arm")
                            } else {
                                CockpitButton("Cancelar", { vm.container.performance.cancel(kind) }, icon = Icons.Filled.Close, tag = "perf_cancel")
                            }
                            if (demo) CockpitButton("Simular arrancada", { vm.container.performance.arm(kind); vm.demoLaunch() }, icon = Icons.Filled.Flag, kind = BtnKind.PRIMARY, tag = "perf_sim_launch")
                        }
                    }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Como medir", color = p.text, fontSize = 14.sp)
                        InfoNote("1. Pare o veículo e toque em Armar (estado PRONTO).")
                        InfoNote(if (kind == PerfKind.ZERO_100) "2. Acelere. O tempo é contado do último instante parado até cruzar 100 km/h (interpolado entre amostras)." else "2. Acelere. A distância é integrada da velocidade; o tempo e a velocidade de chegada são interpolados nos 402,3 m.")
                        InfoNote("A medição é abortada se a velocidade ficar indisponível (desconexão), se houver intervalo > 700 ms entre amostras, se o carro parar ou após 60 s.")
                        if (demo) InfoNote("No modo demonstração, 'Simular arrancada' freia o carro simulado até parar e acelera a fundo. Resultados ficam marcados como SIMULADO.", color = p.warning)
                        InfoNote("Use somente em local fechado e seguro. A precisão depende da taxa de amostragem do adaptador OBD2.")
                    }
                }
            }
            if (wide) ResultsPanel(results.filter { it.kind == kind }, Modifier.weight(1f).fillMaxHeight()) { confirmClear = true }
        }
    }
    if (confirmClear) {
        CockpitDialog("Apagar resultados?", { confirmClear = false }) {
            InfoNote("Todos os resultados salvos de 0–100 e 1/4 de milha serão apagados.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Cancelar", { confirmClear = false })
                CockpitButton("Apagar", { confirmClear = false; vm.clearPerfResults() }, kind = BtnKind.DANGER)
            }
        }
    }
}


@Composable
private fun ResultsPanel(results: List<com.bmwcockpit.app.perf.PerfResult>, modifier: Modifier, onClear: () -> Unit) {
    val p = LocalPalette.current
    val df = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    Panel(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Últimas medições", color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                CockpitButton("Limpar", onClear, icon = Icons.Filled.DeleteSweep, enabled = results.isNotEmpty(), compact = true)
            }
            if (results.isEmpty()) InfoNote("Nenhuma medição concluída.", Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtS(r.timeS)} s", color = p.text, fontSize = 16.sp, fontFamily = CondensedFamily, modifier = Modifier.weight(1f))
                        r.trapSpeedKmh?.let { Text("${it.toInt()} km/h  ", color = p.textDim, fontSize = 12.sp) }
                        if (r.simulated) Text("SIMULADO  ", color = p.warning, fontSize = 11.sp)
                        Text(df.format(Date(r.timestampEpochMs)), color = p.textFaint, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LapsSection(vm: MainViewModel) {
    val p = LocalPalette.current
    val perf by vm.container.performance.state.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val ctl = vm.container.performance
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cronômetro de voltas", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Volta ${perf.laps.size + 1}", color = p.textDim, fontSize = 14.sp)
                    Text(lapTime(perf.currentLapS), color = if (perf.lapRunning) p.text else p.textFaint, fontSize = 52.sp, fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, modifier = Modifier.testTag("lap_time"))
                    Text("${perf.currentLapDistanceM.toInt()} m nesta volta" + if (snap.source == SourceKind.DEMO) " · SIMULADO" else "", color = p.textFaint, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!perf.lapRunning) CockpitButton("Iniciar", { ctl.startLaps() }, icon = Icons.Filled.PlayArrow, kind = BtnKind.SUCCESS, enabled = snap.isFresh(SensorId.SPEED), tag = "lap_start")
                        else {
                            CockpitButton("Marcar volta", { ctl.markLap() }, icon = Icons.Filled.Timer, kind = BtnKind.PRIMARY, tag = "lap_mark")
                            CockpitButton("Parar", { ctl.stopLaps() }, icon = Icons.Filled.Stop, kind = BtnKind.DANGER, tag = "lap_stop")
                        }
                        CockpitButton("Zerar", { ctl.resetLaps() }, icon = Icons.Filled.Refresh, enabled = !perf.lapRunning && perf.laps.isNotEmpty())
                    }
                }
            }
            InfoNote("Toque em Iniciar ao cruzar a linha de partida e em Marcar volta a cada passagem. Parar registra a volta em andamento. O tempo usa os carimbos de tempo das amostras da fonte ativa e a distância é integrada da velocidade.")
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                Text("Voltas", color = p.text, fontSize = 14.sp)
                if (perf.laps.isEmpty()) InfoNote("Nenhuma volta registrada.", Modifier.padding(top = 8.dp))
                val best = perf.laps.minByOrNull { it.timeS }
                LazyColumn {
                    items(perf.laps.reversed()) { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${l.number}", color = p.textDim, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
                            Text(lapTime(l.timeS), color = if (l == best) p.ok else p.text, fontSize = 18.sp, fontFamily = CondensedFamily, modifier = Modifier.weight(1f))
                            Text("${l.distanceM.toInt()} m · máx ${l.maxKmh.toInt()} km/h", color = p.textFaint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun lapTime(s: Double): String {
    val m = (s / 60).toInt()
    val sec = s - m * 60
    return String.format(Locale("pt", "BR"), "%d:%05.2f", m, sec)
}
