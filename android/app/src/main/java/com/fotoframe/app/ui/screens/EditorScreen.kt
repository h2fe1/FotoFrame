package com.fotoframe.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fotoframe.app.core.compose.FrameLayout
import com.fotoframe.app.core.compose.LayoutDensity
import com.fotoframe.app.model.BatchItem
import com.fotoframe.app.ui.components.DensitySelector
import com.fotoframe.app.ui.components.InfoSheet
import com.fotoframe.app.ui.components.ResultPreview
import com.fotoframe.app.ui.components.SizeSelector
import com.fotoframe.app.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    item: BatchItem?,
    preview: Bitmap?,
    previewing: Boolean,
    error: String?,
    density: LayoutDensity,
    longEdge: Int,
    exporting: Boolean,
    exportProgress: String,
    canvas: FrameLayout?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onImportFile: () -> Unit,
    onRetry: () -> Unit,
    onDensity: (LayoutDensity) -> Unit,
    onLongEdge: (Int) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onAbout: () -> Unit,
) {
    val palette = LocalPalette.current
    var infoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回照片",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            item?.camera?.displayName
                                ?: if (item != null) "未识别机型" else "编辑器",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item?.name ?: "选择一张照片开始",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { infoOpen = true },
                        enabled = item != null,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "照片信息",
                            tint = if (item != null) palette.ink2 else palette.ink3.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        enabled = item?.camera != null && !exporting,
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = if (item?.camera != null && !exporting) palette.ink2
                            else palette.ink3.copy(alpha = 0.4f),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = palette.surface,
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制成图") },
                                enabled = item?.camera != null && !exporting,
                                onClick = {
                                    menuOpen = false
                                    onCopy()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("从文件选择…") },
                                enabled = !exporting,
                                onClick = {
                                    menuOpen = false
                                    onImportFile()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("关于 FotoFrame") },
                                onClick = {
                                    menuOpen = false
                                    onAbout()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.paper,
                    titleContentColor = palette.ink,
                    actionIconContentColor = palette.ink2,
                ),
            )
        },
        bottomBar = {
            if (item?.camera != null) {
                EditorControls(
                    item = item,
                    density = density,
                    longEdge = longEdge,
                    exporting = exporting,
                    exportProgress = exportProgress,
                    onDensity = onDensity,
                    onLongEdge = onLongEdge,
                    onSave = onSave,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.stage, palette.paper),
                    )
                )
        ) {
            when {
                item == null -> EditorEmpty(
                    onImport = onImport,
                    onImportFile = onImportFile,
                )

                preview == null && previewing -> EditorLoading()

                preview == null -> EditorError(
                    item = item,
                    error = error ?: "暂时无法生成预览",
                    onBack = onBack,
                    onRetry = onRetry,
                )

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        EditorMetaRow(
                            canvas = canvas,
                            previewing = previewing,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            ResultPreview(
                                bitmap = preview,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (previewing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = palette.accent,
                                    trackColor = Color.Transparent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (infoOpen && item != null) {
        InfoSheet(
            item = item,
            onDismiss = { infoOpen = false },
            onCopy = {
                infoOpen = false
                onCopy()
            },
            onShare = {
                infoOpen = false
                onShare()
            },
        )
    }
}

@Composable
private fun EditorMetaRow(
    canvas: FrameLayout?,
    previewing: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (previewing) palette.accent else palette.ok, CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (previewing) "更新中" else "已套框",
            style = MaterialTheme.typography.labelMedium,
            color = palette.ink2,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            canvas?.let { "${it.canvasW} × ${it.canvasH} · PNG" } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = palette.ink3,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "双指缩放",
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink3,
        )
    }
}

@Composable
private fun EditorControls(
    item: BatchItem,
    density: LayoutDensity,
    longEdge: Int,
    exporting: Boolean,
    exportProgress: String,
    onDensity: (LayoutDensity) -> Unit,
    onLongEdge: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val palette = LocalPalette.current
    val canSave = item.camera != null && !exporting
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 16.dp,
        tonalElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.ink3.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("版式", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${density.label} · 相机留白 ${(density.cameraMarginRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink3,
                )
            }
            Spacer(Modifier.height(8.dp))
            DensitySelector(density, onDensity)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("输出长边", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$longEdge px · 相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink3,
                )
            }
            Spacer(Modifier.height(8.dp))
            SizeSelector(longEdge, onLongEdge)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.accentInk,
                ),
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = palette.accentInk,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (exportProgress.isBlank()) "保存中…" else "保存中 $exportProgress",
                        style = MaterialTheme.typography.titleSmall,
                    )
                } else {
                    Icon(
                        Icons.Outlined.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("保存到相册", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        }
    }
}

@Composable
private fun EditorEmpty(
    onImport: () -> Unit,
    onImportFile: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(palette.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "还没有作品",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "从照片页选择一张，或直接导入新的照片。",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.accentInk,
            ),
        ) {
            Text("选择照片", style = MaterialTheme.typography.titleSmall)
        }
        TextButton(onClick = onImportFile) {
            Text("从文件选择…", color = palette.accent)
        }
    }
}

@Composable
private fun EditorError(
    item: BatchItem,
    error: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(palette.accentSoft.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = palette.error,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (item.camera == null) "没有认出这台相机" else "这一步没走完",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.accentInk,
            ),
        ) {
            Text("换一张照片")
        }
        if (item.camera != null) {
            TextButton(onClick = onRetry) {
                Text("重试", color = palette.accent)
            }
        }
    }
}

@Composable
private fun EditorLoading() {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = palette.accent,
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "正在生成预览…",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink2,
        )
    }
}