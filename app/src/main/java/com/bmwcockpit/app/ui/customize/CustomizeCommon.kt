package com.bmwcockpit.app.ui.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bmwcockpit.app.ui.Routes
import com.bmwcockpit.app.ui.common.Segmented
import com.bmwcockpit.app.ui.go

private enum class CustomTab(val label: String, val route: String) {
    EDITOR("Editor de painel", Routes.EDITOR),
    LAYOUTS("Multitelas", Routes.LAYOUTS),
    THEMES("Temas e skins", Routes.THEMES),
}

/** Sub-navigation of the "Personalizar" area. */
@Composable
fun CustomizeTabs(nav: NavHostController, current: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        Segmented(
            CustomTab.entries, CustomTab.entries.first { it.route == current }, { it.label }, { nav.go(it.route) },
            icon = {
                when (it) {
                    CustomTab.EDITOR -> Icons.Filled.Brush
                    CustomTab.LAYOUTS -> Icons.Filled.Dashboard
                    CustomTab.THEMES -> Icons.Filled.Palette
                }
            },
            tag = { "custom_${it.name}" },
        )
    }
}
