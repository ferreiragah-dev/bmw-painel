package com.bmwcockpit.app.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.TelemetrySnapshot
import com.bmwcockpit.app.data.telemetry.HistoryPoint
import com.bmwcockpit.app.prefs.AppSettings
import com.bmwcockpit.app.prefs.SpeedUnit
import com.bmwcockpit.app.ui.gauges.GaugeScale
import com.bmwcockpit.app.ui.theme.LocalPalette

/** Everything a dashboard needs to render and interact. */
data class DashContext(
    val snap: TelemetrySnapshot,
    val settings: AppSettings,
    /** Recent history for trend widgets: (sensor, window ms) → points. */
    val history: (SensorId, Long) -> List<HistoryPoint>,
    val onCardTap: ((slot: CardSlot) -> Unit)? = null,
    val onProfileTap: (() -> Unit)? = null,
    val onPrev: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    /** Thumbnails disable interaction and animations that would be distracting. */
    val thumbnail: Boolean = false,
)

sealed class CardSlot {
    data class Bottom(val index: Int) : CardSlot()
    data class Race(val left: Boolean, val index: Int) : CardSlot()
}

fun speedScale(s: AppSettings) =
    if (s.units.speed == SpeedUnit.MPH) GaugeScale(0f, 180f, 20f, 4) else GaugeScale(0f, 300f, 30f, 3)

fun speedScaleMax(s: AppSettings) = if (s.units.speed == SpeedUnit.MPH) 180f else 300f

val TachScale = GaugeScale(0f, 8000f, 1000f, 5, redFrom = 6500f, labelDivisor = 1000f)

/** Edge chevrons that switch to the previous/next layout. */
@Composable
fun EdgeArrow(left: Boolean, onClick: (() -> Unit)?, width: Dp = 22.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.width(width).fillMaxHeight()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).testTag(if (left) "dash_prev" else "dash_next") else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (onClick != null) Icon(if (left) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight, if (left) "Layout anterior" else "Próximo layout", tint = p.textFaint)
    }
}

/** Sparkline of a history window. */
@Composable
fun Sparkline(points: List<HistoryPoint>, color: Color, min: Float, max: Float, windowMs: Long, nowMs: Long, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 1..3) drawLine(p.border.copy(alpha = 0.5f), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f)
        if (points.size < 2) return@Canvas
        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = w * (1f - (nowMs - pt.t).toFloat() / windowMs)
            val y = h * (1f - ((pt.v - min) / (max - min)).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.3f), style = Stroke(6f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}
