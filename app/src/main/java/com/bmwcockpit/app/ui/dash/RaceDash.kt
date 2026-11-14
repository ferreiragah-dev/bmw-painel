package com.bmwcockpit.app.ui.dash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.ui.common.AlertLevel
import com.bmwcockpit.app.ui.common.alertLevel
import com.bmwcockpit.app.ui.common.alertLimitText
import com.bmwcockpit.app.ui.common.originBadge
import com.bmwcockpit.app.ui.common.sensorIcon
import com.bmwcockpit.app.ui.common.view
import com.bmwcockpit.app.ui.gauges.GearIndicator
import com.bmwcockpit.app.ui.gauges.MStripes
import com.bmwcockpit.app.ui.gauges.RoundGauge
import com.bmwcockpit.app.ui.gauges.alertColor
import com.bmwcockpit.app.ui.gauges.edgeColor
import com.bmwcockpit.app.ui.gauges.pxToSp
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette

/** Race Dashboard: central red tachometer with shift lights, four angular sensor cards on each side. */
@Composable
fun RaceDash(ctx: DashContext, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("dash_race")) {
        val portrait = maxHeight > maxWidth * 1.05f
        if (portrait) RacePortrait(ctx, maxWidth, maxHeight) else RaceLandscape(ctx, maxWidth, maxHeight)
    }
}

private fun arrowShape(pointRight: Boolean, k: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val kk = minOf(k, w * 0.3f)
    if (pointRight) {
        moveTo(0f, 0f); lineTo(w - kk, 0f); lineTo(w, h / 2); lineTo(w - kk, h); lineTo(0f, h); close()
    } else {
        moveTo(kk, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(kk, h); lineTo(0f, h / 2); close()
    }
}

@Composable
private fun RaceCard(ctx: DashContext, sensor: SensorId, left: Boolean, index: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val v = ctx.snap.view(sensor, ctx.settings.units)
    val level = alertLevel(sensor, v.raw, ctx.settings.alerts)
    val edge = edgeColor(level, p.hot)
    val flash = alertColor(level)
    BoxWithConstraints(modifier) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val k = h * 0.35f
        val shape = arrowShape(pointRight = left, k = k)
        Box(
            Modifier.fillMaxSize().clip(shape)
                .background(Brush.horizontalGradient(if (left) listOf(Color.Black, p.surfaceHigh) else listOf(p.surfaceHigh, Color.Black)))
                .then(if (!ctx.thumbnail && ctx.onCardTap != null) Modifier.clickable { ctx.onCardTap.invoke(CardSlot.Race(left, index)) } else Modifier)
                .testTag("race_${if (left) "l" else "r"}$index"),
        )
        Canvas(Modifier.fillMaxSize()) {
            val kk = minOf(k, size.width * 0.3f)
            val path = Path().apply {
                if (left) { moveTo(size.width - kk, 0f); lineTo(size.width, size.height / 2); lineTo(size.width - kk, size.height) }
                else { moveTo(kk, 0f); lineTo(0f, size.height / 2); lineTo(kk, size.height) }
            }
            drawPath(path, edge.copy(alpha = 0.35f), style = Stroke(10f))
            drawPath(path, edge, style = Stroke(3f))
            val y = size.height - 1f
            drawLine(Brush.horizontalGradient(if (left) listOf(Color.Transparent, edge) else listOf(edge, Color.Transparent)), Offset(0f, y), Offset(size.width, y), 2f)
            drawRect(p.border.copy(alpha = 0.6f), Offset.Zero, Size(size.width, size.height), style = Stroke(1f))
        }
        val iconPx = minOf(h * 0.42f, w * 0.16f)
        val valuePx = minOf(h * 0.40f, w * 0.17f)
        val labelPx = minOf(h * 0.17f, w * 0.075f)
        Row(
            Modifier.fillMaxSize().padding(start = if (left) 10.dp else with(LocalDensity.current) { (k * 0.8f).toDp() }, end = if (left) with(LocalDensity.current) { (k * 0.8f).toDp() } else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(sensorIcon(sensor), null, tint = flash ?: p.textDim, modifier = Modifier.size(with(LocalDensity.current) { iconPx.toDp() }))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sensor.shortLabel, color = p.textDim, fontSize = pxToSp(labelPx), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    originBadge(v.origin)?.let { Text(" $it", color = p.textFaint, fontSize = pxToSp(labelPx * 0.75f)) }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(v.text, color = if (v.raw == null) p.textFaint else (flash ?: p.text), fontSize = pxToSp(valuePx), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Text(v.unit, color = p.textDim, fontSize = pxToSp(labelPx), modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
                }
                alertLimitText(sensor, ctx.settings.alerts, ctx.settings.units)?.let {
                    Text(it, color = p.textFaint, fontSize = pxToSp(labelPx * 0.72f), maxLines = 1)
                }
            }
        }
    }
}

/** Shift-light LED strip: green → amber → red, flashing above the configured shift RPM. */
@Composable
private fun ShiftLights(rpm: Float?, shiftRpm: Float, modifier: Modifier) {
    val p = LocalPalette.current
    val flash = alertColor(if (rpm != null && rpm >= shiftRpm) AlertLevel.CRITICAL else AlertLevel.NONE)
    Canvas(modifier) {
        val n = 15
        val start = shiftRpm - 2800f
        val gap = size.width * 0.012f
        val segW = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val threshold = start + i * (2800f / n)
            val on = rpm != null && rpm >= threshold
            val base = when {
                i < 6 -> Color(0xFF2BD96B)
                i < 11 -> Color(0xFFFFB020)
                else -> p.hot
            }
            val c = if (flash != null) flash else if (on) base else base.copy(alpha = 0.12f)
            val x = i * (segW + gap)
            drawRoundRect(c, Offset(x, 0f), Size(segW, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 3))
            if (on) drawRoundRect(c.copy(alpha = 0.25f), Offset(x - 2, -2f), Size(segW + 4, size.height + 4), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
    }
}

@Composable
private fun RaceTach(ctx: DashContext, d: Dp) {
    val p = LocalPalette.current
    val v = ctx.snap.view(SensorId.RPM, ctx.settings.units)
    val alert = alertLevel(SensorId.RPM, v.raw, ctx.settings.alerts) == AlertLevel.CRITICAL
    RoundGauge(
        v.raw, TachScale, Modifier.size(d).testTag("race_tach"),
        arcColor = p.hot, arcColorEnd = Color(0xFFFF7A45), needleColor = p.needle, alert = alert,
        description = "Conta-giros ${v.text} rpm",
    ) { dp ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v.text, color = if (v.raw == null) p.textFaint else if (alert) p.danger else p.text, fontSize = pxToSp(dp * 0.16f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
            Text("RPM", color = p.textDim, fontSize = pxToSp(dp * 0.05f))
            Spacer(Modifier.height(with(LocalDensity.current) { (dp * 0.05f).toDp() }))
            GearIndicator(ctx.snap.value(SensorId.GEAR), ctx.settings.profile, dp * 0.2f, onProfileClick = if (ctx.thumbnail) null else ctx.onProfileTap)
        }
    }
}

@Composable
private fun Title(px: Float) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Flag, null, tint = p.text, modifier = Modifier.size(with(LocalDensity.current) { (px * 1.1f).toDp() }))
        Spacer(Modifier.width(6.dp))
        Text("Race Dashboard", color = p.text, fontSize = pxToSp(px), fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RaceLandscape(ctx: DashContext, w: Dp, h: Dp) {
    val density = LocalDensity.current
    val arrowW = if (ctx.onPrev != null && !ctx.thumbnail) 22.dp else 2.dp
    val titleH = (h * 0.08f).coerceIn(18.dp, 34.dp)
    val d = minOf(h - titleH - 8.dp, (w - arrowW * 2) * 0.42f)
    val sideW = ((w - arrowW * 2 - d) / 2 - 8.dp).coerceAtLeast(60.dp)
    val s = ctx.settings
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        EdgeArrow(true, if (ctx.thumbnail) null else ctx.onPrev, arrowW)
        Column(Modifier.width(sideW).fillMaxHeight().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.raceLeft.forEachIndexed { i, sensor -> RaceCard(ctx, sensor, true, i, Modifier.fillMaxWidth().weight(1f)) }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(titleH), contentAlignment = Alignment.Center) { Title(with(density) { (titleH * 0.55f).toPx() }) }
            ShiftLights(ctx.snap.value(SensorId.RPM), s.alerts.shiftRpm, Modifier.width(d * 0.62f).height((titleH * 0.28f).coerceAtLeast(5.dp)))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { RaceTach(ctx, d - titleH * 0.3f) }
            MStripes(6.dp)
        }
        Column(Modifier.width(sideW).fillMaxHeight().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.raceRight.forEachIndexed { i, sensor -> RaceCard(ctx, sensor, false, i, Modifier.fillMaxWidth().weight(1f)) }
        }
        EdgeArrow(false, if (ctx.thumbnail) null else ctx.onNext, arrowW)
    }
}

@Composable
private fun RacePortrait(ctx: DashContext, w: Dp, h: Dp) {
    val density = LocalDensity.current
    val d = minOf(w - 24.dp, h * 0.45f)
    val s = ctx.settings
    Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Title(with(density) { 16.dp.toPx() })
        ShiftLights(ctx.snap.value(SensorId.RPM), s.alerts.shiftRpm, Modifier.width(d * 0.7f).height(8.dp))
        RaceTach(ctx, d)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.raceLeft.forEachIndexed { i, sensor -> RaceCard(ctx, sensor, true, i, Modifier.fillMaxWidth().weight(1f)) }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.raceRight.forEachIndexed { i, sensor -> RaceCard(ctx, sensor, false, i, Modifier.fillMaxWidth().weight(1f)) }
            }
        }
    }
}
