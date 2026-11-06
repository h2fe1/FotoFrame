package com.fotoframe.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Share / clipboard / "open in gallery" helpers around a rendered PNG. */
object ShareAndClipboard {

    fun cachePng(context: Context, stem: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeStem = stem.ifBlank { "fotoframe" }
        val file = File(dir, "${safeStem}_framed.png")
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun share(context: Context, uri: Uri, chooserTitle: String = "分享成图") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun copyToClipboard(context: Context, uri: Uri, label: String): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clip = ClipData.newUri(context.contentResolver, label, uri)
        manager.setPrimaryClip(clip)
        return true
    }

    fun openImage(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}