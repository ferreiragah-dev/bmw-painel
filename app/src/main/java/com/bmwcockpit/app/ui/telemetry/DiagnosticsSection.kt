package com.bmwcockpit.app.ui.telemetry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.bmwcockpit.app.connection.ConnectionState
import com.bmwcockpit.app.connection.DtcReport
import com.bmwcockpit.app.obd.DtcResult
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.go
import com.bmwcockpit.app.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DiagTab(val label: String) { REAL("Leituras do veículo"), DEMO("Exemplos (demonstração)") }

@Composable
fun DiagnosticsSection(vm: MainViewModel, nav: NavHostController) {
    val p = LocalPalette.current
    val diag by vm.container.diagnostics.state.collectAsStateWithLifecycle()
    val history by vm.container.settings.diagHistory.collectAsStateWithLifecycle(emptyList())
    val conn by vm.connection.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DiagTab.REAL) }
    val connected = conn is ConnectionState.Connected

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Diagnóstico (OBD2)", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val (icon, color, txt) = when (conn) {
                            is ConnectionState.Connected -> Triple(Icons.Filled.CheckCircle, p.ok, "ECU ${(conn as ConnectionState.Connected).info.ecu} conectada")
                            is ConnectionState.Connecting, is ConnectionState.Reconnecting -> Triple(Icons.Filled.Bluetooth, p.warning, "Conectando…")
                            else -> Triple(Icons.Filled.ErrorOutline, p.textFaint, "Sem conexão com o veículo")
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(txt, color = color, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CockpitButton("Ler códigos (DTC)", { vm.container.diagnostics.readVehicle() }, icon = Icons.Filled.Search, kind = BtnKind.PRIMARY, enabled = connected && !diag.reading, tag = "diag_read")
                        if (!connected) CockpitButton("Conectar adaptador", { nav.go(Routes.SET_CONNECTION) }, icon = Icons.Filled.Bluetooth)
                        CockpitButton("Gerar exemplo (demo)", { vm.container.diagnostics.generateDemoExample(); tab = DiagTab.DEMO }, icon = Icons.Filled.Science, tag = "diag_demo")
                        if (diag.reading) CircularProgressIndicator(Modifier.size(22.dp), color = p.accent, strokeWidth = 2.dp)
                    }
                    diag.error?.let { InfoNote(it, color = p.danger) }
                    InfoNote("Esta versão é somente leitura: a limpeza de códigos (serviço 04) não está disponível e nenhum comando de escrita é enviado à ECU.")
                }
            }
            val last = diag.last
            if (last != null) ReportView(last) else InfoNote("Nenhuma leitura nesta sessão. Leituras anteriores ficam no histórico ao lado.")
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Histórico local", color = p.text, fontSize = 14.sp)
                Segmented(DiagTab.entries, tab, { it.label }, { tab = it })
                val list = history.filter { it.simulated == (tab == DiagTab.DEMO) }
                if (list.isEmpty()) InfoNote(if (tab == DiagTab.REAL) "Nenhuma leitura real registrada." else "Nenhum exemplo gerado.")
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list) { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.container.diagnostics.select(r) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(df(r.timestampEpochMs) + if (r.simulated) " · SIMULADO" else " · ECU ${r.ecu}", color = p.text, fontSize = 13.sp)
                                Text(summary(r.stored), color = p.textDim, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun df(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun summary(r: DtcResult): String = when (r) {
    is DtcResult.Codes -> "${r.codes.size} código(s): ${r.codes.joinToString { it.code }}"
    DtcResult.NoCodes -> "Sem códigos armazenados"
    is DtcResult.Incomplete -> "Leitura incompleta"
    is DtcResult.NoResponse -> "Sem resposta"
    is DtcResult.Unsupported -> "Não suportado"
}

@Composable
private fun ReportView(r: DtcReport) {
    val p = LocalPalette.current
    Panel(Modifier.fillMaxWidth().testTag("diag_report"), borderColor = if (r.simulated) p.warning else null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (r.simulated) "EXEMPLO DE DEMONSTRAÇÃO · " else "Leitura do veículo · ECU ${r.ecu} · ") + df(r.timestampEpochMs),
                color = if (r.simulated) p.warning else p.textDim, fontSize = 12.sp,
            )
            r.milOn?.let { Text("Luz de anomalia (MIL): ${if (it) "ACESA" else "apagada"} · ${r.milCount ?: 0} código(s) confirmados informados pela ECU", color = if (it) p.warning else p.textDim, fontSize = 12.sp) }
            r.warning?.let { InfoNote(it, color = p.danger) }
            ResultBlock("Códigos armazenados (serviço 03)", r.stored)
            ResultBlock("Códigos pendentes (serviço 07)", r.pending)
            ResultBlock("Códigos permanentes (serviço 0A)", r.permanent)
        }
    }
}

@Composable
private fun ResultBlock(title: String, r: DtcResult) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        when (r) {
            DtcResult.NoCodes -> StatusLine(Icons.Filled.CheckCircle, p.ok, "Sem falhas registradas (resposta completa da ECU)")
            is DtcResult.Codes -> r.codes.forEach { c -> StatusLine(Icons.Filled.Warning, p.warning, "${c.code} – ${c.description ?: "descrição não disponível (código de fabricante ou fora da tabela)"}") }
            is DtcResult.Incomplete -> {
                StatusLine(Icons.Filled.ErrorOutline, p.danger, "Leitura incompleta: ${r.reason}. Não é possível afirmar ausência de outros códigos.")
                r.partial.forEach { c -> StatusLine(Icons.Filled.Warning, p.warning, "${c.code} – ${c.description ?: "descrição não disponível"}") }
            }
            is DtcResult.NoResponse -> StatusLine(Icons.Filled.ErrorOutline, p.danger, "Sem resposta válida: ${r.reason}. Isto NÃO indica ausência de códigos.")
            is DtcResult.Unsupported -> StatusLine(Icons.Filled.HelpOutline, p.textFaint, r.reason)
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String) {
    val p = LocalPalette.current
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = p.text, fontSize = 13.sp)
    }
}
