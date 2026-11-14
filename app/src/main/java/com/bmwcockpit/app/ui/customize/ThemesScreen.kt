package com.bmwcockpit.app.ui.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.bmwcockpit.app.data.model.SensorId
import com.bmwcockpit.app.prefs.BrightnessMode
import com.bmwcockpit.app.prefs.DriveProfile
import com.bmwcockpit.app.prefs.ThemeId
import com.bmwcockpit.app.ui.MainViewModel
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.common.InfoNote
import com.bmwcockpit.app.ui.common.Panel
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.common.formatValue
import com.bmwcockpit.app.ui.dash.TachScale
import com.bmwcockpit.app.ui.gauges.RoundGauge
import com.bmwcockpit.app.ui.gauges.pxToSp
import com.bmwcockpit.app.ui.theme.CockpitTheme
import com.bmwcockpit.app.ui.theme.CondensedFamily
import com.bmwcockpit.app.ui.theme.LocalPalette
import com.bmwcockpit.app.ui.theme.Palettes

private val AccentOptions = listOf(0xFF1C8CFFL, 0xFFE5262BL, 0xFF8A4BFFL, 0xFF2FD27AL, 0xFFFFB020L, 0xFFFF6A00L, 0xFFF2F2F2L)

/** Themes & skins: each preview is the real gauge component rendered with that theme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(vm: MainViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        CustomizeTabs(nav, Routes.THEMES)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Temas e Skins", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = when { maxWidth > 900.dp -> 5; maxWidth > 560.dp -> 3; else -> 2 }
                val cardW = (maxWidth - 10.dp * (cols - 1)) / cols
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeId.entries.forEach { t ->
                        val selected = settings.theme == t
                        val palette = Palettes.resolve(t, if (selected) settings.accent else null, settings.brightness)
                        Column(
                            Modifier.width(cardW).clip(RoundedCornerShape(10.dp)).background(palette.background)
                                .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.border, RoundedCornerShape(10.dp))
                                .clickable { vm.setTheme(t) }.padding(8.dp).testTag("theme_${t.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(cardW * 0.62f), contentAlignment = Alignment.Center) {
                                CockpitTheme(palette) {
                                    RoundGauge(snap.value(SensorId.RPM), TachScale, Modifier.size(cardW * 0.6f), palette = palette, arcColor = palette.accent, arcColorEnd = palette.accentBright) { d ->
                                        Text(formatValue(SensorId.RPM, snap.value(SensorId.RPM), settings.units), color = palette.text, fontSize = pxToSp(d * 0.16f), fontFamily = CondensedFamily, fontStyle = FontStyle.Italic)
                                    }
                                }
                                if (selected) Icon(Icons.Filled.CheckCircle, "Selecionado", tint = p.accent, modifier = Modifier.align(Alignment.TopEnd).size(22.dp))
                            }
                            Text(t.label, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cor de destaque", color = p.text, fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(p.surfaceHigh).border(if (settings.accent == null) 3.dp else 1.dp, if (settings.accent == null) Color.White else p.border, CircleShape)
                                .clickable { vm.setAccent(null) }.testTag("accent_default"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.FormatColorReset, "Cor do tema", tint = p.textDim, modifier = Modifier.size(20.dp)) }
                        AccentOptions.forEach { c ->
                            val sel = settings.accent == c
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(c.toInt())).border(if (sel) 3.dp else 1.dp, if (sel) Color.White else p.border, CircleShape)
                                    .clickable { vm.setAccent(c) }.testTag("accent_${java.lang.Long.toHexString(c)}"),
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Filled.Check, null, tint = if (c == 0xFFF2F2F2L) Color.Black else Color.White) }
                        }
                    }
                    InfoNote("A cor de destaque altera arcos, seleção e brilhos de todos os instrumentos. O círculo cinza volta à cor do tema.")
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo", color = p.text, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                        Segmented(BrightnessMode.entries, settings.brightness, { it.label }, { vm.setBrightness(it) }, icon = {
                            when (it) { BrightnessMode.AUTO -> Icons.Filled.BrightnessAuto; BrightnessMode.LIGHT -> Icons.Filled.LightMode; BrightnessMode.DARK -> Icons.Filled.DarkMode }
                        })
                    }
                    InfoNote("Claro = superfícies grafite mais luminosas para uso diurno. Automático alterna entre Claro (7h–18h) e Escuro.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Perfil visual", color = p.text, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                        Segmented(DriveProfile.entries, settings.profile, { it.label }, { vm.setProfile(it) })
                    }
                    InfoNote("O perfil COMFORT/SPORT/SPORT+ é apenas uma identificação visual do painel; não é lido do carro.")
                }
            }
        }
    }
}
