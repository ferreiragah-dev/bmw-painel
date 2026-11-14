package com.bmwcockpit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.TelemetrySnapshot
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.common.sensorIcon
import com.bmwcockpit.app.ui.theme.LocalPalette

/** Dialog frame matching the cockpit style. Back / outside tap dismisses it before leaving the screen. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CockpitDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .semantics { testTagsAsResourceId = true }
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .background(p.surface)
                .border(1.dp, p.borderBright, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

/** Demo simulator controls: run/pause, restart, launch from standstill, pace. */
@Composable
fun DemoControlDialog(vm: MainViewModel, onDismiss: () -> Unit, onOpenConnection: () -> Unit) {
    val demo by vm.demo.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    CockpitDialog("Modo demonstração", onDismiss) {
        InfoNote("Todos os valores exibidos neste modo são gerados por um simulador físico do F30 320i (dados SIMULADOS, não leituras do veículo).")
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (demo.running) CockpitButton("Pausar", { vm.demoRun(false) }, icon = Icons.Filled.Pause, tag = "demo_pause")
            else CockpitButton("Iniciar", { vm.demoRun(true) }, icon = Icons.Filled.PlayArrow, kind = BtnKind.SUCCESS, tag = "demo_play")
            CockpitButton("Reiniciar", { vm.demoRestart() }, icon = Icons.Filled.Replay, tag = "demo_restart")
            CockpitButton("Partir da imobilidade", { vm.demoLaunch() }, icon = Icons.Filled.Flag, kind = BtnKind.PRIMARY, tag = "demo_launch")
        }
        Spacer(Modifier.size(12.dp))
        Text("Ritmo da simulação", color = LocalPalette.current.textDim, fontSize = 13.sp)
        Spacer(Modifier.size(6.dp))
        Segmented(listOf(0.5f, 1f, 2f, 4f), settings.demoPace, { "${if (it < 1f) "0,5" else it.toInt()}×" }, { vm.setDemoPace(it) })
        Spacer(Modifier.size(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CockpitButton("Usar o veículo (OBD2)", onOpenConnection, icon = Icons.Filled.Bluetooth)
            Spacer(Modifier.weight(1f))
            CockpitButton("Fechar", onDismiss, kind = BtnKind.PRIMARY, tag = "dialog_close")
        }
    }
}

/** Picks a sensor for a configurable card. Shows availability in the current source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SensorPickerDialog(
    title: String,
    current: SensorId,
    options: List<SensorId>,
    snapshot: TelemetrySnapshot,
    onPick: (SensorId) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    CockpitDialog(title, onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { s ->
                val sel = s == current
                val available = snapshot.isFresh(s)
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) p.accent.copy(alpha = 0.25f) else p.surfaceHigh)
                        .border(1.dp, if (sel) p.accent else p.border, RoundedCornerShape(8.dp))
                        .clickable { onPick(s); onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("pick_${s.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(sensorIcon(s), null, tint = if (available) p.text else p.textFaint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(s.label, color = p.text, fontSize = 13.sp)
                        Text(if (available) "disponível" else (snapshot.reading(s)?.note ?: "indisponível na fonte atual"), color = if (available) p.ok else p.textFaint, fontSize = 10.sp, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { CockpitButton("Cancelar", onDismiss) }
    }
}
