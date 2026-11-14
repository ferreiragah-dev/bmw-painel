package com.bmwcockpit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.bmwcockpit.app.prefs.BrightnessMode
import com.bmwcockpit.app.prefs.ThemeId
import java.util.Calendar

@Immutable
data class CockpitPalette(
    val id: ThemeId,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderBright: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    /** Main highlight: progress arcs, selected items, glows. */
    val accent: Color,
    /** Secondary highlight (gradient end of arcs). */
    val accentBright: Color,
    /** Needles. */
    val needle: Color,
    val redZone: Color,
    val danger: Color,
    val warning: Color,
    val ok: Color,
    val metalLight: Color,
    val metalDark: Color,
    val carbon: Boolean = false,
    val lightMode: Boolean = false,
) {
    /** Second hue used by the tachometer / race elements. */
    val hot: Color get() = redZone
}

object Palettes {
    private val bmwRed = Color(0xFFE5262B)

    fun base(id: ThemeId): CockpitPalette = when (id) {
        ThemeId.M_PERFORMANCE -> CockpitPalette(
            id, Color(0xFF000000), Color(0xFF0A0E14), Color(0xFF111824), Color(0xFF1C2532), Color(0xFF2C3A4E),
            Color(0xFFF4F7FB), Color(0xFF9AA6B8), Color(0xFF5A6578),
            accent = Color(0xFF1C8CFF), accentBright = Color(0xFF63C8FF), needle = Color(0xFFFF2B2B),
            redZone = bmwRed, danger = Color(0xFFFF3B30), warning = Color(0xFFFFA726), ok = Color(0xFF34D27B),
            metalLight = Color(0xFFB8C2CF), metalDark = Color(0xFF2A313B),
        )
        ThemeId.RACE_RED -> CockpitPalette(
            id, Color(0xFF000000), Color(0xFF100809), Color(0xFF1A0C0E), Color(0xFF3A1416), Color(0xFF6A1E22),
            Color(0xFFFFF5F5), Color(0xFFC8A2A4), Color(0xFF7A5658),
            accent = Color(0xFFFF2B2B), accentBright = Color(0xFFFF7A45), needle = Color(0xFFFF3A2A),
            redZone = Color(0xFFFF1A1A), danger = Color(0xFFFF453A), warning = Color(0xFFFFB020), ok = Color(0xFF34D27B),
            metalLight = Color(0xFFC9B6B6), metalDark = Color(0xFF3A2526),
        )
        ThemeId.DARK_CARBON -> CockpitPalette(
            id, Color(0xFF050506), Color(0xFF0E0F11), Color(0xFF16181B), Color(0xFF26292E), Color(0xFF3C4148),
            Color(0xFFF2F2F2), Color(0xFFA7ABB2), Color(0xFF656A72),
            accent = Color(0xFFDCE1E8), accentBright = Color(0xFFFFFFFF), needle = Color(0xFFFF5A1F),
            redZone = Color(0xFFFF4A1F), danger = Color(0xFFFF453A), warning = Color(0xFFFFB020), ok = Color(0xFF7BE0A6),
            metalLight = Color(0xFFD0D3D8), metalDark = Color(0xFF2B2E33), carbon = true,
        )
        ThemeId.BLUE_ICE -> CockpitPalette(
            id, Color(0xFF00040A), Color(0xFF06101C), Color(0xFF0B1828), Color(0xFF14304A), Color(0xFF22507A),
            Color(0xFFF0FBFF), Color(0xFF9CC4DA), Color(0xFF52758C),
            accent = Color(0xFF39C6FF), accentBright = Color(0xFFB5EEFF), needle = Color(0xFF7FE3FF),
            redZone = Color(0xFFFF4D6A), danger = Color(0xFFFF4D6A), warning = Color(0xFFFFC14D), ok = Color(0xFF5FF0C0),
            metalLight = Color(0xFFBFD9E8), metalDark = Color(0xFF1C2E3E),
        )
        ThemeId.AMBER -> CockpitPalette(
            id, Color(0xFF050300), Color(0xFF110C04), Color(0xFF1A1307), Color(0xFF3A2A0E), Color(0xFF6A4A12),
            Color(0xFFFFF4E0), Color(0xFFD9B57A), Color(0xFF8A6A3A),
            accent = Color(0xFFFFB020), accentBright = Color(0xFFFFE08A), needle = Color(0xFFFF8A00),
            redZone = Color(0xFFFF4A1F), danger = Color(0xFFFF4A1F), warning = Color(0xFFFFD54F), ok = Color(0xFFB8E05F),
            metalLight = Color(0xFFD8C7A8), metalDark = Color(0xFF3A2E1C),
        )
    }

    fun resolve(id: ThemeId, accent: Long?, mode: BrightnessMode, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): CockpitPalette {
        var p = base(id)
        if (accent != null) {
            val c = Color(accent.toInt())
            p = p.copy(accent = c, accentBright = lerpWhite(c, 0.45f))
        }
        val light = when (mode) {
            BrightnessMode.LIGHT -> true
            BrightnessMode.DARK -> false
            BrightnessMode.AUTO -> hour in 7..17
        }
        if (light) {
            // "Claro" = day mode: graphite surfaces with higher luminance and stronger borders, keeping
            // white text for contrast (a cockpit never turns white).
            p = p.copy(
                background = Color(0xFF1B2028), surface = Color(0xFF262D37), surfaceHigh = Color(0xFF303845),
                border = Color(0xFF3D4756), borderBright = Color(0xFF55637A), textDim = Color(0xFFC0C8D4),
                textFaint = Color(0xFF8792A3), lightMode = true,
            )
        }
        return p
    }

    private fun lerpWhite(c: Color, f: Float) = Color(
        c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, 1f,
    )
}

val LocalPalette = staticCompositionLocalOf { Palettes.base(ThemeId.M_PERFORMANCE) }

/** Condensed family for numbers (Roboto Condensed is present on every Android device as "sans-serif-condensed"). */
val CondensedFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)),
)
val CondensedTypeface: android.graphics.Typeface =
    android.graphics.Typeface.create(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD), android.graphics.Typeface.BOLD_ITALIC)

@Composable
fun CockpitTheme(palette: CockpitPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color.White,
        background = palette.background,
        surface = palette.surface,
        onSurface = palette.text,
        onBackground = palette.text,
        surfaceVariant = palette.surfaceHigh,
        outline = palette.border,
        error = palette.danger,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
