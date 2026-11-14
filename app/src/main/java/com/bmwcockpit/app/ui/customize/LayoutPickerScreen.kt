package com.bmwcockpit.app.ui.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bmwcockpit.app.prefs.DashLayout
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.dash.DashFor
import com.bmwcockpit.app.ui.dash.rememberDashContext
import com.bmwcockpit.app.ui.go
import com.bmwcockpit.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * "Multitelas": layout selector. Each miniature is the real dashboard composable rendered at a fixed virtual
 * size and scaled down, fed by the live telemetry.
 */
@Composable
fun LayoutPickerScreen(vm: MainViewModel, nav: NavHostController) {
    val p = LocalPalette.current
    val ctx = rememberDashContext(vm, thumbnail = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val layouts = DashLayout.entries
    Column(Modifier.fillMaxSize()) {
        CustomizeTabs(nav, Routes.LAYOUTS)
        Text("Selecione um Painel", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val portrait = maxHeight > maxWidth
            val cardW = if (portrait) maxWidth * 0.80f else ((maxWidth - 96.dp) / 3.6f)
            val thumbH = minOf(cardW * 0.56f, maxHeight - 56.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0)) } }, Modifier.testTag("layouts_prev")) {
                    Icon(Icons.Filled.ChevronLeft, "Anterior", tint = p.text, modifier = Modifier.size(32.dp))
                }
                LazyRow(Modifier.weight(1f), state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    itemsIndexed(layouts) { _, l ->
                        val selected = ctx.settings.layout == l
                        Column(
                            Modifier
                                .width(cardW)
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.surface)
                                .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.border, RoundedCornerShape(10.dp))
                                .clickable { vm.setLayout(l); nav.go(Routes.DASH) }
                                .padding(6.dp)
                                .testTag("layout_${l.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(thumbH).clip(RoundedCornerShape(6.dp)).background(p.background), contentAlignment = Alignment.Center) {
                                Thumbnail(l, ctx, cardW - 12.dp, thumbH)
                                if (selected) Icon(Icons.Filled.CheckCircle, "Selecionado", tint = p.accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(l.label, color = if (selected) p.text else p.textDim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                IconButton({ scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(layouts.size - 1)) } }, Modifier.testTag("layouts_next")) {
                    Icon(Icons.Filled.ChevronRight, "Próximo", tint = p.text, modifier = Modifier.size(32.dp))
                }
            }
        }
        val first by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        Row(Modifier.align(Alignment.CenterHorizontally).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            layouts.indices.forEach { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == first) p.accent else p.textFaint))
            }
        }
    }
}

@Composable
private fun Thumbnail(layout: DashLayout, ctx: com.bmwcockpit.app.ui.dash.DashContext, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    // Render at a virtual head-unit size and scale down so the miniature keeps the real proportions.
    val virtualW = 1024.dp
    val virtualH = virtualW * (h.value / w.value)
    val scale = w.value / virtualW.value
    Box(Modifier.size(w, h), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(virtualW, virtualH).graphicsLayer { scaleX = scale; scaleY = scale }) {
            DashFor(layout, ctx, Modifier.fillMaxSize())
        }
    }
}
