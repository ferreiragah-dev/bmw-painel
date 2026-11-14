package com.bmwcockpit.app.ui.dash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmwcockpit.app.ui.theme.LocalPalette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.DashLayout
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.SensorPickerDialog

@Composable
fun rememberDashContext(vm: MainViewModel, thumbnail: Boolean = false, onCardTap: ((CardSlot) -> Unit)? = null): DashContext {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history = vm.container.hub.history
    val layouts = DashLayout.entries
    return DashContext(
        snap = snap,
        settings = settings,
        history = { sensor, window -> history.range(sensor, snap.nowMs - window, snap.nowMs) },
        onCardTap = onCardTap,
        onProfileTap = { vm.cycleProfile() },
        onPrev = { vm.setLayout(layouts[(settings.layout.ordinal - 1 + layouts.size) % layouts.size]) },
        onNext = { vm.setLayout(layouts[(settings.layout.ordinal + 1) % layouts.size]) },
        thumbnail = thumbnail,
    )
}

@Composable
fun DashFor(layout: DashLayout, ctx: DashContext, modifier: Modifier = Modifier) {
    when (layout) {
        DashLayout.M_PERFORMANCE -> MPerformanceDash(ctx, modifier)
        DashLayout.RACE -> RaceDash(ctx, modifier)
        DashLayout.GT -> GtDash(ctx, modifier)
        DashLayout.MINIMAL -> MinimalDash(ctx, modifier)
        DashLayout.CUSTOM -> CustomDash(ctx, ctx.settings.customLayout, modifier)
    }
}

/** The main dashboard: renders the persisted layout full screen (no scrolling). */
@Composable
fun DashboardScreen(vm: MainViewModel) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = rememberDashContext(vm, onCardTap = { slot ->
        picking = when (slot) {
            is CardSlot.Bottom -> "b:${slot.index}"
            is CardSlot.Race -> "r:${if (slot.left) "l" else "r"}:${slot.index}"
        }
    })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth * 1.05f
        if (portrait) {
            // Portrait compositions have no room for edge arrows: a compact layout switcher sits on top.
            Column(Modifier.fillMaxSize()) {
                PortraitLayoutSwitcher(ctx)
                DashFor(ctx.settings.layout, ctx.copy(onPrev = null, onNext = null), Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            DashFor(ctx.settings.layout, ctx, Modifier.fillMaxSize())
        }
    }
    val p = picking
    if (p != null) {
        val parts = p.split(":")
        val current: SensorId
        val title: String
        val apply: (SensorId) -> Unit
        if (parts[0] == "b") {
            val i = parts[1].toInt()
            current = ctx.settings.cardSensors.getOrElse(i) { SensorId.BOOST }
            title = "Sensor do cartão ${i + 1}"
            apply = { vm.setCardSensor(i, it) }
        } else {
            val left = parts[1] == "l"
            val i = parts[2].toInt()
            current = (if (left) ctx.settings.raceLeft else ctx.settings.raceRight).getOrElse(i) { SensorId.SPEED }
            title = "Sensor do painel ${if (left) "esquerdo" else "direito"} ${i + 1}"
            apply = { vm.setRaceSensor(left, i, it) }
        }
        SensorPickerDialog(title, current, SensorId.cardSelectable, ctx.snap, apply, onDismiss = { picking = null })
    }
}

@Composable
private fun PortraitLayoutSwitcher(ctx: DashContext) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton({ ctx.onPrev?.invoke() }, Modifier.testTag("dash_prev")) {
            Icon(Icons.Filled.ChevronLeft, "Layout anterior", tint = p.text)
        }
        Text(ctx.settings.layout.label, color = p.textDim, fontSize = 14.sp)
        IconButton({ ctx.onNext?.invoke() }, Modifier.testTag("dash_next")) {
            Icon(Icons.Filled.ChevronRight, "Próximo layout", tint = p.text)
        }
    }
}