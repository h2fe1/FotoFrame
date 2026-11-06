package com.fotoframe.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fotoframe.app.BuildConfig
import com.fotoframe.app.FotoFrameViewModel
import com.fotoframe.app.UiEvent
import com.fotoframe.app.ui.components.AppTab
import com.fotoframe.app.ui.screens.EditorScreen
import com.fotoframe.app.ui.screens.LibraryScreen
import com.fotoframe.app.ui.screens.SettingsScreen
import com.fotoframe.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch

enum class AppScreen { Library, Editor, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FotoFrameApp(viewModel: FotoFrameViewModel) {
    val palette = LocalPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.Library.name) }
    val screen = AppScreen.valueOf(screenName)
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var aboutOpen by remember { mutableStateOf(false) }

    val pickVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }

    val openPicker: () -> Unit = {
        pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openFilePicker: () -> Unit = {
        pickFiles.launch(arrayOf("image/*", "application/octet-stream"))
    }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun switchTab(tab: AppTab) {
        exitSelection()
        screenName = when (tab) {
            AppTab.Library -> AppScreen.Library.name
            AppTab.Editor -> AppScreen.Editor.name
            AppTab.Settings -> AppScreen.Settings.name
        }
    }

    fun openItem(index: Int) {
        if (index !in viewModel.items.indices) return
        viewModel.select(index)
        exitSelection()
        screenName = AppScreen.Editor.name
    }

    fun toggleSelection(index: Int) {
        if (index !in viewModel.items.indices) return
        selectionMode = true
        selected = if (index in selected) selected - index else selected + index
    }

    // After a picker import: a single photo opens the editor, multiple photos land
    // in the grid with the newly imported tiles already selected for batch framing.
    LaunchedEffect(viewModel.importGeneration) {
        if (viewModel.importGeneration == 0) return@LaunchedEffect
        val indices = viewModel.lastAddedUris.mapNotNull { uri ->
            viewModel.items.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }
        }
        if (indices.isEmpty()) return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "已导入 ${indices.size} 张照片",
                duration = SnackbarDuration.Short,
            )
        }
        if (indices.size == 1) {
            viewModel.select(indices.first())
            exitSelection()
            screenName = AppScreen.Editor.name
        } else {
            selected = indices.toSet()
            selectionMode = true
            screenName = AppScreen.Library.name
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.Message -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = if (event.imageUri != null) "查看" else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed && event.imageUri != null) {
                        viewModel.viewImage(event.imageUri)
                    }
                }
            }
        }
    }

    BackHandler(enabled = selectionMode || screen != AppScreen.Library) {
        when {
            selectionMode -> exitSelection()
            screen != AppScreen.Library -> screenName = AppScreen.Library.name
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.paper)
    ) {
        when (screen) {
            AppScreen.Library -> LibraryScreen(
                items = viewModel.items,
                selectedIndex = viewModel.selectedIndex,
                selectionMode = selectionMode,
                selected = selected,
                importing = viewModel.importing,
                importProgress = viewModel.importProgress,
                exporting = viewModel.exporting,
                exportProgress = viewModel.exportProgress,
                snackbarHostState = snackbarHostState,
                onImport = openPicker,
                onImportFile = openFilePicker,
                onOpen = ::openItem,
                onToggle = ::toggleSelection,
                onLongPress = { index ->
                    selectionMode = true
                    selected = if (index in selected) selected - index else selected + index
                },
                onEnterSelection = {
                    selectionMode = true
                    selected = emptySet()
                },
                onExitSelection = ::exitSelection,
                onSelectAll = {
                    val all = viewModel.items.indices.toSet()
                    selected = if (selected.containsAll(all)) emptySet() else all
                },
                onBatch = {
                    val indices = selected
                    exitSelection()
                    viewModel.saveIndices(indices)
                },
                onDeleteSelected = {
                    val indices = selected
                    exitSelection()
                    viewModel.removeIndices(indices)
                },
                onClearAll = {
                    exitSelection()
                    viewModel.clearAll()
                },
                onAbout = { aboutOpen = true },
                selectedTab = AppTab.Library,
                onTab = ::switchTab,
            )

            AppScreen.Editor -> EditorScreen(
                item = viewModel.current,
                preview = viewModel.preview,
                previewing = viewModel.previewing,
                error = viewModel.error,
                density = viewModel.density,
                longEdge = viewModel.longEdge,
                exporting = viewModel.exporting,
                exportProgress = viewModel.exportProgress,
                canvas = viewModel.canvasLayout(),
                snackbarHostState = snackbarHostState,
                onBack = {
                    screenName = AppScreen.Library.name
                },
                onImport = openPicker,
                onImportFile = openFilePicker,
                onRetry = viewModel::refreshPreview,
                onDensity = viewModel::updateDensity,
                onLongEdge = viewModel::updateLongEdge,
                onSave = viewModel::saveCurrent,
                onShare = viewModel::shareCurrent,
                onCopy = viewModel::copyCurrent,
                onAbout = { aboutOpen = true },
            )

            AppScreen.Settings -> SettingsScreen(
                density = viewModel.density,
                longEdge = viewModel.longEdge,
                cameraCount = viewModel.cameraCount,
                itemCount = viewModel.items.size,
                snackbarHostState = snackbarHostState,
                onDensity = viewModel::updateDensity,
                onLongEdge = viewModel::updateLongEdge,
                onClearAll = viewModel::clearAll,
                onAbout = { aboutOpen = true },
                selectedTab = AppTab.Settings,
                onTab = ::switchTab,
            )
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("FotoFrame", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "把照片套回拍摄它的相机里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink2,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}\n" +
                        "26 台相机官方背面图 · 逐台标定屏幕四角\n" +
                        "相纸灰 #E9E9E6 · 长边 1600 / 2400 / 3200",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink3,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "所有照片只在本机处理，不上传网络。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("好", color = palette.accent)
            }
        },
    )
}