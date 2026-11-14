package com.bmwcockpit.app.ui.gauges

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.AlertThresholds
import com.bmwcockpit.app.prefs.DriveProfile
import com.bmwcockpit.app.ui.common.AlertLevel
import com.bmwcockpit.app.ui.common.DASH
import com.bmwcockpit.app.ui.common.SensorView
import com.bmwcockpit.app.ui.common.alertLevel
import com.bmwcockpit.app.ui.common.originBadge
import com.bmwcockpit.app.ui.common.sensorColor
import com.bmwcockpit.app.ui.common.sensorIcon
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette

/** Px → sp independent of the user's font scale (instrument text is sized by the instrument). */
@Composable
fun pxToSp(px: Float): TextUnit = with(LocalDensity.current) { (px / fontScale).toSp() }

/** Animated horizontal scale bar. */
@Composable
fun ScaleBar(fraction: Float, color: Color, modifier: Modifier = Modifier, alert: Boolean = false) {
    val p = LocalPalette.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    val c = if (alert) p.danger else color
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(h / 2, h / 2))
        if (f > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(c.copy(alpha = 0.55f), c), endX = size.width * f),
                size = Size(size.width * f, h), cornerRadius = CornerRadius(h / 2, h / 2),
            )
            drawCircle(c.copy(alpha = 0.35f), radius = h * 1.6f, center = Offset(size.width * f, h / 2))
        }
    }
}

/** Flashing modifier colour for alerts. */
@Composable
fun alertColor(level: AlertLevel): Color? {
    val p = LocalPalette.current
    if (level == AlertLevel.NONE) return null
    val t = rememberInfiniteTransition(label = "alert")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "a")
    return (if (level == AlertLevel.CRITICAL) p.danger else p.warning).copy(alpha = a)
}

/**
 * Sensor card of the M Performance bottom bar: icon + name, value + unit, scale bar.
 * Tapping opens the sensor picker (configurable card).
 */
@Composable
fun SensorCard(
    v: SensorView,
    alerts: AlertThresholds,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
) {
    val p = LocalPalette.current
    val level = alertLevel(v.sensor, v.raw, alerts)
    val ac = alertColor(level)
    val shape = RoundedCornerShape(6.dp)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.surfaceHigh, p.surface, Color.Black.copy(alpha = 0.6f))))
            .border(1.dp, ac ?: p.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val valuePx = minOf(h * 0.36f, w * 0.20f)
        val labelPx = minOf(h * 0.16f, w * 0.085f)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sensorIcon(v.sensor), null, tint = p.textDim, modifier = Modifier.size(with(LocalDensity.current) { (labelPx * 1.25f).toDp() }))
                Spacer(Modifier.width(4.dp))
                Text(v.sensor.shortLabel, color = p.textDim, fontSize = pxToSp(labelPx), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                originBadge(v.origin)?.let { Text(it, color = p.textFaint, fontSize = pxToSp(labelPx * 0.75f)) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    v.text, color = if (v.raw == null) p.textFaint else (ac ?: p.text), fontSize = pxToSp(valuePx),
                    fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(v.unit, color = p.textDim, fontSize = pxToSp(labelPx * 0.95f), maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
            }
            ScaleBar(v.fraction, sensorColor(v.sensor), Modifier.fillMaxWidth().height(with(LocalDensity.current) { (h * 0.045f).coerceAtLeast(3f).toDp() }), alert = level == AlertLevel.CRITICAL)
        }
    }
}

/** Digital readout block: label, large value, unit; optional limit text. */
@Composable
fun DigitalReadout(
    v: SensorView,
    modifier: Modifier = Modifier,
    color: Color? = null,
    alert: AlertLevel = AlertLevel.NONE,
    limitText: String? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    showBar: Boolean = true,
) {
    val p = LocalPalette.current
    val ac = alertColor(alert)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.surfaceHigh, p.surface)))
            .border(1.dp, ac ?: (color ?: p.border).copy(alpha = 0.7f), shape),
    ) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val valuePx = minOf(h * 0.42f, w * 0.26f)
        val labelPx = minOf(h * 0.15f, w * 0.08f).coerceAtLeast(18f)
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sensorIcon(v.sensor), null, tint = color ?: p.textDim, modifier = Modifier.size(with(LocalDensity.current) { (labelPx * 1.2f).toDp() }))
                Spacer(Modifier.width(4.dp))
                Text(v.sensor.shortLabel, color = p.textDim, fontSize = pxToSp(labelPx), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                originBadge(v.origin)?.let { Text(it, color = p.textFaint, fontSize = pxToSp(labelPx * 0.75f)) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(v.text, color = if (v.raw == null) p.textFaint else (ac ?: p.text), fontSize = pxToSp(valuePx), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(v.unit, color = p.textDim, fontSize = pxToSp(labelPx), modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                if (limitText != null) Text(limitText, color = p.textFaint, fontSize = pxToSp(labelPx * 0.8f), maxLines = 1)
            }
            if (showBar) ScaleBar(v.fraction, color ?: sensorColor(v.sensor), Modifier.fillMaxWidth().height(4.dp), alert = alert == AlertLevel.CRITICAL)
        }
    }
}

/** Gear indicator ("D4") with the demonstrative drive-profile label below. */
@Composable
fun GearIndicator(
    gear: Float?,
    profile: DriveProfile,
    heightPx: Float,
    modifier: Modifier = Modifier,
    onProfileClick: (() -> Unit)? = null,
    showProfile: Boolean = true,
) {
    val p = LocalPalette.current
    val profileColor = when (profile) {
        DriveProfile.COMFORT -> p.accent
        DriveProfile.SPORT -> p.warning
        DriveProfile.SPORT_PLUS -> p.redZone
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val text = gear?.let { "D${it.toInt()}" } ?: DASH
        Text(
            text, color = if (gear == null) p.textFaint else p.text, fontSize = pxToSp(heightPx * 0.62f),
            fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.testTag("gear"),
        )
        if (gear == null) Text("marcha indisponível", color = p.textFaint, fontSize = pxToSp(heightPx * 0.12f), maxLines = 1)
        if (showProfile) {
            Text(
                profile.label, color = profileColor, fontSize = pxToSp(heightPx * 0.2f), fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.ExtraBold, maxLines = 1,
                modifier = Modifier
                    .then(if (onProfileClick != null) Modifier.clickable(onClick = onProfileClick) else Modifier)
                    .padding(horizontal = 6.dp),
            )
            Text("perfil visual", color = p.textFaint, fontSize = pxToSp(heightPx * 0.09f), maxLines = 1)
        }
    }
}

/** Animated colour helper for the race cards' edge glow. */
@Composable
fun edgeColor(level: AlertLevel, base: Color): Color {
    val p = LocalPalette.current
    val target = when (level) {
        AlertLevel.CRITICAL -> p.danger
        AlertLevel.WARN -> p.warning
        AlertLevel.NONE -> base
    }
    val c by animateColorAsState(target, label = "edge")
    return c
}

/** Bar-style widget used by the editor and Minimal layout. */
@Composable
fun BarWidget(v: SensorView, color: Color, modifier: Modifier = Modifier, alert: AlertLevel = AlertLevel.NONE) {
    val p = LocalPalette.current
    val ac = alertColor(alert)
    val shape = RoundedCornerShape(6.dp)
    BoxWithConstraints(modifier.clip(shape).background(p.surface).border(1.dp, ac ?: color.copy(alpha = 0.5f), shape)) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val txt = minOf(h * 0.32f, w * 0.12f)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(v.sensor.shortLabel, color = p.textDim, fontSize = pxToSp(txt * 0.55f), maxLines = 1, modifier = Modifier.weight(1f))
                Text(v.text, color = if (v.raw == null) p.textFaint else (ac ?: p.text), fontSize = pxToSp(txt), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic, maxLines = 1)
                Spacer(Modifier.width(3.dp))
                Text(v.unit, color = p.textDim, fontSize = pxToSp(txt * 0.5f), modifier = Modifier.padding(bottom = 2.dp))
            }
            ScaleBar(v.fraction, color, Modifier.fillMaxWidth().height(with(LocalDensity.current) { (h * 0.14f).coerceIn(4f, 40f).toDp() }), alert = alert == AlertLevel.CRITICAL)
        }
    }
}

@Composable
fun SensorIconBox(sensor: SensorId, tint: Color, sizePx: Float) {
    Box(Modifier.size(with(LocalDensity.current) { sizePx.toDp() }), contentAlignment = Alignment.Center) {
        Icon(sensorIcon(sensor), null, tint = tint, modifier = Modifier.fillMaxSize())
    }
}
