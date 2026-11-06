package com.fotoframe.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fotoframe.app.BuildConfig
import com.fotoframe.app.core.compose.LayoutDensity
import com.fotoframe.app.ui.components.AppBottomBar
import com.fotoframe.app.ui.components.AppTab
import com.fotoframe.app.ui.components.DensitySelector
import com.fotoframe.app.ui.components.SizeSelector
import com.fotoframe.app.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    density: LayoutDensity,
    longEdge: Int,
    cameraCount: Int,
    itemCount: Int,
    snackbarHostState: SnackbarHostState,
    onDensity: (LayoutDensity) -> Unit,
    onLongEdge: (Int) -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit,
    selectedTab: AppTab,
    onTab: (AppTab) -> Unit,
) {
    val palette = LocalPalette.current
    var clearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.paper,
                    titleContentColor = palette.ink,
                ),
            )
        },
        bottomBar = { AppBottomBar(selected = selectedTab, onSelect = onTab) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("输出") }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("版式", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                density.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink3,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DensitySelector(density, onDensity)
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = palette.line2)
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.AspectRatio,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("输出长边", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "$longEdge px",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink3,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        SizeSelector(longEdge, onLongEdge)
                    }
                }
            }

            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.Folder,
                        title = "保存位置",
                        subtitle = "相册 / Pictures/FotoFrame",
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = palette.line2,
                    )
                    SettingRow(
                        icon = Icons.Outlined.PhotoCamera,
                        title = "已导入照片",
                        subtitle = if (itemCount == 0) "还没有照片" else "$itemCount 张 · 长按照片可多选",
                        trailing = if (itemCount > 0) {
                            {
                                TextButton(onClick = { clearConfirm = true }) {
                                    Text("清空", color = palette.error)
                                }
                            }
                        } else null,
                    )
                }
            }

            item { SectionLabel("外观") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "深色模式",
                        subtitle = "跟随系统自动切换",
                    )
                }
            }

            item { SectionLabel("关于") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.PhotoCamera,
                        title = "支持机型",
                        subtitle = "Hasselblad · Leica · Panasonic · Ricoh · Sony",
                        trailing = {
                            Text(
                                "$cameraCount 台",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink3,
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = palette.line2,
                    )
                    SettingRow(
                        icon = Icons.Outlined.Info,
                        title = "关于 FotoFrame",
                        subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.clickable(onClick = onAbout),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = palette.line2,
                    )
                    SettingRow(
                        icon = Icons.Outlined.Lock,
                        title = "隐私",
                        subtitle = "纯本地处理 · 无网络权限 · 不收数据",
                    )
                }
            }
            item {
                Text(
                    "相机背面图取自各品牌官方产品图，仅用于本工具合成展示。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink3,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
        }
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            containerColor = palette.surface,
            title = { Text("清空照片？", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "只会移除列表中的 $itemCount 张照片，不会删除相册里的原图。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirm = false
                    onClearAll()
                }) {
                    Text("清空", color = palette.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) {
                    Text("取消", color = palette.ink2)
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalPalette.current.ink3,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.line2),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.ink2,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.ink3,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}