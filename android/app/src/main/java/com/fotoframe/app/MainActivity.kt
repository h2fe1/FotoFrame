package com.fotoframe.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fotoframe.app.ui.FotoFrameApp
import com.fotoframe.app.ui.theme.FotoFrameTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FotoFrameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false

        setContent {
            FotoFrameTheme {
                FotoFrameApp(viewModel)
            }
        }

        // Launch from "share to" / "open with" straight into the queue.
        // Skip on recreation (rotation / display-size change): the ViewModel still has
        // the queue, and replaying the intent would only duplicate it.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uris = collectImageUris(intent)
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }

    private fun collectImageUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val out = mutableListOf<Uri>()

        intent.data?.let { out += it }

        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (stream != null) out += stream
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                @Suppress("DEPRECATION")
                val streams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                streams?.let { out += it }
            }
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { out += it }
                }
            }
        }

        return out
            .filter { it.scheme == "content" || it.scheme == "file" }
            .distinctBy { it.toString() }
    }
}