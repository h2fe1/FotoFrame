package com.fotoframe.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fotoframe.app.ui.theme.LocalPalette

enum class AppTab(val label: String, val icon: ImageVector) {
    Library("照片", Icons.Outlined.PhotoLibrary),
    Editor("编辑", Icons.Outlined.Edit),
    Settings("设置", Icons.Outlined.Settings),
}

/** Native three-tab bottom bar used by the library and settings screens. */
@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    NavigationBar(
        modifier = modifier,
        containerColor = palette.surface,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.accent,
                    selectedTextColor = palette.accent,
                    indicatorColor = palette.accentSoft,
                    unselectedIconColor = palette.ink3,
                    unselectedTextColor = palette.ink3,
                ),
            )
        }
    }
}