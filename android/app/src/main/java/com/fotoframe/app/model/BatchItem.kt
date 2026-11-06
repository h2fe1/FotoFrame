package com.fotoframe.app.model

import android.graphics.Bitmap
import android.net.Uri
import com.fotoframe.app.core.catalog.CameraAsset
import com.fotoframe.app.core.exif.ExifInfo

enum class ItemStatus(val label: String) {
    Pending("待处理"),
    Processing("处理中"),
    Done("完成"),
    Failed("失败"),
}

/** One photo in the queue. */
class BatchItem(
    val uri: Uri,
    val name: String,
    val exif: ExifInfo? = null,
    val camera: CameraAsset? = null,
    val matchInfo: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val thumbnail: Bitmap? = null,
    val status: ItemStatus = ItemStatus.Pending,
    val error: String? = null,
) {
    val stem: String
        get() = if (name.contains('.')) name.substringBeforeLast('.') else name

    val failed: Boolean get() = status == ItemStatus.Failed

    fun copy(
        exif: ExifInfo? = this.exif,
        camera: CameraAsset? = this.camera,
        matchInfo: String = this.matchInfo,
        width: Int = this.width,
        height: Int = this.height,
        thumbnail: Bitmap? = this.thumbnail,
        status: ItemStatus = this.status,
        error: String? = this.error,
    ): BatchItem = BatchItem(
        uri = uri,
        name = name,
        exif = exif,
        camera = camera,
        matchInfo = matchInfo,
        width = width,
        height = height,
        thumbnail = thumbnail,
        status = status,
        error = error,
    )
}