package com.fotoframe.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fotoframe.app.model.BatchItem
import com.fotoframe.app.model.ItemStatus
import com.fotoframe.app.ui.components.AppBottomBar
import com.fotoframe.app.ui.components.AppTab
import com.fotoframe.app.ui.theme.LocalPalette
import com.fotoframe.app.ui.theme.serifHeadline

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    items: List<BatchItem>,
    selectedIndex: Int,
    selectionMode: Boolean,
    selected: Set<Int>,
    importing: Boolean,
    importProgress: String,
    exporting: Boolean,
    exportProgress: String,
    snackbarHostState: SnackbarHostState,
    onImport: () -> Unit,
    onImportFile: () -> Unit,
    onOpen: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBatch: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit,
    selectedTab: AppTab,
    onTab: (AppTab) -> Unit,
) {
    val palette = LocalPalette.current
    val validSelected = selected.filter { it in items.indices }
    val supportedSelected = validSelected.count { items[it].camera != null }
    val allSelected = items.isNotEmpty() && validSelected.size == items.size
    var clearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = validSelected.size,
                    allSelected = allSelected,
                    onClose = onExitSelection,
                    onSelectAll = onSelectAll,
                )
            } else {
                LibraryTopBar(
                    count = items.size,
                    importing = importing,
                    importProgress = importProgress,
                    exporting = exporting,
                    exportProgress = exportProgress,
                    canSelect = items.isNotEmpty(),
                    onSelect = onEnterSelection,
                    onImportFile = onImportFile,
                    onClearAll = { clearConfirm = true },
                    onAbout = onAbout,
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                SelectionBottomBar(
                    count = validSelected.size,
                    supported = supportedSelected,
                    exporting = exporting,
                    exportProgress = exportProgress,
                    onDelete = onDeleteSelected,
                    onBatch = onBatch,
                )
            } else {
                AppBottomBar(selected = selectedTab, onSelect = onTab)
            }
        },
        floatingActionButton = {
            if (!selectionMode && items.isNotEmpty()) {
                val busy = importing || exporting
                ExtendedFloatingActionButton(
                    onClick = { if (!busy) onImport() },
                    containerColor = if (busy) palette.accent.copy(alpha = 0.5f) else palette.accent,
                    contentColor = palette.accentInk,
                    shape = RoundedCornerShape(18.dp),
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = {
                        Text(
                            when {
                                importing -> "导入中"
                                exporting -> "生成中"
                                else -> "导入照片"
                            }
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (items.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onImport = onImport,
                onImportFile = onImportFile,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.uri.toString() },
                ) { index, item ->
                    PhotoTile(
                        item = item,
                        selected = index in validSelected,
                        selectionMode = selectionMode,
                        current = index == selectedIndex,
                        onClick = {
                            if (selectionMode) onToggle(index) else onOpen(index)
                        },
                        onLongClick = { onLongPress(index) },
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
                    "只会移除列表中的 ${items.size} 张照片，不会删除相册里的原图。",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    count: Int,
    importing: Boolean,
    importProgress: String,
    exporting: Boolean,
    exportProgress: String,
    canSelect: Boolean,
    onSelect: () -> Unit,
    onImportFile: () -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit,
) {
    val palette = LocalPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = when {
        importing -> "正在导入 ${importProgress.ifBlank { "…" }}"
        exporting -> "正在生成 ${exportProgress.ifBlank { "…" }}"
        count == 0 -> "相机照片自动套框"
        else -> "$count 张照片 · 长按可多选"
    }
    Column(Modifier.background(palette.paper)) {
        TopAppBar(
            title = {
                Column {
                    Text("照片", style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                if (canSelect) {
                    TextButton(onClick = onSelect) {
                        Text("选择", color = palette.accent)
                    }
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
                            text = { Text("从文件选择…") },
                            onClick = {
                                menuOpen = false
                                onImportFile()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("清空照片") },
                            onClick = {
                                menuOpen = false
                                onClearAll()
                            },
                            enabled = canSelect && !importing && !exporting,
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
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
        if (importing || exporting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = palette.accent,
                trackColor = Color.Transparent,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val palette = LocalPalette.current
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "退出选择")
            }
        },
        title = {
            Text(
                if (count == 0) "选择照片" else "已选 $count 张",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(if (allSelected) "取消全选" else "全选", color = palette.accent)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.surface,
            titleContentColor = palette.ink,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomBar(
    count: Int,
    supported: Int,
    exporting: Boolean,
    exportProgress: String,
    onDelete: () -> Unit,
    onBatch: () -> Unit,
) {
    val palette = LocalPalette.current
    Surface(
        color = palette.surface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (exporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = palette.accent,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (exporting) "正在生成 ${exportProgress.ifBlank { "…" }}" else "已选 $count 张",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when {
                            exporting -> "请保持前台，完成后存入相册"
                            count == 0 -> "点按照片进行选择"
                            supported == 0 -> "这些照片未识别到机型"
                            else -> "可生成 $supported 张 · 相册 Pictures/FotoFrame"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = count > 0 && !exporting,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "移除所选",
                        tint = if (count > 0) palette.error else palette.ink3.copy(alpha = 0.4f),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onBatch,
                    enabled = supported > 0 && !exporting,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.accentInk,
                    ),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (count > 0) "套进相机 ($count)" else "套进相机")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    item: BatchItem,
    selected: Boolean,
    selectionMode: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val thumb: Bitmap? = item.thumbnail

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(palette.line2)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
    ) {
        if (thumb != null && !thumb.isRecycled) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.Center),
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 7.dp, end = 7.dp, bottom = 6.dp)
        ) {
            Text(
                item.camera?.displayName ?: "未识别机型",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.stem,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.status == ItemStatus.Failed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(palette.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "!",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }

        if (selectionMode) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.accent.copy(alpha = 0.10f))
                        .border(2.dp, palette.accent)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(
                        if (selected) palette.accent else Color.Black.copy(alpha = 0.20f),
                        CircleShape,
                    )
                    .border(
                        width = 2.dp,
                        color = if (selected) palette.accent else Color.White,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        if (current && !selectionMode && item.status != ItemStatus.Failed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(7.dp)
                    .background(palette.accent, CircleShape)
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onImportFile: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(92.dp)
                .background(palette.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "把照片套回相机里",
            style = palette.serifHeadline(29),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "选择相机直出的照片，自动识别机型，\n装进官方背面图的屏幕。",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink2,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(26.dp))
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
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("选择照片", style = MaterialTheme.typography.titleSmall)
        }
        TextButton(onClick = onImportFile) {
            Text("从文件选择…", color = palette.accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "JPG · PNG · HEIC · TIFF · DNG",
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink3,
        )
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "纯本地处理 · 不上传网络",
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink3,
            )
        }
    }
    }
}
