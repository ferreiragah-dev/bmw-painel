package com.bmwcockpit.app.ui.gauges

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

val MLightBlue = Color(0xFF4FB5E8)
val MDarkBlue = Color(0xFF2B3990)
val MRed = Color(0xFFE5262B)

/** Roundel drawn with Canvas (outer ring with lettering, quartered centre). */
@Composable
fun Roundel(size: Dp, modifier: Modifier = Modifier) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE3EA), Color(0xFF6C7682)), c, r), r, c)
        drawCircle(Color(0xFF0B0D10), r * 0.92f, c)
        val inner = r * 0.56f
        val rect = Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner)
        val blue = Color(0xFF1C69D4)
        drawArc(Color.White, 180f, 90f, true, rect.topLeft, rect.size)
        drawArc(blue, 270f, 90f, true, rect.topLeft, rect.size)
        drawArc(Color.White, 0f, 90f, true, rect.topLeft, rect.size)
        drawArc(blue, 90f, 90f, true, rect.topLeft, rect.size)
        drawCircle(Color(0xFFB8C2CF), inner, c, style = Stroke(r * 0.05f))
        // Lettering along the top of the ring
        val textR = r * 0.74f
        paint.textSize = r * 0.30f
        val path = android.graphics.Path().apply {
            addArc(c.x - textR, c.y - textR, c.x + textR, c.y + textR, 200f, 140f)
        }
        val fm = paint.fontMetrics
        drawContext.canvas.nativeCanvas.drawTextOnPath("BMW", path, 0f, -(fm.ascent + fm.descent) / 2f, paint)
    }
}

/** Three slanted M stripes. */
@Composable
fun MStripes(height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = height * 1.9f, height = height)) {
        val h = this.size.height
        val w = this.size.width / 3.4f
        val slant = h * 0.35f
        listOf(MLightBlue, MDarkBlue, MRed).forEachIndexed { i, col ->
            val x = i * w * 1.12f
            val p = Path().apply {
                moveTo(x + slant, 0f); lineTo(x + slant + w, 0f); lineTo(x + w, h); lineTo(x, h); close()
            }
            drawPath(p, col)
        }
    }
}

/** "M" stripes + italic label, e.g. the "M Performance" badge. */
@Composable
fun MBadge(text: String, stripeHeight: Dp, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Color.White) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MStripes(stripeHeight)
        Spacer(Modifier.width(stripeHeight * 0.4f))
        Text(text, color = color, fontSize = fontSize, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
