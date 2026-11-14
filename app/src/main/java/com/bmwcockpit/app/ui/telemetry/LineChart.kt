package com.bmwcockpit.app.ui.telemetry

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.Units
import com.bmwcockpit.app.ui.common.chartColor
import com.bmwcockpit.app.ui.common.convert
import com.bmwcockpit.app.ui.common.decimals
import com.bmwcockpit.app.ui.common.unitLabel
import com.bmwcockpit.app.ui.theme.CockpitPalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class AxisMode { SINGLE, DUAL, NORMALIZED }

data class AxisPlan(
    val mode: AxisMode,
    val leftUnit: String,
    val rightUnit: String?,
    val leftSensors: List<SensorId>,
    val rightSensors: List<SensorId>,
) {
    val title: String get() = when (mode) {
        AxisMode.SINGLE -> "Eixo: $leftUnit"
        AxisMode.DUAL -> "Eixo esquerdo: $leftUnit · direito: $rightUnit"
        AxisMode.NORMALIZED -> "Escala normalizada: % da faixa de cada sensor (unidades diferentes)"
    }
}

/** One shared axis per unit; two units → left/right axes; three or more → explicit normalization. */
fun planAxes(sensors: List<SensorId>, u: Units): AxisPlan {
    val groups = sensors.groupBy { unitLabel(it, u) }
    return when (groups.size) {
        0, 1 -> AxisPlan(AxisMode.SINGLE, groups.keys.firstOrNull() ?: "", null, sensors, emptyList())
        2 -> {
            val (a, b) = groups.entries.toList()
            AxisPlan(AxisMode.DUAL, a.key, b.key, a.value, b.value)
        }
        else -> AxisPlan(AxisMode.NORMALIZED, "%", null, sensors, emptyList())
    }
}

/** "Nice" axis bounds and step. */
fun niceRange(minV: Float, maxV: Float, ticks: Int = 5): Triple<Float, Float, Float> {
    var lo = minV
    var hi = maxV
    if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
    val raw = (hi - lo) / ticks
    val mag = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val norm = raw / mag
    val step = mag * when { norm < 1.5f -> 1f; norm < 3f -> 2f; norm < 7f -> 5f; else -> 10f }
    return Triple(floor(lo / step) * step, ceil(hi / step) * step, step)
}

private val PT = java.util.Locale("pt", "BR")

private fun fmt(v: Float, step: Float): String = when {
    step >= 1f -> String.format(PT, "%.0f", v)
    step >= 0.1f -> String.format(PT, "%.1f", v)
    else -> String.format(PT, "%.2f", v)
}

/**
 * Time-series chart drawn with Canvas: grid, Y axis (or two), X axis in seconds/minutes before the end,
 * lines with glow and a value tag at the right end of each series.
 */
@Composable
fun LineChart(frame: ChartFrame, units: Units, palette: CockpitPalette, modifier: Modifier = Modifier) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f } }
    val sensors = frame.series.map { it.sensor }
    val plan = planAxes(sensors, units)
    Canvas(modifier) {
        val textH = size.minDimension.coerceAtMost(900f) * 0.035f + 14f
        paint.textSize = textH
        val leftPad = textH * 3.4f
        val rightPad = if (plan.mode == AxisMode.DUAL) textH * 3.4f else textH * 4.6f
        val topPad = textH * 0.8f
        val bottomPad = textH * 1.8f
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 10 || plotH <= 10) return@Canvas
        fun xOf(t: Long) = leftPad + plotW * ((t - frame.startMs).toFloat() / (frame.endMs - frame.startMs).toFloat())

        // Ranges
        fun rangeOf(list: List<SensorId>): Triple<Float, Float, Float> {
            val vals = frame.series.filter { it.sensor in list }.flatMap { s -> s.points.map { convert(s.sensor, it.v, units) } }
            return if (vals.isEmpty()) {
                val s = list.firstOrNull()
                if (s == null) niceRange(0f, 1f) else niceRange(convert(s, s.min, units), convert(s, s.max, units))
            } else {
                val mn = vals.min(); val mx = vals.max()
                val pad = (mx - mn).coerceAtLeast(abs(mx) * 0.05f + 0.5f) * 0.1f
                niceRange(mn - pad, mx + pad)
            }
        }
        val left = if (plan.mode == AxisMode.NORMALIZED) Triple(0f, 100f, 20f) else rangeOf(plan.leftSensors)
        val right = if (plan.mode == AxisMode.DUAL) rangeOf(plan.rightSensors) else null

        // Grid + left axis labels
        paint.color = palette.textDim.toArgb()
        paint.textAlign = Paint.Align.RIGHT
        var v = left.first
        while (v <= left.second + left.third * 0.01f) {
            val y = topPad + plotH * (1f - (v - left.first) / (left.second - left.first))
            drawLine(palette.border.copy(alpha = 0.6f), Offset(leftPad, y), Offset(leftPad + plotW, y), 1f)
            drawContext.canvas.nativeCanvas.drawText(fmt(v, left.third), leftPad - textH * 0.4f, y + textH * 0.35f, paint)
            v += left.third
        }
        if (right != null) {
            paint.textAlign = Paint.Align.LEFT
            var rv = right.first
            while (rv <= right.second + right.third * 0.01f) {
                val y = topPad + plotH * (1f - (rv - right.first) / (right.second - right.first))
                drawContext.canvas.nativeCanvas.drawText(fmt(rv, right.third), leftPad + plotW + textH * 0.4f, y + textH * 0.35f, paint)
                rv += right.third
            }
        }
        // Axis units
        paint.textAlign = Paint.Align.LEFT
        paint.color = palette.textFaint.toArgb()
        drawContext.canvas.nativeCanvas.drawText(plan.leftUnit, 2f, topPad - textH * 0.1f + textH * 0.6f, paint)
        if (right != null) {
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(plan.rightUnit ?: "", size.width - 2f, topPad + textH * 0.5f, paint)
        }

        // X axis: 5 labels relative to the end ("Agora" when live)
        paint.textAlign = Paint.Align.CENTER
        paint.color = palette.textDim.toArgb()
        val span = frame.endMs - frame.startMs
        for (i in 0..5) {
            val t = frame.startMs + span * i / 5
            val x = xOf(t)
            drawLine(palette.border.copy(alpha = 0.35f), Offset(x, topPad), Offset(x, topPad + plotH), 1f)
            val ago = (frame.endMs - t) / 1000 + frame.panMs / 1000
            val label = when {
                i == 5 && !frame.paused -> "Agora"
                ago >= 60 -> "−${ago / 60} min${if (ago % 60 != 0L) " ${ago % 60}s" else ""}"
                else -> "−${ago}s"
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, topPad + plotH + textH * 1.3f, paint)
        }
        drawLine(palette.borderBright, Offset(leftPad, topPad + plotH), Offset(leftPad + plotW, topPad + plotH), 1.5f)

        // Series
        val tags = ArrayList<Triple<Color, Float, String>>()
        var tagX = leftPad
        for (s in frame.series) {
            if (s.points.isEmpty()) continue
            val color = chartColor(s.sensor)
            val onRight = right != null && s.sensor in plan.rightSensors
            val r = if (onRight) right!! else left
            fun yOf(raw: Float): Float {
                val value = if (plan.mode == AxisMode.NORMALIZED) (raw - s.sensor.min) / (s.sensor.max - s.sensor.min) * 100f else convert(s.sensor, raw, units)
                return topPad + plotH * (1f - ((value - r.first) / (r.second - r.first)).coerceIn(-0.02f, 1.02f))
            }
            val path = Path()
            var lastT = -1L
            s.points.forEachIndexed { i, pt ->
                val x = xOf(pt.t)
                val y = yOf(pt.v)
                // Break the line across gaps (disconnection) instead of drawing a fake straight segment.
                if (i == 0 || pt.t - lastT > 3_000) path.moveTo(x, y) else path.lineTo(x, y)
                lastT = pt.t
            }
            drawPath(path, color.copy(alpha = 0.25f), style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = s.points.last()
            tags += Triple(color, yOf(last.v), String.format(PT, "%.${decimals(s.sensor, units)}f", convert(s.sensor, last.v, units)))
            tagX = maxOf(tagX, xOf(last.t))
        }
        // End-of-series value tags, pushed apart vertically so none hides another.
        val tagH = textH * 1.4f
        val placed = tags.sortedBy { it.second }.toMutableList()
        val ys = placed.map { it.second }.toMutableList()
        for (k in 1 until ys.size) if (ys[k] - ys[k - 1] < tagH + 2f) ys[k] = ys[k - 1] + tagH + 2f
        val overflow = (ys.lastOrNull() ?: 0f) + tagH / 2 - (topPad + plotH + textH)
        if (overflow > 0) for (k in ys.indices) ys[k] -= overflow
        paint.textAlign = Paint.Align.LEFT
        placed.forEachIndexed { k, (color, _, text) ->
            val tw = paint.measureText(text) + textH * 0.8f
            val tx = (tagX + 6f).coerceAtMost(size.width - tw - 2f)
            val ty = ys[k]
            drawRoundRect(color, Offset(tx, ty - tagH / 2), Size(tw, tagH), CornerRadius(textH * 0.3f))
            paint.color = Color.Black.toArgb()
            drawContext.canvas.nativeCanvas.drawText(text, tx + textH * 0.4f, ty + textH * 0.35f, paint)
        }
    }
}
