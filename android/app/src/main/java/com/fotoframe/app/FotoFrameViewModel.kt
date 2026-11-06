package com.fotoframe.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fotoframe.app.core.catalog.CameraCatalog
import com.fotoframe.app.core.compose.FrameLayout
import com.fotoframe.app.core.compose.FrameTheme
import com.fotoframe.app.core.compose.LayoutDensity
import com.fotoframe.app.core.compose.LayoutEngine
import com.fotoframe.app.core.exif.ExifInfo
import com.fotoframe.app.core.exif.ExifMissingException
import com.fotoframe.app.core.exif.ExifReader
import com.fotoframe.app.model.BatchItem
import com.fotoframe.app.model.ItemStatus
import com.fotoframe.app.platform.GallerySaver
import com.fotoframe.app.platform.ShareAndClipboard
import com.fotoframe.app.render.CameraImageStore
import com.fotoframe.app.render.FrameRenderer
import com.fotoframe.app.render.PhotoDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot UI messages (snackbar, open-in-gallery action). */
sealed interface UiEvent {
    data class Message(val text: String, val imageUri: Uri? = null) : UiEvent
}

class FotoFrameViewModel(app: Application) : AndroidViewModel(app) {

    val items = mutableStateListOf<BatchItem>()
    var selectedIndex by mutableIntStateOf(-1)
        private set

    var density by mutableStateOf(LayoutDensity.Normal)
        private set
    var longEdge by mutableIntStateOf(2400)
        private set

    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var previewing by mutableStateOf(false)
        private set

    var importing by mutableStateOf(false)
        private set
    var importProgress by mutableStateOf("")
        private set

    var exporting by mutableStateOf(false)
        private set
    var exportProgress by mutableStateOf("")
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var cameraCount by mutableIntStateOf(0)
        private set

    /** URIs added by the most recent import (for one-shot grid selection). */
    var lastAddedUris by mutableStateOf<List<Uri>>(emptyList())
        private set
    var importGeneration by mutableIntStateOf(0)
        private set

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private val appContext = app.applicationContext
    private val prefs = app.getSharedPreferences("fotoframe", 0)
    private val imageStore = CameraImageStore(app)
    private val catalogDeferred = viewModelScope.async(Dispatchers.IO) {
        CameraCatalog.loadFromAssets(app.assets)
    }

    private var previewJob: Job? = null
    private var renderToken = 0

    /** Decoded source for the current item, reused across theme changes. */
    private var previewSource: Bitmap? = null
    private var previewSourceUri: String? = null

    init {
        density = runCatching {
            LayoutDensity.valueOf(prefs.getString("density", LayoutDensity.Normal.name)!!)
        }.getOrDefault(LayoutDensity.Normal)
        longEdge = prefs.getInt("longEdge", 2400).takeIf { it in setOf(1600, 2400, 3200) } ?: 2400

        viewModelScope.launch {
            try {
                val catalog = catalogDeferred.await()
                cameraCount = catalog.assets.size
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = "相机素材加载失败：${t.message}"
            }
        }
    }

    val current: BatchItem?
        get() = items.getOrNull(selectedIndex)

    val supportedCount: Int
        get() = items.count { it.camera != null }

    fun theme(): FrameTheme = FrameTheme(
        longEdge = longEdge,
        cameraMarginRatio = density.cameraMarginRatio,
    )

    fun previewTheme(): FrameTheme = theme().copy(
        longEdge = if (longEdge < 1600) longEdge else 1600,
    )

    fun canvasLayout(item: BatchItem? = current): FrameLayout? {
        val camera = item?.camera ?: return null
        if (item.width <= 0 || item.height <= 0) return null
        return runCatching {
            LayoutEngine.compute(theme(), camera, item.width, item.height)
        }.getOrNull()
    }

    // ---------------- import ----------------

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty() || importing) return
        val existing = items.map { it.uri.toString() }.toSet()
        val fresh = uris.distinctBy { it.toString() }.filter { it.toString() !in existing }
        if (fresh.isEmpty()) {
            viewModelScope.launch { events.send(UiEvent.Message("这些照片已经在队列里了")) }
            return
        }
        viewModelScope.launch {
            importing = true
            importProgress = "0/${fresh.size}"
            error = null
            val catalog = try {
                catalogDeferred.await().also { cameraCount = it.assets.size }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = "相机素材加载失败：${t.message}"
                importing = false
                return@launch
            }

            val hadSelection = selectedIndex >= 0
            val addedUris = mutableListOf<Uri>()
            for ((i, uri) in fresh.withIndex()) {
                val item = withContext(Dispatchers.IO) { loadItem(uri, catalog) }
                if (item != null && items.none { it.uri == uri }) {
                    items.add(item)
                    addedUris += uri
                }
                importProgress = "${i + 1}/${fresh.size}"
                if (!hadSelection && selectedIndex < 0 && items.isNotEmpty()) {
                    selectedIndex = 0
                }
            }
            importing = false
            importProgress = ""
            if (addedUris.isEmpty()) {
                events.send(UiEvent.Message("没有可导入的照片"))
            } else {
                lastAddedUris = addedUris
                importGeneration++
                if (!hadSelection) refreshPreview()
            }
        }
    }

    private fun loadItem(uri: Uri, catalog: CameraCatalog): BatchItem? {
        val originalName = runCatching { PhotoDecoder.displayName(appContext, uri) }
            .getOrDefault("photo")
        var exif: ExifInfo? = null
        var camera: com.fotoframe.app.core.catalog.CameraAsset? = null
        var matchInfo = ""
        var status = ItemStatus.Pending
        var failure: String? = null

        try {
            val bytes = PhotoDecoder.readBytes(appContext, uri)
            try {
                var info = ExifReader.read(bytes)
                if (info.orientation == null) {
                    val frameworkOrientation = PhotoDecoder.frameworkOrientation(appContext, uri)
                    if (frameworkOrientation != null) info = info.copy(orientation = frameworkOrientation)
                }
                exif = info
                val hit = catalog.match(make = info.make, model = info.model)
                val label = exifLabel(info.make, info.model)
                if (hit != null) {
                    camera = hit.asset
                    matchInfo = listOf(hit.how.label, label)
                        .filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    matchInfo = "未识别 · $label"
                }
            } catch (e: ExifMissingException) {
                exif = ExifInfo()
                matchInfo = "无 EXIF 机型信息"
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                exif = ExifInfo()
                matchInfo = "EXIF 读取失败"
                status = ItemStatus.Failed
                failure = e.message ?: "EXIF 读取失败"
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            status = ItemStatus.Failed
            failure = t.message ?: "无法读取照片"
            matchInfo = "读取失败"
        }

        val thumbnail = runCatching {
            PhotoDecoder.decodeThumbnail(appContext, uri, exif?.orientation)
        }.getOrNull()
        val size = runCatching {
            PhotoDecoder.readSize(appContext, uri, exif?.orientation)
        }.getOrNull()
        val name = friendlyName(originalName, exif, camera)

        return BatchItem(
            uri = uri,
            name = name,
            exif = exif,
            camera = camera,
            matchInfo = matchInfo,
            width = size?.first ?: thumbnail?.width ?: 0,
            height = size?.second ?: thumbnail?.height ?: 0,
            thumbnail = thumbnail,
            status = status,
            error = failure,
        )
    }

    /** "Canon" + "Canon EOS R5m2" → "Canon EOS R5m2" (avoid duplicated brands). */
    private fun exifLabel(make: String?, model: String?): String {
        val mk = make?.trim().orEmpty()
        val md = model?.trim().orEmpty()
        if (md.isEmpty()) return mk
        if (mk.isEmpty()) return md
        if (md.startsWith(mk, ignoreCase = true)) return md
        val first = mk.substringBefore(' ')
        if (first.isNotEmpty() && md.startsWith(first, ignoreCase = true)) return md
        return "$mk $md"
    }

    /**
     * The Android Photo Picker deliberately hides the original file name on some
     * versions (it hands back a numeric media id). In that case build a readable
     * name from the camera + EXIF capture date so exports are still recognizable.
     */
    private fun friendlyName(
        original: String,
        exif: ExifInfo?,
        camera: com.fotoframe.app.core.catalog.CameraAsset?,
    ): String {
        val stem = original.substringBeforeLast('.', original)
        val numeric = stem.length >= 6 && stem.all { it.isDigit() }
        if (!numeric) return original
        val ext = original.substringAfterLast('.', "jpg")
        val model = camera?.model ?: exif?.model ?: "FotoFrame"
        val cam = model.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "FotoFrame" }
        val date = exif?.dateTimeOriginal
            ?.takeIf { it.length >= 19 && it[4] == ':' && it[7] == ':' }
            ?.let { "${it.substring(0, 4)}${it.substring(5, 7)}${it.substring(8, 10)}" +
                "_${it.substring(11, 13)}${it.substring(14, 16)}" }
        return if (date != null) "${cam}_${date}.${ext}" else "${cam}.${ext}"
    }

    // ---------------- selection / preview ----------------

    fun select(index: Int) {
        if (index !in items.indices || index == selectedIndex) return
        selectedIndex = index
        error = null
        refreshPreview()
    }

    fun removeAt(index: Int) {
        if (index !in items.indices || exporting) return
        val wasSelected = selectedIndex == index
        items.removeAt(index)
        if (items.isEmpty()) {
            previewJob?.cancel()
            renderToken++
            selectedIndex = -1
            preview = null
            previewSource = null
            previewSourceUri = null
            error = null
            previewing = false
            return
        }
        selectedIndex = when {
            wasSelected -> index.coerceAtMost(items.size - 1)
            selectedIndex > index -> selectedIndex - 1
            else -> selectedIndex
        }
        previewSource = null
        previewSourceUri = null
        refreshPreview()
    }

    fun removeIndices(indices: Set<Int>) {
        if (exporting || indices.isEmpty()) return
        indices.sortedDescending().forEach { removeAt(it) }
    }

    fun clearAll() {
        if (exporting) return
        previewJob?.cancel()
        renderToken++
        items.clear()
        selectedIndex = -1
        preview = null
        previewSource = null
        previewSourceUri = null
        previewing = false
        error = null
    }

    fun refreshPreview() {
        previewJob?.cancel()
        val item = current
        if (item == null) {
            preview = null
            previewing = false
            error = null
            return
        }
        if (item.camera == null) {
            preview = null
            previewing = false
            val raw = exifLabel(item.exif?.make, item.exif?.model)
            error = when {
                item.status == ItemStatus.Failed -> item.error ?: "照片读取失败"
                raw.isEmpty() -> "照片没有 EXIF 机型信息，无法自动匹配"
                else -> "EXIF 机型「$raw」暂不支持"
            }
            return
        }
        val token = ++renderToken
        previewing = true
        previewJob = viewModelScope.launch {
            try {
                val camera = item.camera ?: return@launch
                val source = if (previewSourceUri == item.uri.toString() &&
                    previewSource?.isRecycled == false
                ) {
                    previewSource!!
                } else {
                    withContext(Dispatchers.IO) {
                        PhotoDecoder.decode(
                            appContext,
                            item.uri,
                            maxLongEdge = 1600,
                            orientation = item.exif?.orientation,
                        )
                    }.also { bitmap ->
                        previewSource = bitmap
                        previewSourceUri = item.uri.toString()
                    }
                }
                if (token != renderToken) return@launch

                val result = withContext(Dispatchers.Default) {
                    FrameRenderer.render(imageStore, camera, source, previewTheme())
                }
                if (token == renderToken) {
                    preview = result.bitmap
                    val index = items.indexOfFirst { it.uri == item.uri }
                    if (index >= 0) {
                        val known = items[index]
                        items[index] = known.copy(
                            width = if (known.width > 0) known.width else source.width,
                            height = if (known.height > 0) known.height else source.height,
                            status = when (known.status) {
                                ItemStatus.Done, ItemStatus.Processing -> known.status
                                else -> ItemStatus.Pending
                            },
                            error = null,
                        )
                    }
                    error = null
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                if (token == renderToken) {
                    preview = null
                    error = when (t) {
                        is OutOfMemoryError -> "内存不足，无法生成预览"
                        else -> "预览失败：${t.message}"
                    }
                }
            } finally {
                if (token == renderToken) previewing = false
            }
        }
    }

    fun updateDensity(value: LayoutDensity) {
        if (density == value) return
        density = value
        prefs.edit().putString("density", value.name).apply()
        refreshPreview()
    }

    fun updateLongEdge(value: Int) {
        if (longEdge == value) return
        longEdge = value
        prefs.edit().putInt("longEdge", value).apply()
        refreshPreview()
    }

    // ---------------- export ----------------

    fun saveCurrent() {
        val item = current ?: return
        if (exporting) return
        if (item.camera == null) {
            viewModelScope.launch { events.send(UiEvent.Message("当前照片还没有识别到机型")) }
            return
        }
        viewModelScope.launch {
            exporting = true
            exportProgress = "1/1"
            error = null
            try {
                val bytes = renderBytes(item)
                val uri = withContext(Dispatchers.IO) {
                    GallerySaver.savePng(
                        appContext,
                        GallerySaver.uniqueName(item.stem),
                        bytes,
                    )
                }
                events.send(
                    UiEvent.Message("已保存到相册 Pictures/FotoFrame", uri)
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = if (t is OutOfMemoryError) {
                    "内存不足，请把输出长边调小后重试"
                } else {
                    "导出失败：${t.message}"
                }
                error = message
                events.send(UiEvent.Message(message))
            } finally {
                exporting = false
                exportProgress = ""
            }
        }
    }

    fun saveAll() {
        saveIndices(items.indices.toSet())
    }

    /** Batch-save the selected grid items. */
    fun saveIndices(indices: Set<Int>) {
        if (exporting) return
        val selected = indices.sorted().mapNotNull { items.getOrNull(it) }
        val targets = selected.filter { it.camera != null }
        if (targets.isEmpty()) {
            viewModelScope.launch {
                events.send(
                    UiEvent.Message(
                        when {
                            items.isEmpty() -> "还没有导入照片"
                            selected.isEmpty() -> "先选择要套框的照片"
                            else -> "选中的照片都未识别到机型"
                        }
                    )
                )
            }
            return
        }
        startBatch(targets, selected.size - targets.size)
    }

    private fun startBatch(targets: List<BatchItem>, skipped: Int) {
        viewModelScope.launch {
            exporting = true
            error = null
            var done = 0
            var failed = 0
            exportProgress = "0/${targets.size}"
            for (item in targets) {
                val index = items.indexOfFirst { it.uri == item.uri }
                if (index < 0) continue
                items[index] = items[index].copy(status = ItemStatus.Processing)
                try {
                    val bytes = renderBytes(item)
                    withContext(Dispatchers.IO) {
                        GallerySaver.savePng(
                            appContext,
                            GallerySaver.uniqueName(item.stem),
                            bytes,
                        )
                    }
                    val i = items.indexOfFirst { it.uri == item.uri }
                    if (i >= 0) items[i] = items[i].copy(status = ItemStatus.Done, error = null)
                    done++
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    val i = items.indexOfFirst { it.uri == item.uri }
                    if (i >= 0) {
                        items[i] = items[i].copy(
                            status = ItemStatus.Failed,
                            error = t.message ?: "导出失败",
                        )
                    }
                    failed++
                }
                exportProgress = "${done + failed}/${targets.size}"
            }
            exporting = false
            exportProgress = ""
            val skipNote = if (skipped > 0) "，跳过 $skipped 张未识别机型" else ""
            val failNote = if (failed > 0) "，失败 $failed 张" else ""
            events.send(UiEvent.Message("已保存 $done 张到相册$failNote$skipNote"))
        }
    }

    fun shareCurrent() {
        val item = current ?: return
        if (item.camera == null) {
            viewModelScope.launch { events.send(UiEvent.Message("当前照片还没有识别到机型")) }
            return
        }
        viewModelScope.launch {
            try {
                val bytes = renderBytes(item)
                val uri = withContext(Dispatchers.IO) {
                    ShareAndClipboard.cachePng(appContext, item.stem, bytes)
                }
                ShareAndClipboard.share(appContext, uri)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                events.send(UiEvent.Message("分享失败：${t.message}"))
            }
        }
    }

    fun copyCurrent() {
        val item = current ?: return
        if (item.camera == null) {
            viewModelScope.launch { events.send(UiEvent.Message("当前照片还没有识别到机型")) }
            return
        }
        viewModelScope.launch {
            try {
                val bytes = renderBytes(item)
                val uri = withContext(Dispatchers.IO) {
                    ShareAndClipboard.cachePng(appContext, item.stem, bytes)
                }
                val ok = ShareAndClipboard.copyToClipboard(
                    appContext,
                    uri,
                    "${item.stem}_framed.png",
                )
                events.send(
                    UiEvent.Message(if (ok) "已复制到剪贴板（PNG）" else "当前设备不支持复制图片")
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                events.send(UiEvent.Message("复制失败：${t.message}"))
            }
        }
    }

    fun viewImage(uri: Uri) {
        if (!ShareAndClipboard.openImage(appContext, uri)) {
            viewModelScope.launch { events.send(UiEvent.Message("没有可打开图片的应用")) }
        }
    }

    /** Decode at output resolution, render, encode PNG, and free intermediates. */
    private suspend fun renderBytes(item: BatchItem): ByteArray {
        val camera = item.camera ?: error("未识别机型")
        return withContext(Dispatchers.Default) {
            val source = PhotoDecoder.decode(
                appContext,
                item.uri,
                maxLongEdge = longEdge,
                orientation = item.exif?.orientation,
            )
            try {
                val result = FrameRenderer.render(imageStore, camera, source, theme())
                try {
                    FrameRenderer.encodePng(result.bitmap)
                } finally {
                    result.bitmap.recycle()
                }
            } finally {
                source.recycle()
            }
        }
    }
}