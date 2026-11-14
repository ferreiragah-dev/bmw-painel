package com.bmwcockpit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmwcockpit.app.connection.ConnectionState
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.session.RecordingState
import com.bmwcockpit.app.ui.common.PulseDot
import com.bmwcockpit.app.ui.gauges.MStripes
import com.bmwcockpit.app.ui.gauges.Roundel
import com.bmwcockpit.app.ui.theme.LocalPalette

enum class TopTab(val label: String, val icon: ImageVector, val route: String) {
    PAINEL("Painel", Icons.Filled.Speed, Routes.DASH),
    TELEMETRIA("Telemetria", Icons.Filled.BarChart, Routes.TELE_CHARTS),
    PERSONALIZAR("Personalizar", Icons.Filled.GridView, Routes.EDITOR),
    CONFIG("Configurações", Icons.Filled.Settings, Routes.SET_CONNECTION),
}

data class HeaderStatus(
    val source: SourceKind,
    val connection: ConnectionState,
    val deviceName: String?,
    val recording: RecordingState,
)

/**
 * Top bar reproducing the reference: roundel + M identity, four tabs with the selected one glowing,
 * the "MODO DEMONSTRAÇÃO" indicator (always visible in demo) and the adapter connection pill.
 */
@Composable
fun CockpitHeader(
    selected: TopTab,
    status: HeaderStatus,
    height: Dp,
    compact: Boolean,
    showTitle: Boolean = !compact,
    onTab: (TopTab) -> Unit,
    onConnectionClick: () -> Unit,
    onDemoClick: () -> Unit,
    onRecordingClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(p.surfaceHigh.copy(alpha = 0.9f), p.background)))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Roundel(height * 0.66f)
        Spacer(Modifier.width(8.dp))
        if (showTitle) {
            MStripes(height * 0.24f)
            Spacer(Modifier.width(6.dp))
            Text(
                "BMW Digital Cockpit", color = p.text, fontSize = (height.value * 0.30f).coerceIn(12f, 20f).sp,
                fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
        }
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.Center) {
            TopTab.entries.forEach { t ->
                TabItem(t, t == selected, compact, height, Modifier.weight(1f, fill = false)) { onTab(t) }
            }
        }
        if (status.recording.recording) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onRecordingClick).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(p.danger)
                if (!compact) { Spacer(Modifier.width(4.dp)); Text("REC", color = p.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (status.source == SourceKind.DEMO && !compact) {
            DemoBadge(onDemoClick)
            Spacer(Modifier.width(6.dp))
        }
        ConnectionPill(status, compact, onConnectionClick)
    }
}

@Composable
private fun TabItem(t: TopTab, selected: Boolean, compact: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxHeight()
            .widthIn(min = if (compact) 48.dp else 84.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag("tab_${t.name}"),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier.matchParentSize().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.55f), p.accent.copy(alpha = 0.12f))))
                    .border(1.dp, Brush.verticalGradient(listOf(p.accentBright, p.accent.copy(alpha = 0.3f))), RoundedCornerShape(6.dp)),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(t.icon, t.label, tint = if (selected) Color.White else p.textDim, modifier = Modifier.size((height.value * 0.36f).coerceIn(18f, 26f).dp))
            if (!compact || height > 50.dp) {
                Text(t.label, color = if (selected) Color.White else p.textDim, fontSize = (height.value * 0.19f).coerceIn(9f, 13f).sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun DemoBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.warning.copy(alpha = 0.12f))
            .border(1.dp, p.warning.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 30.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("demo_badge"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(p.warning, 7.dp)
        Spacer(Modifier.width(5.dp))
        Text("MODO DEMONSTRAÇÃO", color = p.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ConnectionPill(status: HeaderStatus, compact: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val (icon, color, line) = when (val c = status.connection) {
        is ConnectionState.Connected -> Triple(Icons.Filled.BluetoothConnected, p.ok, "Conectado")
        is ConnectionState.Connecting -> Triple(Icons.Filled.BluetoothSearching, p.warning, "Conectando…")
        is ConnectionState.Reconnecting -> Triple(Icons.Filled.BluetoothSearching, p.warning, "Reconectando ${c.attempt}/${c.maxAttempts}")
        is ConnectionState.Failed -> Triple(Icons.Filled.BluetoothDisabled, p.danger, "Falha")
        ConnectionState.Idle -> if (status.source == SourceKind.DEMO) Triple(Icons.Filled.Bluetooth, p.textFaint, "Não usado (demo)")
        else Triple(Icons.Filled.BluetoothDisabled, p.textFaint, "Desconectado")
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .background(p.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("connection_pill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, "Conexão OBD2", tint = color, modifier = Modifier.size(18.dp))
        if (!compact) {
            Spacer(Modifier.width(5.dp))
            Column {
                Text(status.deviceName ?: "Vgate", color = p.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                Text(line, color = color, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
