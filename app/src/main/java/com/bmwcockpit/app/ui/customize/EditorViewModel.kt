package com.bmwcockpit.app.ui.customize

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bmwcockpit.app.CockpitApp
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.DashLayout
import com.bmwcockpit.app.prefs.EditorDefaults
import com.bmwcockpit.app.prefs.EditorLayout
import com.bmwcockpit.app.prefs.EditorWidget
import com.bmwcockpit.app.prefs.WidgetStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Draft state of the visual editor. Changes stay in [draft] until [save]; [cancel] reloads the persisted
 * composition. Coordinates are normalized (0..1) so the composition fits any screen.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as CockpitApp).container

    data class State(
        val draft: EditorLayout = EditorDefaults.layout(),
        val saved: EditorLayout = EditorDefaults.layout(),
        val selectedId: String? = null,
        val loaded: Boolean = false,
    ) {
        val dirty: Boolean get() = loaded && draft != saved
        val selected: EditorWidget? get() = draft.widgets.firstOrNull { it.id == selectedId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = container.awaitSettings()
            _state.value = State(s.customLayout, s.customLayout, null, true)
        }
    }

    private fun edit(t: (EditorLayout) -> EditorLayout) = _state.update { it.copy(draft = EditorDefaults.sanitize(t(it.draft))) }

    private fun editWidget(id: String, t: (EditorWidget) -> EditorWidget) =
        edit { l -> l.copy(widgets = l.widgets.map { if (it.id == id) t(it) else it }) }

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun add(sensor: SensorId) {
        val id = "w" + System.nanoTime().toString(36)
        val style = if (sensor == SensorId.SPEED || sensor == SensorId.RPM) WidgetStyle.CIRCULAR else WidgetStyle.DIGITAL
        val (w, h) = if (style == WidgetStyle.CIRCULAR) 0.26f to 0.46f else 0.24f to 0.22f
        edit { l -> l.copy(widgets = l.widgets + EditorWidget(id, sensor, 0.5f - w / 2, 0.5f - h / 2, w, h, style, EditorDefaults.BLUE)) }
        select(id)
    }

    fun remove(id: String) {
        edit { l -> l.copy(widgets = l.widgets.filterNot { it.id == id }) }
        select(null)
    }

    fun move(id: String, dx: Float, dy: Float) = editWidget(id) { it.copy(x = it.x + dx, y = it.y + dy) }

    fun resize(id: String, dw: Float, dh: Float) = editWidget(id) {
        it.copy(w = (it.w + dw).coerceIn(EditorDefaults.MIN_SIZE, 1f - it.x), h = (it.h + dh).coerceIn(EditorDefaults.MIN_SIZE, 1f - it.y))
    }

    /** Size slider: scales the widget around its centre keeping the aspect ratio. */
    fun setScale(id: String, scale: Float) = editWidget(id) {
        val aspect = it.h / it.w
        val nw = scale.coerceIn(EditorDefaults.MIN_SIZE, 1f)
        val nh = (nw * aspect).coerceIn(EditorDefaults.MIN_SIZE, 1f)
        val cx = it.x + it.w / 2
        val cy = it.y + it.h / 2
        it.copy(w = nw, h = nh, x = cx - nw / 2, y = cy - nh / 2)
    }

    fun setSensor(id: String, s: SensorId) = editWidget(id) { it.copy(sensor = s) }
    fun setColor(id: String, c: Long) = editWidget(id) { it.copy(color = c) }
    fun setStyle(id: String, st: WidgetStyle) = editWidget(id) { it.copy(style = st) }

    fun save(andUse: Boolean = false) {
        val d = _state.value.draft
        viewModelScope.launch {
            container.settings.update { s -> s.copy(customLayout = d, layout = if (andUse) DashLayout.CUSTOM else s.layout) }
            _state.update { it.copy(saved = d) }
            container.notify(if (andUse) "Painel salvo e aplicado" else "Painel salvo")
        }
    }

    fun cancel() = _state.update { it.copy(draft = it.saved, selectedId = null) }

    fun restoreDefault() = _state.update { it.copy(draft = EditorDefaults.layout(), selectedId = null) }

    fun saveAs(name: String) {
        val n = name.trim().take(30).ifBlank { return }
        val d = _state.value.draft.copy(name = n)
        viewModelScope.launch {
            container.settings.update { s -> s.copy(savedLayouts = listOf(d) + s.savedLayouts.filterNot { it.name == n }, customLayout = d) }
            _state.update { it.copy(draft = d, saved = d) }
            container.notify("Layout \"$n\" salvo")
        }
    }

    fun load(layout: EditorLayout) = _state.update { it.copy(draft = EditorDefaults.sanitize(layout), selectedId = null) }

    fun deleteSaved(name: String) {
        viewModelScope.launch { container.settings.update { s -> s.copy(savedLayouts = s.savedLayouts.filterNot { it.name == name }) } }
    }
}
