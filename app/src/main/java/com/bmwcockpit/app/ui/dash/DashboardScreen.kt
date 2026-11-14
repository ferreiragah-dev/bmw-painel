package com.bmwcockpit.app.ui.dash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    Box(Modifier.fillMaxSize()) {
        DashFor(ctx.settings.layout, ctx, Modifier.fillMaxSize())
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
