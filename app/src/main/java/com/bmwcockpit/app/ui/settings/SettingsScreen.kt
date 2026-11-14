package com.bmwcockpit.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.bmwcockpit.app.BuildConfig
import com.bmwcockpit.app.connection.ConnectionState
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.data.telemetry.PowerEstimator
import com.bmwcockpit.app.obd.Pid
import com.bmwcockpit.app.prefs.DataSourcePref
import com.bmwcockpit.app.prefs.PressureUnit
import com.bmwcockpit.app.prefs.SpeedUnit
import com.bmwcockpit.app.prefs.TempUnit
import com.bmwcockpit.app.transport.BluetoothPermissions
import com.bmwcockpit.app.transport.DeviceRef
import com.bmwcockpit.app.ui.CockpitDialog
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.LabeledValue
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.RailItem
import com.bmwcockpit.app.ui.common.SectionScaffold
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.common.ToggleRow
import com.bmwcockpit.app.ui.go
import com.bmwcockpit.app.ui.telemetry.stamp
import com.bmwcockpit.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val SettingsRail = listOf(
    RailItem(Routes.SET_CONNECTION, "Conexão OBD2", Icons.Filled.SettingsBluetooth),
    RailItem(Routes.SET_GENERAL, "Geral", Icons.Filled.Settings),
    RailItem(Routes.SET_UNITS, "Unidades", Icons.Filled.Straighten),
    RailItem(Routes.SET_ALARMS, "Alarmes", Icons.Filled.NotificationsActive),
    RailItem(Routes.SET_BACKUP, "Exportar/Importar", Icons.Filled.ImportExport),
    RailItem(Routes.SET_ABOUT, "Sobre", Icons.Filled.Info),
)

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController, route: String) {
    SectionScaffold(SettingsRail, route, { nav.go(it) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (route) {
                Routes.SET_CONNECTION -> ConnectionSection(vm)
                Routes.SET_GENERAL -> GeneralSection(vm)
                Routes.SET_UNITS -> UnitsSection(vm)
                Routes.SET_ALARMS -> AlarmsSection(vm)
                Routes.SET_BACKUP -> BackupSection(vm)
                Routes.SET_ABOUT -> AboutSection()
            }
        }
    }
}

@Composable
private fun Title(t: String) {
    Text(t, color = LocalPalette.current.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

// --------------------------------------------------------------------------------- Connection

@Composable
private fun ConnectionSection(vm: MainViewModel, scan: ScanViewModel = viewModel()) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conn by vm.connection.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val sc by scan.state.collectAsStateWithLifecycle()
    val trace by vm.container.connection.trace.collectAsStateWithLifecycle()
    var permDenied by rememberSaveable { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) { permDenied = false; scan.scan() } else permDenied = true
    }
    fun startScan() {
        if (BluetoothPermissions.scanGranted(ctx)) scan.scan() else permLauncher.launch(BluetoothPermissions.required())
    }

    Title("Conexão OBD2")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fonte de dados", color = p.text, fontSize = 14.sp)
            Segmented(
                DataSourcePref.entries, settings.source,
                { if (it == DataSourcePref.DEMO) "Demonstração (simulador)" else "Veículo (Vgate OBD2)" },
                { if (it == DataSourcePref.DEMO) vm.useDemo() else vm.connectVehicle(null) },
                icon = { if (it == DataSourcePref.DEMO) Icons.Filled.Science else Icons.Filled.DirectionsCar },
            )
            InfoNote("Trocar a fonte limpa todos os valores e o histórico da fonte anterior.")
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, null, tint = p.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.device?.let { "${it.name} (${it.type.label})" } ?: "Nenhum adaptador selecionado", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    val (txt, col) = when (val c = conn) {
                        is ConnectionState.Connected -> "Conectado · ${c.info.protocolName} · ECU ${c.info.ecu}" to p.ok
                        is ConnectionState.Connecting -> c.step to p.warning
                        is ConnectionState.Reconnecting -> "Reconectando (${c.attempt}/${c.maxAttempts}): ${c.lastError}" to p.warning
                        is ConnectionState.Failed -> c.message to p.danger
                        ConnectionState.Idle -> (if (snap.source == SourceKind.DEMO) "Modo demonstração ativo" else "Desconectado") to p.textDim
                    }
                    Text(txt, color = col, fontSize = 12.sp, modifier = Modifier.testTag("conn_status"))
                }
                when (conn) {
                    is ConnectionState.Connected, is ConnectionState.Connecting, is ConnectionState.Reconnecting ->
                        CockpitButton("Desconectar", { vm.disconnectVehicle() }, icon = Icons.Filled.LinkOff, kind = BtnKind.DANGER)
                    else -> CockpitButton("Conectar", { vm.connectVehicle(null) }, icon = Icons.Filled.Bluetooth, kind = BtnKind.PRIMARY, enabled = settings.device != null)
                }
            }
            (conn as? ConnectionState.Connected)?.let { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledValue("Adaptador", c.info.elmVersion)
                    LabeledValue("Protocolo", "${c.info.protocolNumber} ${c.info.protocolName}")
                    LabeledValue("ECUs", c.info.ecusSeen.joinToString())
                }
                InfoNote("PIDs suportados usados: " + Pid.entries.filter { it.code in c.info.supportedPids }.joinToString { "${it.hex} ${it.sensor?.shortLabel ?: ""}" } + ". Transporte: ${c.transport}")
            }
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Adaptadores", color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (sc.scanning) CockpitButton("Parar busca", { scan.stop() }, icon = Icons.Filled.Stop, compact = true)
                else CockpitButton("Buscar dispositivos", { startScan() }, icon = Icons.Filled.BluetoothSearching, compact = true, tag = "bt_scan")
            }
            if (permDenied) {
                InfoNote("Permissão de Bluetooth negada. Sem ela não é possível buscar nem conectar ao adaptador; o modo demonstração continua disponível.", color = p.danger)
                CockpitButton("Abrir configurações do app", {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, compact = true)
            }
            sc.error?.let { InfoNote(it, color = p.danger) }
            if (sc.scanning) InfoNote("Buscando (Bluetooth Classic e BLE)…")
            if (!sc.scanning && sc.devices.isEmpty()) InfoNote("Pareie o Vgate nas configurações de Bluetooth do Android (PIN 1234 ou 0000) para modelos Classic, ou apenas busque para modelos BLE.")
            sc.devices.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (settings.device?.address == d.address) p.accent.copy(alpha = 0.15f) else p.surface)
                        .border(1.dp, if (d.looksLikeObd) p.accent.copy(alpha = 0.6f) else p.border, RoundedCornerShape(6.dp))
                        .clickable { scan.stop(); vm.connectVehicle(DeviceRef(d.name, d.address, d.type)) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bluetooth, null, tint = if (d.looksLikeObd) p.accent else p.textDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name + if (d.looksLikeObd) "  · provável OBD2" else "", color = p.text, fontSize = 14.sp)
                        Text("${d.address} · ${d.type.label}${if (d.bonded) " · pareado" else ""}${d.rssi?.let { " · $it dBm" } ?: ""}", color = p.textDim, fontSize = 11.sp)
                    }
                    Text("Conectar", color = p.accent, fontSize = 13.sp)
                }
            }
            InfoNote("Classic: porta serial RFCOMM/SPP. BLE: perfis FFF0/FFF1/FFF2, FFE0/FFE1, 18F0/2AF0/2AF1 e E781…; outros serviços com escrita+notificação são tentados de forma heurística e um perfil desconhecido gera mensagem com os UUIDs encontrados.")
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            ToggleRow("Conectar automaticamente ao iniciar", settings.autoConnect, { vm.setAutoConnect(it) }, subtitle = "Usa o último adaptador quando a fonte é Veículo")
            ToggleRow("Reconectar em caso de queda", settings.autoReconnect, { vm.setAutoReconnect(it) }, subtitle = "Até 5 tentativas com espera crescente")
            ToggleRow("Modo de economia (menor taxa de atualização)", settings.economy, { vm.setEconomy(it) }, subtitle = "Reduz o polling: útil para adaptadores lentos")
        }
    }
    if (trace.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Text("Registro ELM327 (últimos comandos)", color = p.text, fontSize = 13.sp)
                trace.takeLast(30).forEach { Text(it, color = p.textDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
    InfoNote("Integração Bluetooth implementada e testada com respostas OBD2 gravadas; compatibilidade física com o Vgate ainda não validada em hardware.", color = p.warning)
}

// ------------------------------------------------------------------------------------ General

@Composable
private fun GeneralSection(vm: MainViewModel) {
    val p = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    Title("Geral")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simulador (modo demonstração)", color = p.text, fontSize = 14.sp)
            if (!demo.active) InfoNote("O simulador está desligado porque a fonte atual é o veículo.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!demo.active) CockpitButton("Ativar demonstração", { vm.useDemo() }, icon = Icons.Filled.Science, kind = BtnKind.PRIMARY)
                else {
                    if (demo.running) CockpitButton("Pausar", { vm.demoRun(false) }, icon = Icons.Filled.Pause)
                    else CockpitButton("Iniciar", { vm.demoRun(true) }, icon = Icons.Filled.PlayArrow, kind = BtnKind.SUCCESS)
                    CockpitButton("Reiniciar", { vm.demoRestart() }, icon = Icons.Filled.Replay)
                    CockpitButton("Partir da imobilidade", { vm.demoLaunch() }, icon = Icons.Filled.Flag, kind = BtnKind.PRIMARY)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ritmo", color = p.textDim, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                Segmented(listOf(0.5f, 1f, 2f, 4f), settings.demoPace, { "${if (it < 1f) "0,5" else it.toInt()}×" }, { vm.setDemoPace(it) })
            }
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            ToggleRow("Manter a tela ligada", settings.keepScreenOn, { vm.setKeepScreenOn(it) })
            ToggleRow("Tela cheia (ocultar barras do sistema)", settings.immersive, { vm.setImmersive(it) })
        }
    }
    InfoNote("Segundo plano: sem gravação ativa, o simulador e a leitura OBD2 pausam quando o app sai da tela (o Bluetooth permanece conectado). Com gravação ativa, a coleta e a gravação continuam com uma notificação persistente.")
}

// -------------------------------------------------------------------------------------- Units

@Composable
private fun UnitsSection(vm: MainViewModel) {
    val p = LocalPalette.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val u = settings.units
    Title("Unidades")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Velocidade", color = p.text, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                Segmented(SpeedUnit.entries, u.speed, { it.label }, { vm.setUnits(u.copy(speed = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Temperatura", color = p.text, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                Segmented(TempUnit.entries, u.temp, { it.label }, { vm.setUnits(u.copy(temp = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pressão", color = p.text, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                Segmented(PressureUnit.entries, u.pressure, { it.label }, { vm.setUnits(u.copy(pressure = it)) })
            }
            InfoNote("As unidades afetam instrumentos, cartões e gráficos. O CSV sempre usa as unidades canônicas (km/h, °C, bar, kPa, V) indicadas na coluna unit.")
        }
    }
}

// ------------------------------------------------------------------------------------- Alarms

@Composable
private fun AlarmSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, decimals: Int, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column {
        Row {
            Text(label, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(String.format("%.${decimals}f %s", value, unit), color = p.accentBright, fontSize = 14.sp)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.border))
    }
}

@Composable
private fun AlarmsSection(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val a = settings.alerts
    Title("Alarmes")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow("Alertas visuais ativos", a.enabled, { vm.setAlerts(a.copy(enabled = it)) })
            AlarmSlider("Temperatura do óleo (máx.)", a.oilTempMax, 90f..150f, "°C", 0) { vm.setAlerts(a.copy(oilTempMax = it)) }
            AlarmSlider("Temperatura da água (máx.)", a.coolantMax, 90f..125f, "°C", 0) { vm.setAlerts(a.copy(coolantMax = it)) }
            AlarmSlider("Temperatura de admissão (máx.)", a.iatMax, 30f..90f, "°C", 0) { vm.setAlerts(a.copy(iatMax = it)) }
            AlarmSlider("Turbo (máx.)", a.boostMax, 0.5f..2.5f, "bar", 2) { vm.setAlerts(a.copy(boostMax = it)) }
            AlarmSlider("Tensão mínima", a.voltageMin, 10f..13.5f, "V", 1) { vm.setAlerts(a.copy(voltageMin = it)) }
            AlarmSlider("Tensão máxima", a.voltageMax, 14f..16f, "V", 1) { vm.setAlerts(a.copy(voltageMax = it)) }
            AlarmSlider("Luz de troca de marcha", a.shiftRpm, 4000f..7000f, "rpm", 0) { vm.setAlerts(a.copy(shiftRpm = it)) }
            InfoNote("Aviso (âmbar) perto do limite e alerta (vermelho piscando) ao atingir o limite. Os limites aparecem nos cartões do Race Dashboard. Valores em unidades canônicas.")
        }
    }
}

// ------------------------------------------------------------------------------------- Backup

@Composable
private fun BackupSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = vm.container.settings.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "w")?.use { it.write(json.toByteArray()) } ?: error("destino indisponível") }
                vm.container.notify("Configurações exportadas")
            } catch (e: Exception) { vm.container.notify("Falha ao exportar: ${e.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { s -> s.readBytes().also { require(it.size < 1_000_000) { "arquivo grande demais" } }.toString(Charsets.UTF_8) }
                } ?: error("arquivo indisponível")
                confirmImport = text
            } catch (e: Exception) { vm.container.notify("Falha ao ler: ${e.message}") }
        }
    }
    Title("Exportar/Importar")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoNote("Exporta tema, layout, sensores, editor, alarmes, unidades e conexão em um arquivo JSON escolhido por você. As sessões de telemetria são exportadas em Telemetria → Logs.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Exportar configurações", { exportLauncher.launch("cockpit_config_${stamp()}.json") }, icon = Icons.Filled.FileUpload, kind = BtnKind.PRIMARY)
                CockpitButton("Importar configurações", { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, icon = Icons.Filled.FileDownload)
            }
        }
    }
    confirmImport?.let { text ->
        CockpitDialog("Substituir configurações?", { confirmImport = null }) {
            InfoNote("As configurações atuais serão substituídas pelas do arquivo. Valores inválidos são corrigidos para o padrão.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Cancelar", { confirmImport = null })
                CockpitButton("Importar", {
                    confirmImport = null
                    scope.launch {
                        try { vm.container.settings.importJson(text); vm.container.notify("Configurações importadas") }
                        catch (e: Exception) { vm.container.notify(e.message ?: "Arquivo inválido") }
                    }
                }, kind = BtnKind.PRIMARY)
            }
        }
    }
}

// -------------------------------------------------------------------------------------- About

@Composable
private fun AboutSection() {
    val p = LocalPalette.current
    Title("Sobre")
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BMW Digital Cockpit ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = p.text, fontSize = 15.sp)
            InfoNote("Para BMW 320i F30 2015 ActiveFlex (N20) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · mínimo suportado: Android 7.0 (API 24).")
            InfoNote("Funciona offline. Projeto pessoal, sem afiliação com a BMW AG.")
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Origem dos dados", color = p.text, fontSize = 14.sp)
            InfoNote("Veículo: PIDs OBD2 genéricos (modo 01) suportados pela ECU. Marcha real, pressão de óleo e dados BMW específicos não estão disponíveis no OBD2 genérico e aparecem como “—”.")
            InfoNote("Turbo relativo = MAP (PID 0B) − pressão atmosférica (PID 33, ou MAP medido com motor parado). Sem referência medida, o turbo fica indisponível. O PID 0B satura em 255 kPa (~1,5 bar relativo).")
            InfoNote("Potência/torque ESTIMADOS: combustível = MAF/(${PowerEstimator.AFR_STOICH}×λ); P[kW] = combustível×3600/${PowerEstimator.BSFC_G_PER_KWH.toInt()} g/kWh; T = P×9549/RPM. λ do PID 44 ou assumido (0,85 em carga ≥ 80 %).")
            InfoNote("Tensão ECU (PID 42) é a tensão de alimentação do módulo de controle; Tensão OBD (ATRV) é medida pelo adaptador no pino 16 da tomada.")
            InfoNote("Demonstração: simulador físico (motor, turbo, câmbio 8HP, arrasto) – valores marcados SIM/SIMULADO.")
        }
    }
}
