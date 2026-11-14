package com.bmwcockpit.app.ui.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.EditorDefaults
import com.bmwcockpit.app.prefs.EditorWidget
import com.bmwcockpit.app.prefs.WidgetStyle
import com.bmwcockpit.app.ui.CockpitDialog
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.SensorPickerDialog
import com.bmwcockpit.app.ui.common.BtnKind
import com.bmwcockpit.app.ui.common.CockpitButton
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.sensorIcon
import com.bmwcockpit.app.ui.dash.WidgetView
import com.bmwcockpit.app.ui.dash.rememberDashContext
import com.bmwcockpit.app.ui.go
import com.bmwcockpit.app.ui.theme.LocalPalette

val EditorColors = listOf(0xFF1C8CFFL, 0xFFE5262BL, 0xFF8A4BFFL, 0xFF2FD27AL, 0xFFFFB020L, 0xFFFF6A00L, 0xFFFFFFFFL)

private val librarySensors = (SensorId.cardSelectable + SensorId.chartable).distinct()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    vm: MainViewModel,
    nav: NavHostController,
    // Activity-scoped: an unsaved draft survives switching tabs and is only dropped by Cancelar/Descartar.
    ed: EditorViewModel = viewModel(androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity),
) {
    val st by ed.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var saveAsDialog by rememberSaveable { mutableStateOf(false) }
    var loadDialog by rememberSaveable { mutableStateOf(false) }
    var sensorDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = st.dirty) { confirmLeave = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth
        val libW = (maxWidth * 0.17f).coerceIn(130.dp, 200.dp)
        val propW = (maxWidth * 0.21f).coerceIn(170.dp, 250.dp)
        Column(Modifier.fillMaxSize()) {
            CustomizeTabs(nav, Routes.EDITOR)
            if (portrait) {
                Column(Modifier.fillMaxWidth().weight(1f).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompositionArea(vm, ed, st, Modifier.fillMaxWidth().weight(1f))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        librarySensors.forEach { s -> LibraryChip(s) { ed.add(s) } }
                    }
                    Properties(ed, st, Modifier.fillMaxWidth().heightIn(max = 260.dp), onPickSensor = { sensorDialog = true })
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Library(ed, Modifier.width(libW).fillMaxHeight())
                    CompositionArea(vm, ed, st, Modifier.weight(1f).fillMaxHeight())
                    Properties(ed, st, Modifier.width(propW).fillMaxHeight(), onPickSensor = { sensorDialog = true })
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (st.dirty) Text("Alterações não salvas", color = p.warning, fontSize = 12.sp)
                CockpitButton("Cancelar", { ed.cancel() }, icon = Icons.Filled.Close, enabled = st.dirty, tag = "editor_cancel", compact = true)
                CockpitButton("Restaurar padrão", { ed.restoreDefault() }, icon = Icons.Filled.RestartAlt, tag = "editor_restore", compact = true)
                CockpitButton("Carregar", { loadDialog = true }, icon = Icons.Filled.FolderOpen, enabled = settings.savedLayouts.isNotEmpty(), tag = "editor_load", compact = true)
                CockpitButton("Salvar como…", { saveAsDialog = true }, icon = Icons.Filled.SaveAs, tag = "editor_saveas", compact = true)
                CockpitButton("Salvar", { ed.save() }, icon = Icons.Filled.Save, kind = BtnKind.PRIMARY, enabled = st.dirty, tag = "editor_save", compact = true)
                CockpitButton("Salvar e usar no painel", { ed.save(andUse = true); nav.go(Routes.DASH) }, icon = Icons.Filled.Check, kind = BtnKind.SUCCESS, tag = "editor_use", compact = true)
            }
        }
    }

    if (confirmLeave) {
        CockpitDialog("Descartar alterações?", { confirmLeave = false }) {
            InfoNote("O painel tem alterações não salvas. Sair descarta essas alterações.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Continuar editando", { confirmLeave = false })
                CockpitButton("Salvar e sair", { confirmLeave = false; ed.save(); nav.popBackStack() }, kind = BtnKind.PRIMARY)
                CockpitButton("Descartar", { confirmLeave = false; ed.cancel(); nav.popBackStack() }, kind = BtnKind.DANGER)
            }
        }
    }
    if (saveAsDialog) {
        var name by rememberSaveable { mutableStateOf(st.draft.name.takeIf { it != "Padrão" } ?: "") }
        CockpitDialog("Salvar layout com nome", { saveAsDialog = false }) {
            OutlinedTextField(
                name, { name = it.take(30) }, singleLine = true, label = { Text("Nome do layout") },
                modifier = Modifier.fillMaxWidth().testTag("saveas_name"),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = p.text, unfocusedTextColor = p.text, focusedBorderColor = p.accent, unfocusedBorderColor = p.border, focusedLabelColor = p.accent, unfocusedLabelColor = p.textDim, cursorColor = p.accent),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitButton("Cancelar", { saveAsDialog = false })
                CockpitButton("Salvar", { ed.saveAs(name); saveAsDialog = false }, kind = BtnKind.PRIMARY, enabled = name.isNotBlank(), tag = "saveas_confirm")
            }
        }
    }
    if (loadDialog) {
        CockpitDialog("Layouts salvos", { loadDialog = false }) {
            if (settings.savedLayouts.isEmpty()) InfoNote("Nenhum layout salvo ainda.")
            settings.savedLayouts.forEach { l ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.name}  ·  ${l.widgets.size} instrumentos", color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    CockpitButton("Abrir", { ed.load(l); loadDialog = false }, compact = true)
                    Spacer(Modifier.width(6.dp))
                    CockpitButton("Excluir", { ed.deleteSaved(l.name) }, icon = Icons.Filled.Delete, kind = BtnKind.DANGER, compact = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            CockpitButton("Fechar", { loadDialog = false })
        }
    }
    val sel = st.selected
    if (sensorDialog && sel != null) {
        val snap by vm.snapshot.collectAsStateWithLifecycle()
        SensorPickerDialog("Sensor do instrumento", sel.sensor, librarySensors, snap, { ed.setSensor(sel.id, it) }, { sensorDialog = false })
    }
}

@Composable
private fun LibraryChip(s: SensorId, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceHigh).border(1.dp, p.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(s.shortLabel, color = p.text, fontSize = 13.sp)
    }
}

@Composable
private fun Library(ed: EditorViewModel, modifier: Modifier) {
    val p = LocalPalette.current
    Panel(modifier, padding = PaddingValues(6.dp)) {
        Column(Modifier.fillMaxSize()) {
            Text("Adicionar sensor", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                librarySensors.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(6.dp)).clickable { ed.add(s) }.padding(horizontal = 6.dp).testTag("lib_${s.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sensorIcon(s), null, tint = p.textDim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.shortLabel, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Add, "Adicionar", tint = p.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompositionArea(vm: MainViewModel, ed: EditorViewModel, st: EditorViewModel.State, modifier: Modifier) {
    val p = LocalPalette.current
    val ctx = rememberDashContext(vm, thumbnail = true)
    Panel(modifier, padding = PaddingValues(0.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().testTag("editor_area").pointerInput(Unit) { detectTapGestures { ed.select(null) } },
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            // Dotted grid
            Canvas(Modifier.fillMaxSize()) {
                val step = size.minDimension / 12f
                var x = step
                while (x < size.width) {
                    var y = step
                    while (y < size.height) { drawCircle(p.border, 1.5f, Offset(x, y)); y += step }
                    x += step
                }
            }
            if (st.draft.widgets.isEmpty()) {
                Text("Toque em um sensor da biblioteca para adicionar um instrumento", color = p.textFaint, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            }
            for (w in st.draft.widgets) {
                androidx.compose.runtime.key(w.id) { EditableWidget(ctx, ed, w, w.id == st.selectedId, wPx, hPx, maxWidth, maxHeight) }
            }
        }
    }
}

@Composable
private fun EditableWidget(
    ctx: com.bmwcockpit.app.ui.dash.DashContext,
    ed: EditorViewModel,
    w: EditorWidget,
    selected: Boolean,
    wPx: Float,
    hPx: Float,
    W: androidx.compose.ui.unit.Dp,
    H: androidx.compose.ui.unit.Dp,
) {
    val p = LocalPalette.current
    val area by rememberUpdatedState(Pair(wPx, hPx))
    Box(
        Modifier
            .offset(W * w.x, H * w.y)
            .size(W * w.w, H * w.h)
            .pointerInput(w.id) {
                detectDragGestures(onDragStart = { ed.select(w.id) }) { change, drag ->
                    change.consume()
                    ed.move(w.id, drag.x / area.first, drag.y / area.second)
                }
            }
            .pointerInput(w.id) { detectTapGestures { ed.select(w.id) } }
            .testTag("widget_${w.id}"),
    ) {
        WidgetView(ctx, w, Modifier.fillMaxSize().padding(2.dp))
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, p.accent, RoundedCornerShape(4.dp)))
            for (a in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart)) {
                Box(Modifier.align(a).size(10.dp).background(p.accent))
            }
            // Resize handle (bottom-right): large touch target.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .pointerInput(w.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            ed.resize(w.id, drag.x / area.first, drag.y / area.second)
                        }
                    }
                    .testTag("resize_${w.id}"),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.OpenInFull, "Redimensionar", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Properties(ed: EditorViewModel, st: EditorViewModel.State, modifier: Modifier, onPickSensor: () -> Unit) {
    val p = LocalPalette.current
    val w = st.selected
    Panel(modifier, padding = PaddingValues(8.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Propriedades", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (w == null) {
                InfoNote("Selecione um instrumento na área de composição para editar. Arraste para mover e use a alça azul para redimensionar.")
                InfoNote("${st.draft.widgets.size} instrumento(s) · layout \"${st.draft.name}\"")
                return@Column
            }
            Text("Sensor", color = p.textDim, fontSize = 12.sp)
            CockpitButton(w.sensor.label, onPickSensor, icon = sensorIcon(w.sensor), modifier = Modifier.fillMaxWidth(), compact = true, tag = "prop_sensor")
            Text("Tamanho", color = p.textDim, fontSize = 12.sp)
            Slider(
                value = w.w, onValueChange = { ed.setScale(w.id, it) }, valueRange = EditorDefaults.MIN_SIZE..0.9f,
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.border),
                modifier = Modifier.testTag("prop_size"),
            )
            Text("Cor do display", color = p.textDim, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorColors.forEach { c ->
                    val sel = c == w.color
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(c.toInt()))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else p.border, CircleShape)
                            .clickable { ed.setColor(w.id, c) }
                            .testTag("color_${java.lang.Long.toHexString(c)}"),
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Filled.Check, null, tint = if (c == 0xFFFFFFFFL) Color.Black else Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            Text("Estilo", color = p.textDim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetStyle.entries.forEach { s ->
                    val sel = s == w.style
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (sel) p.accent.copy(alpha = 0.25f) else p.surface)
                            .border(1.dp, if (sel) p.accent else p.border, RoundedCornerShape(6.dp))
                            .clickable { ed.setStyle(w.id, s) }
                            .testTag("style_${s.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StyleIcon(s, if (sel) p.accent else p.textDim)
                            Text(s.label, color = if (sel) p.text else p.textDim, fontSize = 10.sp)
                        }
                    }
                }
            }
            CockpitButton("Remover instrumento", { ed.remove(w.id) }, icon = Icons.Filled.Delete, kind = BtnKind.DANGER, modifier = Modifier.fillMaxWidth(), compact = true, tag = "prop_remove")
        }
    }
}

@Composable
private fun StyleIcon(s: WidgetStyle, c: Color) {
    Canvas(Modifier.size(width = 34.dp, height = 22.dp)) {
        when (s) {
            WidgetStyle.CIRCULAR -> {
                drawArc(c, 135f, 270f, false, topLeft = Offset(size.width / 2 - size.height / 2, 0f), size = androidx.compose.ui.geometry.Size(size.height, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
            WidgetStyle.DIGITAL -> {
                drawRoundRect(c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                drawLine(c, Offset(size.width * 0.3f, size.height * 0.55f), Offset(size.width * 0.7f, size.height * 0.55f), 4f)
            }
            WidgetStyle.BAR -> {
                drawRoundRect(c.copy(alpha = 0.3f), topLeft = Offset(0f, size.height * 0.4f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.25f))
                drawRoundRect(c, topLeft = Offset(0f, size.height * 0.4f), size = androidx.compose.ui.geometry.Size(size.width * 0.65f, size.height * 0.25f))
            }
        }
    }
}
