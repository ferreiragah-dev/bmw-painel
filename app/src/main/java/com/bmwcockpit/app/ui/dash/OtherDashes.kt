package com.bmwcockpit.app.ui.dash

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.prefs.EditorLayout
import com.bmwcockpit.app.prefs.EditorWidget
import com.bmwcockpit.app.prefs.WidgetStyle
import com.bmwcockpit.app.ui.common.AlertLevel
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.alertLevel
import com.bmwcockpit.app.ui.common.alertLimitText
import com.bmwcockpit.app.ui.common.chartColor
import com.bmwcockpit.app.ui.common.convert
import com.bmwcockpit.app.ui.common.displayRange
import com.bmwcockpit.app.ui.common.view
import com.bmwcockpit.app.ui.gauges.BarWidget
import com.bmwcockpit.app.ui.gauges.DigitalReadout
import com.bmwcockpit.app.ui.gauges.GaugeScale
import com.bmwcockpit.app.ui.gauges.GearIndicator
import com.bmwcockpit.app.ui.gauges.MBadge
import com.bmwcockpit.app.ui.gauges.RoundGauge
import com.bmwcockpit.app.ui.gauges.alertColor
import com.bmwcockpit.app.ui.gauges.pxToSp
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette

// ------------------------------------------------------------------------------------------- GT

@Composable
private fun SmallGauge(ctx: DashContext, sensor: SensorId, scale: GaugeScale, d: Dp, tag: String) {
    val p = LocalPalette.current
    val u = ctx.settings.units
    val v = ctx.snap.view(sensor, u)
    val shown = v.raw?.let { convert(sensor, it, u) }
    val level = alertLevel(sensor, v.raw, ctx.settings.alerts)
    RoundGauge(shown, scale, Modifier.size(d).testTag(tag), alert = level == AlertLevel.CRITICAL, description = "${sensor.label} ${v.text}") { dp ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sensor.shortLabel, color = p.textDim, fontSize = pxToSp(dp * 0.055f), maxLines = 1)
            Text(v.text, color = if (v.raw == null) p.textFaint else if (level == AlertLevel.CRITICAL) p.danger else p.text, fontSize = pxToSp(dp * 0.17f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
            Text(v.unit, color = p.textDim, fontSize = pxToSp(dp * 0.055f))
        }
    }
}

private fun scaleFor(sensor: SensorId, ctx: DashContext): GaugeScale {
    val (mn, mx) = displayRange(sensor, ctx.settings.units)
    return when (sensor) {
        SensorId.SPEED -> speedScale(ctx.settings)
        SensorId.RPM -> TachScale
        SensorId.BOOST -> {
            val step = (mx - mn) / 6f
            GaugeScale(mn, mx, step, 5, redFrom = convert(sensor, ctx.settings.alerts.boostMax, ctx.settings.units), labelDivisor = 1f)
        }
        SensorId.OIL_TEMP, SensorId.COOLANT_TEMP, SensorId.IAT -> {
            val red = when (sensor) {
                SensorId.OIL_TEMP -> ctx.settings.alerts.oilTempMax
                SensorId.COOLANT_TEMP -> ctx.settings.alerts.coolantMax
                else -> ctx.settings.alerts.iatMax
            }
            GaugeScale(mn, mx, (mx - mn) / 5f, 5, redFrom = convert(sensor, red, ctx.settings.units))
        }
        else -> GaugeScale(mn, mx, (mx - mn) / 5f, 4)
    }
}

/** GT Telemetry: three blue round instruments and live 60 s trend strips. */
@Composable
fun GtDash(ctx: DashContext, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("dash_gt")) {
        val portrait = maxHeight > maxWidth * 1.05f
        val w = maxWidth
        val h = maxHeight
        val trendH = if (portrait) h * 0.30f else (h * 0.30f).coerceIn(70.dp, 200.dp)
        val gaugesH = h - trendH - 12.dp
        val dSide = if (portrait) minOf(w * 0.30f, gaugesH * 0.5f) else minOf(gaugesH * 0.78f, w * 0.27f)
        val dCenter = if (portrait) minOf(w * 0.40f, gaugesH * 0.6f) else minOf(gaugesH, w * 0.34f)
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(gaugesH), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!ctx.thumbnail && ctx.onPrev != null) EdgeArrow(true, ctx.onPrev)
                SmallGauge(ctx, SensorId.BOOST, scaleFor(SensorId.BOOST, ctx), dSide, "gt_boost")
                Box(contentAlignment = Alignment.BottomCenter) {
                    SmallGauge(ctx, SensorId.SPEED, scaleFor(SensorId.SPEED, ctx), dCenter, "gt_speed")
                    GearIndicator(ctx.snap.value(SensorId.GEAR), ctx.settings.profile, with(LocalDensity.current) { (dCenter * 0.16f).toPx() }, showProfile = false, modifier = Modifier.offset(y = dCenter * 0.02f))
                }
                SmallGauge(ctx, SensorId.OIL_TEMP, scaleFor(SensorId.OIL_TEMP, ctx), dSide, "gt_oil")
                if (!ctx.thumbnail && ctx.onNext != null) EdgeArrow(false, ctx.onNext)
            }
            Row(Modifier.fillMaxWidth().height(trendH), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (sensor in listOf(SensorId.RPM, SensorId.BOOST, SensorId.COOLANT_TEMP, SensorId.MODULE_VOLTAGE).take(if (portrait) 2 else 4)) {
                    TrendPanel(ctx, sensor, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TrendPanel(ctx: DashContext, sensor: SensorId, modifier: Modifier) {
    val p = LocalPalette.current
    val u = ctx.settings.units
    val v = ctx.snap.view(sensor, u)
    val window = 60_000L
    val pts = if (ctx.thumbnail) emptyList() else ctx.history(sensor, window)
    Panel(modifier, padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(sensor.shortLabel, color = p.textDim, fontSize = pxToSp(with(LocalDensity.current) { 12.dp.toPx() }), modifier = Modifier.weight(1f), maxLines = 1)
                Text("${v.text} ${v.unit}", color = if (v.raw == null) p.textFaint else p.text, fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, fontSize = pxToSp(with(LocalDensity.current) { 16.dp.toPx() }), maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Sparkline(pts, chartColor(sensor), sensor.min, sensor.max, window, ctx.snap.nowMs, Modifier.fillMaxWidth().weight(1f))
            Text("últimos 60 s", color = p.textFaint, fontSize = pxToSp(with(LocalDensity.current) { 9.dp.toPx() }))
        }
    }
}

// -------------------------------------------------------------------------------------- Minimal

/** Minimal: shift bar, huge digital speed, gear and two secondary values. */
@Composable
fun MinimalDash(ctx: DashContext, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val u = ctx.settings.units
    BoxWithConstraints(modifier.fillMaxSize().testTag("dash_minimal")) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val portrait = h > w * 1.05f
        val speed = ctx.snap.view(SensorId.SPEED, u)
        val rpm = ctx.snap.view(SensorId.RPM, u)
        val rpmLevel = alertLevel(SensorId.RPM, rpm.raw, ctx.settings.alerts)
        val flash = alertColor(rpmLevel)
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            // RPM bar
            Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (h * 0.05f).coerceIn(8f, 40f).toDp() })) {
                val n = 40
                val gap = size.width * 0.006f
                val sw = (size.width - gap * (n - 1)) / n
                val frac = (rpm.raw ?: 0f) / 8000f
                for (i in 0 until n) {
                    val on = i < frac * n
                    val pos = i / n.toFloat()
                    val base = when {
                        pos >= ctx.settings.alerts.shiftRpm / 8000f -> p.hot
                        pos >= 0.6f -> p.warning
                        else -> p.accent
                    }
                    val c = if (flash != null && on) flash else if (on) base else base.copy(alpha = 0.10f)
                    drawRoundRect(c, Offset(i * (sw + gap), 0f), Size(sw, size.height), CornerRadius(2f, 2f))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("${rpm.text} rpm", color = flash ?: p.textDim, fontSize = pxToSp(h * 0.04f), fontFamily = CondensedFamily, modifier = Modifier.weight(1f))
                MBadge("", stripeHeight = with(LocalDensity.current) { (h * 0.025f).toDp() }, fontSize = pxToSp(h * 0.03f))
            }
            if (portrait) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    BigSpeed(speed.text, speed.unit, speed.raw == null, w * 0.33f)
                    GearIndicator(ctx.snap.value(SensorId.GEAR), ctx.settings.profile, h * 0.10f, onProfileClick = if (ctx.thumbnail) null else ctx.onProfileTap)
                }
                Row(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (h * 0.14f).toDp() }), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinimalSide(ctx, SensorId.BOOST, Modifier.weight(1f).fillMaxHeight())
                    MinimalSide(ctx, SensorId.COOLANT_TEMP, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (!ctx.thumbnail && ctx.onPrev != null) EdgeArrow(true, ctx.onPrev)
                    Box(Modifier.weight(0.9f), contentAlignment = Alignment.Center) {
                        GearIndicator(ctx.snap.value(SensorId.GEAR), ctx.settings.profile, h * 0.26f, onProfileClick = if (ctx.thumbnail) null else ctx.onProfileTap)
                    }
                    Box(Modifier.weight(2f), contentAlignment = Alignment.Center) { BigSpeed(speed.text, speed.unit, speed.raw == null, minOf(h * 0.48f, w * 0.20f)) }
                    Column(Modifier.weight(0.9f).fillMaxHeight().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MinimalSide(ctx, SensorId.BOOST, Modifier.fillMaxWidth().weight(1f))
                        MinimalSide(ctx, SensorId.COOLANT_TEMP, Modifier.fillMaxWidth().weight(1f))
                    }
                    if (!ctx.thumbnail && ctx.onNext != null) EdgeArrow(false, ctx.onNext)
                }
            }
            val src = when (ctx.snap.source) { SourceKind.DEMO -> "simulação"; SourceKind.VEHICLE -> "veículo"; SourceKind.NONE -> "sem fonte" }
            val trip = ctx.snap.view(SensorId.TRIP, u)
            Text("Trip ${trip.text} ${trip.unit} · fonte: $src", color = p.textFaint, fontSize = pxToSp(h * 0.03f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun BigSpeed(text: String, unit: String, missing: Boolean, px: Float) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (missing) p.textFaint else p.text, fontSize = pxToSp(px), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("minimal_speed"))
        Text(unit, color = p.accent, fontSize = pxToSp(px * 0.16f))
    }
}

@Composable
private fun MinimalSide(ctx: DashContext, sensor: SensorId, modifier: Modifier) {
    val v = ctx.snap.view(sensor, ctx.settings.units)
    DigitalReadout(v, modifier, alert = alertLevel(sensor, v.raw, ctx.settings.alerts), limitText = alertLimitText(sensor, ctx.settings.alerts, ctx.settings.units))
}

// ------------------------------------------------------------------------------------- Custom

/** Renders one editor widget with its style. Used both by the editor canvas and the "Meu Painel" layout. */
@Composable
fun WidgetView(ctx: DashContext, w: EditorWidget, modifier: Modifier = Modifier) {
    val u = ctx.settings.units
    val v = ctx.snap.view(w.sensor, u)
    val color = Color(w.color.toInt())
    val level = alertLevel(w.sensor, v.raw, ctx.settings.alerts)
    val p = LocalPalette.current
    when (w.style) {
        WidgetStyle.CIRCULAR -> BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            val shown = v.raw?.let { convert(w.sensor, it, u) }
            RoundGauge(shown, scaleFor(w.sensor, ctx), Modifier.size(d), arcColor = color, arcColorEnd = color.copy(alpha = 0.6f).compositeOverWhite(), alert = level == AlertLevel.CRITICAL, showNumbers = d > 90.dp) { dp ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(w.sensor.shortLabel, color = p.textDim, fontSize = pxToSp(dp * 0.06f), maxLines = 1)
                    Text(v.text, color = if (v.raw == null) p.textFaint else p.text, fontSize = pxToSp(dp * 0.17f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
                    Text(v.unit, color = p.textDim, fontSize = pxToSp(dp * 0.06f))
                }
            }
        }
        WidgetStyle.DIGITAL -> DigitalReadout(v, modifier, color = color, alert = level)
        WidgetStyle.BAR -> BarWidget(v, color, modifier, alert = level)
    }
}

private fun Color.compositeOverWhite(): Color = Color(
    red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha), 1f,
)

/** "Meu Painel": the composition created in the editor. */
@Composable
fun CustomDash(ctx: DashContext, layout: EditorLayout, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().padding(6.dp).testTag("dash_custom")) {
        val W = maxWidth
        val H = maxHeight
        for (w in layout.widgets) {
            WidgetView(ctx, w, Modifier.offset(W * w.x, H * w.y).size(W * w.w, H * w.h))
        }
        if (!ctx.thumbnail) {
            Row(Modifier.fillMaxHeight().align(Alignment.CenterStart)) { if (ctx.onPrev != null) EdgeArrow(true, ctx.onPrev) }
            Row(Modifier.fillMaxHeight().align(Alignment.CenterEnd)) { if (ctx.onNext != null) EdgeArrow(false, ctx.onNext) }
        }
    }
}
