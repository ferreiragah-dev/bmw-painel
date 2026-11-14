package com.bmwcockpit.app.ui.gauges

import android.graphics.Paint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bmwcockpit.app.ui.theme.CockpitPalette
import com.bmwcockpit.app.ui.theme.CondensedTypeface
import com.bmwcockpit.app.ui.theme.LocalPalette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Visual configuration of a round instrument. */
data class GaugeScale(
    val min: Float,
    val max: Float,
    val majorStep: Float,
    val minorPerMajor: Int,
    /** Value from which ticks/labels turn red and the red band is drawn (null = none). */
    val redFrom: Float? = null,
    /** Divides the value for the printed scale numbers (e.g. 1000 for ×1000 rpm). */
    val labelDivisor: Float = 1f,
    val startAngle: Float = 135f,
    val sweep: Float = 270f,
)

/**
 * Round instrument drawn entirely with Canvas: metallic bezel, face, glowing progress arc, red zone, major
 * and minor ticks, numbers and an animated floating needle. [value] null (unavailable or stale) parks the
 * needle at the minimum and dims the arc, while [center] shows "—".
 */
@Composable
fun RoundGauge(
    value: Float?,
    scale: GaugeScale,
    modifier: Modifier = Modifier,
    palette: CockpitPalette = LocalPalette.current,
    arcColor: Color = palette.accent,
    arcColorEnd: Color = palette.accentBright,
    needleColor: Color = palette.needle,
    alert: Boolean = false,
    bezel: Boolean = true,
    showNumbers: Boolean = true,
    description: String = "",
    center: @Composable BoxScope.(diameterPx: Float) -> Unit = {},
) {
    val target = (value ?: scale.min).coerceIn(scale.min, scale.max)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val available = value != null
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = CondensedTypeface } }

    BoxWithConstraints(
        modifier.aspectRatio(1f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val d = minOf(constraints.maxWidth, constraints.maxHeight).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            drawGauge(animated, available, scale, palette, arcColor, arcColorEnd, needleColor, alert, bezel, showNumbers, textPaint)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { center(d) }
    }
}

private fun angleFor(v: Float, s: GaugeScale): Float = s.startAngle + (v - s.min) / (s.max - s.min) * s.sweep

private fun DrawScope.drawGauge(
    value: Float,
    available: Boolean,
    s: GaugeScale,
    p: CockpitPalette,
    arc: Color,
    arcEnd: Color,
    needle: Color,
    alert: Boolean,
    bezel: Boolean,
    showNumbers: Boolean,
    textPaint: Paint,
) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)

    // Bezel: brushed-metal ring + chrome highlight
    if (bezel) {
        drawCircle(
            Brush.sweepGradient(
                listOf(p.metalDark, p.metalLight, p.metalDark, p.metalLight.copy(alpha = 0.7f), p.metalDark, p.metalLight, p.metalDark),
                center = c,
            ),
            radius = r, center = c,
        )
        drawCircle(Color.Black, radius = r * 0.955f, center = c)
        drawCircle(Color.White.copy(alpha = 0.10f), radius = r * 0.95f, center = c, style = Stroke(r * 0.006f))
    }
    // Face
    val faceR = if (bezel) r * 0.94f else r
    drawCircle(
        Brush.radialGradient(listOf(p.surfaceHigh, p.surface.copy(alpha = 0.95f), Color.Black), center = c, radius = faceR),
        radius = faceR, center = c,
    )
    if (p.carbon) drawCarbonWeave(c, faceR)

    val trackR = r * 0.86f
    val arcTopLeft = Offset(c.x - trackR, c.y - trackR)
    val arcSize = Size(trackR * 2, trackR * 2)
    val trackW = r * 0.035f
    // Dim track
    drawArc(arc.copy(alpha = 0.12f), s.startAngle, s.sweep, false, arcTopLeft, arcSize, style = Stroke(trackW, cap = StrokeCap.Butt))

    // Progress arc with glow
    val progress = (value - s.min) / (s.max - s.min) * s.sweep
    if (available && progress > 0.2f) {
        val a = if (alert) p.danger else arc
        // Rotate so the sweep gradient starts at the arc start.
        rotate(s.startAngle, c) {
            drawArc(
                Brush.sweepGradient(listOf(a.copy(alpha = 0.35f), a, arcEnd), center = c),
                0f, progress, false, arcTopLeft, arcSize, style = Stroke(trackW * 2.6f, cap = StrokeCap.Butt), alpha = 0.22f,
            )
            drawArc(
                Brush.sweepGradient(listOf(a.copy(alpha = 0.6f), a, arcEnd), center = c),
                0f, progress, false, arcTopLeft, arcSize, style = Stroke(trackW, cap = StrokeCap.Butt),
            )
        }
    }

    // Red zone band
    s.redFrom?.let { red ->
        val ra = angleFor(red, s)
        val bandR = r * 0.79f
        drawArc(
            p.redZone.copy(alpha = 0.9f), ra, s.startAngle + s.sweep - ra, false,
            Offset(c.x - bandR, c.y - bandR), Size(bandR * 2, bandR * 2), style = Stroke(r * 0.045f),
        )
    }

    // Ticks
    val majors = ((s.max - s.min) / s.majorStep).roundToInt()
    val minorStep = s.majorStep / s.minorPerMajor
    val totalMinor = majors * s.minorPerMajor
    for (i in 0..totalMinor) {
        val v = s.min + i * minorStep
        val ang = Math.toRadians(angleFor(v, s).toDouble())
        val major = i % s.minorPerMajor == 0
        val inRed = s.redFrom != null && v >= s.redFrom - 0.001f
        val inner = if (major) r * 0.73f else r * 0.79f
        val outer = r * 0.865f
        val col = when {
            inRed -> p.redZone
            major -> p.text
            else -> p.textDim.copy(alpha = 0.7f)
        }
        drawLine(
            col,
            Offset(c.x + cos(ang).toFloat() * inner, c.y + sin(ang).toFloat() * inner),
            Offset(c.x + cos(ang).toFloat() * outer, c.y + sin(ang).toFloat() * outer),
            strokeWidth = if (major) r * 0.022f else r * 0.009f,
            cap = StrokeCap.Butt,
        )
        if (major && showNumbers) {
            val lr = r * 0.60f
            val label = (v / s.labelDivisor).let { if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(it) }
            textPaint.textSize = r * (if (label.length >= 3) 0.115f else 0.14f)
            textPaint.color = (if (inRed) p.redZone else p.text).toArgb()
            val tx = c.x + cos(ang).toFloat() * lr
            val ty = c.y + sin(ang).toFloat() * lr - (textPaint.ascent() + textPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, textPaint)
        }
    }

    // Floating needle: leaves the centre free for the digital readout.
    val na = angleFor(value, s)
    val nCol = if (available) needle else p.textFaint
    rotate(na, c) {
        val tip = r * 0.885f
        val base = r * 0.40f
        val w = r * 0.032f
        val path = Path().apply {
            moveTo(c.x + base, c.y - w)
            lineTo(c.x + tip, c.y - w * 0.18f)
            lineTo(c.x + tip, c.y + w * 0.18f)
            lineTo(c.x + base, c.y + w)
            close()
        }
        if (available) {
            drawPath(path, nCol.copy(alpha = 0.25f), style = Stroke(w * 2.2f))
        }
        drawPath(path, Brush.horizontalGradient(listOf(nCol.copy(alpha = 0.55f), nCol, Color.White.copy(alpha = 0.9f)), startX = c.x + base, endX = c.x + tip))
    }
    // Inner ring around the digital readout
    drawCircle(p.border.copy(alpha = 0.8f), radius = r * 0.36f, center = c, style = Stroke(r * 0.008f))
}

private fun DrawScope.drawCarbonWeave(c: Offset, radius: Float) {
    val step = radius / 14f
    val col = Color.White.copy(alpha = 0.025f)
    var x = c.x - radius
    while (x < c.x + radius) {
        drawLine(col, Offset(x, c.y - radius), Offset(x + radius, c.y + radius), strokeWidth = step * 0.45f)
        drawLine(col, Offset(x + radius, c.y - radius), Offset(x, c.y + radius), strokeWidth = step * 0.45f)
        x += step
    }
}
