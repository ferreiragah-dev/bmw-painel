package com.bmwcockpit.app.ui.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.bmwcockpit.app.ui.theme.CockpitPalette
import com.bmwcockpit.app.ui.theme.LocalPalette

/**
 * Stylised front view of a BMW 3 Series F30 drawn with paths (no bitmap): body, glasshouse, twin kidney
 * grille, headlights with LED "angel eye" rings, M-Sport bumper intakes and ground glow in the theme accent.
 * Design space is 200 × 110 units, scaled to fit the available size keeping the aspect ratio.
 */
@Composable
fun CarFront(modifier: Modifier = Modifier, palette: CockpitPalette = LocalPalette.current, lightsOn: Boolean = true) {
    Canvas(modifier) {
        val s = minOf(size.width / 200f, size.height / 110f)
        val dx = (size.width - 200f * s) / 2f
        val dy = (size.height - 110f * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawCar(palette, lightsOn)
        }
    }
}

private fun path(block: Path.() -> Unit) = Path().apply(block)

private fun DrawScope.drawCar(p: CockpitPalette, lightsOn: Boolean) {
    val glow = p.accent
    // Ground glow + shadow
    drawOval(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(100f, 102f), radius = 100f),
        topLeft = Offset(0f, 92f), size = Size(200f, 18f),
    )
    drawOval(Color.Black.copy(alpha = 0.75f), topLeft = Offset(12f, 96f), size = Size(176f, 9f))

    // Tyres
    for (x in listOf(13f, 165f)) {
        drawRoundRect(Color(0xFF07080A), Offset(x, 80f), Size(22f, 23f), CornerRadius(4f, 4f))
        drawLine(Color(0xFF2A2F36), Offset(x + 3f, 84f), Offset(x + 3f, 100f), 1.2f)
    }

    // Body
    val body = path {
        moveTo(11f, 94f)
        lineTo(8f, 72f)
        quadraticTo(9f, 58f, 16f, 53f)
        lineTo(34f, 45f)
        quadraticTo(64f, 36f, 100f, 35.5f)
        quadraticTo(136f, 36f, 166f, 45f)
        lineTo(184f, 53f)
        quadraticTo(191f, 58f, 192f, 72f)
        lineTo(189f, 94f)
        quadraticTo(100f, 100f, 11f, 94f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF4A5566), Color(0xFF283039), Color(0xFF12161C), Color(0xFF07090C)), startY = 35f, endY = 98f))
    drawPath(body, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.10f))))
    drawPath(body, p.metalLight.copy(alpha = 0.35f), style = Stroke(0.6f))

    // Glasshouse
    val glass = path {
        moveTo(50f, 39.5f)
        lineTo(64f, 15f)
        quadraticTo(100f, 9f, 136f, 15f)
        lineTo(150f, 39.5f)
        quadraticTo(100f, 35f, 50f, 39.5f)
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF2A3644), Color(0xFF0A1018), Color(0xFF03060A)), startY = 10f, endY = 40f))
    clipPath(glass) {
        drawLine(Color.White.copy(alpha = 0.14f), Offset(70f, 40f), Offset(92f, 10f), 5f)
        drawLine(Color.White.copy(alpha = 0.07f), Offset(80f, 40f), Offset(100f, 10f), 2f)
    }
    drawPath(glass, Color(0xFF59636F), style = Stroke(1.2f))
    // Roof
    drawPath(path { moveTo(64f, 15f); quadraticTo(100f, 6f, 136f, 15f) }, Color(0xFF8D98A6).copy(alpha = 0.6f), style = Stroke(1.4f))

    // Mirrors
    for (m in listOf(false, true)) {
        val mp = path {
            if (!m) { moveTo(40f, 35f); lineTo(51f, 33f); lineTo(52f, 41f); lineTo(42f, 42f); close() }
            else { moveTo(160f, 35f); lineTo(149f, 33f); lineTo(148f, 41f); lineTo(158f, 42f); close() }
        }
        drawPath(mp, Brush.verticalGradient(listOf(Color(0xFF5A6575), Color(0xFF1A1F26)), 33f, 42f))
    }

    // Hood character lines
    drawPath(path { moveTo(38f, 47f); quadraticTo(100f, 39f, 162f, 47f) }, Color.White.copy(alpha = 0.18f), style = Stroke(0.8f))
    drawPath(path { moveTo(82f, 41f); lineTo(78f, 51f) }, Color.White.copy(alpha = 0.12f), style = Stroke(0.7f))
    drawPath(path { moveTo(118f, 41f); lineTo(122f, 51f) }, Color.White.copy(alpha = 0.12f), style = Stroke(0.7f))

    // Headlights
    for (right in listOf(false, true)) {
        fun x(v: Float) = if (right) 200f - v else v
        val hl = path {
            moveTo(x(14f), 57f); lineTo(x(48f), 49f); lineTo(x(66f), 51f); lineTo(x(62f), 58f); lineTo(x(22f), 64f); close()
        }
        drawPath(hl, Brush.verticalGradient(listOf(Color(0xFF1C2430), Color(0xFF05070A)), 49f, 64f))
        drawPath(hl, Color(0xFFAAB4C0).copy(alpha = 0.7f), style = Stroke(0.7f))
        val ringCol = if (lightsOn) Color(0xFFE8F6FF) else Color(0xFF4A5560)
        for ((cx, cy, rr) in listOf(Triple(x(29f), 58.5f, 5.2f), Triple(x(44f), 55.5f, 4.6f))) {
            if (lightsOn) {
                drawCircle(glow.copy(alpha = 0.18f), rr + 3.5f, Offset(cx, cy))
                drawCircle(glow.copy(alpha = 0.35f), rr + 1.5f, Offset(cx, cy), style = Stroke(2.2f))
            }
            drawCircle(ringCol, rr, Offset(cx, cy), style = Stroke(1.1f))
            drawCircle(Color(0xFF0E141B), rr - 1.6f, Offset(cx, cy))
        }
        // LED eyebrow
        if (lightsOn) {
            drawLine(glow.copy(alpha = 0.5f), Offset(x(20f), 56.5f), Offset(x(60f), 50.5f), 2.2f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.9f), Offset(x(20f), 56.5f), Offset(x(60f), 50.5f), 0.6f, StrokeCap.Round)
        }
    }

    // Kidney grille
    for (left in listOf(true, false)) {
        val x0 = if (left) 79.5f else 102.5f
        val tl = Offset(x0, 50f)
        val sz = Size(18f, 17f)
        drawRoundRect(Color(0xFF040506), tl, sz, CornerRadius(5f, 6f))
        var sx = x0 + 2.4f
        while (sx < x0 + 17f) {
            drawLine(Color(0xFF6E7884).copy(alpha = 0.75f), Offset(sx, 51.5f), Offset(sx, 65.5f), 0.7f)
            sx += 2.3f
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFF2F5F8), Color(0xFF7D8894), Color(0xFFE0E6EC)), 50f, 67f),
            tl, sz, CornerRadius(5f, 6f), style = Stroke(1.6f),
        )
    }
    // Hood roundel
    drawCircle(Color(0xFFB8C2CF), 2.6f, Offset(100f, 45.5f))
    drawCircle(Color(0xFF1C69D4), 1.6f, Offset(100f, 45.5f))

    // Bumper intakes (M-Sport)
    val centre = path { moveTo(60f, 74f); lineTo(140f, 74f); lineTo(134f, 89f); lineTo(66f, 89f); close() }
    drawPath(centre, Color(0xFF030405))
    clipPath(centre) {
        var mx = 56f
        while (mx < 146f) {
            drawLine(Color(0xFF2A3038), Offset(mx, 74f), Offset(mx + 10f, 89f), 0.5f)
            drawLine(Color(0xFF2A3038), Offset(mx + 10f, 74f), Offset(mx, 89f), 0.5f)
            mx += 4f
        }
    }
    drawPath(centre, Color(0xFF59636F), style = Stroke(0.8f))
    for (right in listOf(false, true)) {
        fun x(v: Float) = if (right) 200f - v else v
        val side = path { moveTo(x(18f), 72f); lineTo(x(48f), 69f); lineTo(x(52f), 84f); lineTo(x(22f), 88f); close() }
        drawPath(side, Color(0xFF040506))
        drawPath(side, Color(0xFF59636F), style = Stroke(0.8f))
        drawLine(glow.copy(alpha = if (lightsOn) 0.7f else 0.2f), Offset(x(24f), 80f), Offset(x(44f), 77.5f), 1f, StrokeCap.Round)
    }
    // Lower lip
    drawPath(path { moveTo(22f, 93f); quadraticTo(100f, 98.5f, 178f, 93f) }, Color(0xFF8D98A6).copy(alpha = 0.35f), style = Stroke(0.8f))
}
