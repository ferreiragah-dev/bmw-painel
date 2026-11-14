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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.data.model.SourceKind
import com.bmwcockpit.app.ui.common.AlertLevel
import com.bmwcockpit.app.ui.common.alertLevel
import com.bmwcockpit.app.ui.common.view
import com.bmwcockpit.app.ui.gauges.CarFront
import com.bmwcockpit.app.ui.gauges.GearIndicator
import com.bmwcockpit.app.ui.gauges.MBadge
import com.bmwcockpit.app.ui.gauges.MStripes
import com.bmwcockpit.app.ui.gauges.RoundGauge
import com.bmwcockpit.app.ui.gauges.SensorCard
import com.bmwcockpit.app.ui.gauges.pxToSp
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette

/** M Performance: speedometer left, tachometer right, F30 + gear in the centre, 6 sensor cards and footer. */
@Composable
fun MPerformanceDash(ctx: DashContext, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("dash_m_performance")) {
        if (maxHeight > maxWidth * 1.05f) MPerfPortrait(ctx, maxWidth, maxHeight) else MPerfLandscape(ctx, maxWidth, maxHeight)
    }
}

@Composable
private fun SpeedGauge(ctx: DashContext, modifier: Modifier) {
    val p = LocalPalette.current
    val v = ctx.snap.view(SensorId.SPEED, ctx.settings.units)
    val shown = v.raw?.let { com.bmwcockpit.app.ui.common.convert(SensorId.SPEED, it, ctx.settings.units) }
    RoundGauge(shown, speedScale(ctx.settings), modifier.testTag("gauge_speed"), description = "Velocímetro ${v.text} ${v.unit}") { d ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MStripes(with(LocalDensity.current) { (d * 0.035f).toDp() })
            Text(v.text, color = if (v.raw == null) p.textFaint else p.text, fontSize = pxToSp(d * 0.20f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1, modifier = Modifier.testTag("speed_value"))
            Text(v.unit, color = p.textDim, fontSize = pxToSp(d * 0.055f))
        }
    }
}

@Composable
private fun TachGauge(ctx: DashContext, modifier: Modifier) {
    val p = LocalPalette.current
    val v = ctx.snap.view(SensorId.RPM, ctx.settings.units)
    val alert = alertLevel(SensorId.RPM, v.raw, ctx.settings.alerts) == AlertLevel.CRITICAL
    RoundGauge(
        v.raw, TachScale, modifier.testTag("gauge_rpm"),
        arcColor = p.hot, arcColorEnd = Color(0xFFFF8A65), alert = alert, description = "Conta-giros ${v.text} rpm",
    ) { d ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("x1000 rpm", color = p.textFaint, fontSize = pxToSp(d * 0.04f))
            Text(v.text, color = if (v.raw == null) p.textFaint else if (alert) p.danger else p.text, fontSize = pxToSp(d * 0.17f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1, modifier = Modifier.testTag("rpm_value"))
            Text("RPM", color = p.textDim, fontSize = pxToSp(d * 0.055f))
        }
    }
}

@Composable
private fun CenterBlock(ctx: DashContext, modifier: Modifier, gearPx: Float) {
    val p = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        MBadge("Performance", stripeHeight = with(LocalDensity.current) { (gearPx * 0.13f).toDp() }, fontSize = pxToSp(gearPx * 0.17f))
        CarFront(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp))
        Text("F30 320i ActiveFlex · Stage 2", color = p.textFaint, fontSize = pxToSp(gearPx * 0.085f), maxLines = 1)
        GearIndicator(
            ctx.snap.value(SensorId.GEAR), ctx.settings.profile, gearPx,
            onProfileClick = if (ctx.thumbnail) null else ctx.onProfileTap,
        )
    }
}

@Composable
private fun CardsRow(ctx: DashContext, modifier: Modifier, columns: Int = 6) {
    val sensors = ctx.settings.cardSensors
    val rows = sensors.chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { i, s ->
                    val idx = r * columns + i
                    SensorCard(
                        ctx.snap.view(s, ctx.settings.units), ctx.settings.alerts,
                        Modifier.weight(1f).fillMaxHeight(),
                        onClick = if (ctx.thumbnail || ctx.onCardTap == null) null else ({ ctx.onCardTap.invoke(CardSlot.Bottom(idx)) }),
                        tag = "card_$idx",
                    )
                }
            }
        }
    }
}

@Composable
private fun Footer(ctx: DashContext, modifier: Modifier, textPx: Float) {
    val p = LocalPalette.current
    val u = ctx.settings.units
    val trip = ctx.snap.view(SensorId.TRIP, u)
    val pw = ctx.snap.view(SensorId.POWER_EST, u)
    val tq = ctx.snap.view(SensorId.TORQUE_EST, u)
    val source = when (ctx.snap.source) {
        SourceKind.DEMO -> "Fonte: simulação"
        SourceKind.VEHICLE -> "Fonte: veículo (OBD2)"
        SourceKind.NONE -> "Sem fonte de dados"
    }
    val fs = pxToSp(textPx)
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Trip ${trip.text} ${trip.unit}", color = p.textDim, fontSize = fs, maxLines = 1)
        Spacer(Modifier.width(16.dp))
        Text(source, color = p.textFaint, fontSize = fs, maxLines = 1)
        Spacer(Modifier.weight(1f))
        MStripes(with(LocalDensity.current) { (textPx * 0.8f).toDp() })
        Spacer(Modifier.weight(1f))
        Text("Potência (est.) ${pw.text} ${pw.unit}", color = p.textDim, fontSize = fs, maxLines = 1)
        Spacer(Modifier.width(16.dp))
        Text("Torque (est.) ${tq.text} ${tq.unit}", color = p.textDim, fontSize = fs, maxLines = 1)
    }
}

/** Decorative glow line linking both instruments, as in the reference cluster. */
@Composable
private fun WingGlow(modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val y = size.height * 0.80f
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, p.accent.copy(alpha = 0.7f), Color.Transparent, p.hot.copy(alpha = 0.7f), Color.Transparent)),
            Offset(0f, y), Offset(size.width, y), strokeWidth = 2f,
        )
        drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.16f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.5f), size.height * 0.6f), size.height * 0.6f, Offset(size.width * 0.2f, size.height * 0.5f))
        drawCircle(Brush.radialGradient(listOf(p.hot.copy(alpha = 0.14f), Color.Transparent), Offset(size.width * 0.8f, size.height * 0.5f), size.height * 0.6f), size.height * 0.6f, Offset(size.width * 0.8f, size.height * 0.5f))
    }
}

@Composable
private fun MPerfLandscape(ctx: DashContext, w: Dp, h: Dp) {
    val density = LocalDensity.current
    val footerH = (h * 0.065f).coerceIn(18.dp, 30.dp)
    val cardsH = (h * 0.17f).coerceIn(52.dp, 112.dp)
    val gap = 6.dp
    val gaugeAreaH = h - footerH - cardsH - gap * 3
    val arrowW = if (ctx.onPrev != null && !ctx.thumbnail) 22.dp else 4.dp
    val d = minOf(gaugeAreaH, (w - arrowW * 2) * 0.355f)
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Box(Modifier.fillMaxWidth().height(gaugeAreaH)) {
            WingGlow(Modifier.fillMaxSize())
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                EdgeArrow(true, if (ctx.thumbnail) null else ctx.onPrev, arrowW)
                SpeedGauge(ctx, Modifier.size(d))
                CenterBlock(ctx, Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp), with(density) { (gaugeAreaH * 0.30f).toPx() })
                TachGauge(ctx, Modifier.size(d))
                EdgeArrow(false, if (ctx.thumbnail) null else ctx.onNext, arrowW)
            }
        }
        CardsRow(ctx, Modifier.fillMaxWidth().height(cardsH))
        Footer(ctx, Modifier.fillMaxWidth().height(footerH), with(density) { (footerH * 0.5f).toPx() })
    }
}

@Composable
private fun MPerfPortrait(ctx: DashContext, w: Dp, h: Dp) {
    val density = LocalDensity.current
    val gap = 6.dp
    val d = minOf((w - 16.dp) / 2, h * 0.30f)
    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SpeedGauge(ctx, Modifier.size(d))
            TachGauge(ctx, Modifier.size(d))
        }
        CenterBlock(ctx, Modifier.fillMaxWidth().weight(1f), with(density) { (h * 0.10f).toPx() })
        CardsRow(ctx, Modifier.fillMaxWidth().height((h * 0.22f).coerceIn(110.dp, 200.dp)), columns = 3)
        Footer(ctx, Modifier.fillMaxWidth().height(22.dp), with(density) { 10.dp.toPx() })
    }
}
