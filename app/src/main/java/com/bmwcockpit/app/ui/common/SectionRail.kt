package com.bmwcockpit.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmwcockpit.app.ui.theme.LocalPalette

data class RailItem(val route: String, val label: String, val icon: ImageVector)

/** Left section rail (landscape) or horizontal chips (portrait), as in the reference's sub-menus. */
@Composable
fun SectionScaffold(items: List<RailItem>, selected: String, onSelect: (String) -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth || maxWidth < 600.dp
        if (portrait) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { it ->
                        val sel = it.route == selected
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) p.accent.copy(alpha = 0.3f) else p.surface)
                                .border(1.dp, if (sel) p.accent else p.border, RoundedCornerShape(16.dp))
                                .clickable { onSelect(it.route) }.heightIn(min = 40.dp).padding(horizontal = 10.dp)
                                .testTag("rail_${it.route}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(it.icon, null, tint = if (sel) Color.White else p.textDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it.label, color = if (sel) Color.White else p.textDim, fontSize = 13.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)) { content() }
            }
        } else {
            val railW = (maxWidth * 0.17f).coerceIn(150.dp, 210.dp)
            Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.width(railW).fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState()).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.forEach { it ->
                        val sel = it.route == selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.7f), p.accent.copy(alpha = 0.25f))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { onSelect(it.route) }.padding(horizontal = 10.dp)
                                .testTag("rail_${it.route}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(it.icon, null, tint = if (sel) Color.White else p.textDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it.label, color = if (sel) Color.White else p.text, fontSize = 13.sp)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}
