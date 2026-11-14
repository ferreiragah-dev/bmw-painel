package com.bmwcockpit.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmwcockpit.app.ui.theme.LocalPalette

/** Dark panel with a subtle metallic border, used by every card in the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    borderColor: Color? = null,
    highlight: Boolean = false,
    padding: PaddingValues = PaddingValues(10.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val border = borderColor ?: if (highlight) p.accent else p.border
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.surfaceHigh, p.surface)))
            .border(BorderStroke(if (highlight) 1.5.dp else 1.dp, Brush.verticalGradient(listOf(border, border.copy(alpha = 0.45f)))), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

enum class BtnKind { PRIMARY, SECONDARY, DANGER, SUCCESS }

/** Cockpit button. Always has an action; disabled buttons are drawn dimmed and ignore taps. */
@Composable
fun CockpitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.SECONDARY,
    enabled: Boolean = true,
    tag: String? = null,
    compact: Boolean = false,
) {
    val p = LocalPalette.current
    val (bg, fg, border) = when (kind) {
        BtnKind.PRIMARY -> Triple(Brush.verticalGradient(listOf(p.accent, p.accent.copy(alpha = 0.75f))), Color.White, p.accentBright)
        BtnKind.SUCCESS -> Triple(Brush.verticalGradient(listOf(Color(0xFF26C24A), Color(0xFF15992F))), Color.White, Color(0xFF5BE07A))
        BtnKind.DANGER -> Triple(Brush.verticalGradient(listOf(Color(0xFF5A1416), Color(0xFF34090A))), Color(0xFFFFD6D6), p.danger)
        BtnKind.SECONDARY -> Triple(Brush.verticalGradient(listOf(p.surfaceHigh, p.surface)), p.text, p.borderBright)
    }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .heightIn(min = if (compact) 36.dp else 44.dp)
            .clip(shape)
            .background(bg, alpha = if (enabled) 1f else 0.4f)
            .border(1.dp, border.copy(alpha = if (enabled) 0.9f else 0.3f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, tag: String? = null) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = p.textDim, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = Color.White, uncheckedTrackColor = p.surfaceHigh, uncheckedBorderColor = p.borderBright),
        )
    }
}

/** Segmented control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, icon: ((T) -> ImageVector?)? = null, tag: ((T) -> String)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.clip(shape).border(1.dp, p.borderBright, shape).background(p.surface)) {
        options.forEach { o ->
            val sel = o == selected
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .background(if (sel) Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.55f), p.accent.copy(alpha = 0.25f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .border(if (sel) 1.dp else 0.dp, if (sel) p.accent else Color.Transparent, shape)
                    .clickable { onSelect(o) }
                    .then(if (tag != null) Modifier.testTag(tag(o)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke(o)?.let { Icon(it, null, tint = if (sel) Color.White else p.textDim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(label(o), color = if (sel) Color.White else p.textDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, color: Color? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val c = color ?: p.accent
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) c.copy(alpha = 0.22f) else p.surface)
            .border(1.dp, if (selected) c else p.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) c else p.textFaint))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (selected) p.text else p.textDim, fontSize = 13.sp, maxLines = 1)
    }
}

/** Pulsing dot used by "recording" and "demo" indicators. */
@Composable
fun PulseDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPalette.current
    Text(text, color = color ?: p.textDim, fontSize = 12.sp, modifier = modifier, lineHeight = 16.sp)
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.textDim, fontSize = 11.sp)
        Text(value, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VerticalSpacer(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun ColumnScope.Divider() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
}
