package com.fotoframe.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fotoframe.app.ui.theme.LocalPalette
import kotlin.math.min

/**
 * Framed result with native pinch/pan/double-tap. The shadow keeps the paper
 * output feeling like a physical print on the stage.
 */
@Composable
fun ResultPreview(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val availableW = (maxWidth - 28.dp).coerceAtLeast(80.dp)
        val availableH = (maxHeight - 20.dp).coerceAtLeast(80.dp)
        val fit = min(
            availableW.value / bitmap.width,
            availableH.value / bitmap.height,
        )
        val width = (bitmap.width * fit).dp
        val height = (bitmap.height * fit).dp
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { height.toPx() }

        var scale by remember(bitmap) { mutableFloatStateOf(1f) }
        var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

        fun clamp(candidate: Offset, atScale: Float): Offset {
            val maxX = (atScale - 1f) * widthPx / 2f
            val maxY = (atScale - 1f) * heightPx / 2f
            return Offset(
                candidate.x.coerceIn(-maxX, maxX),
                candidate.y.coerceIn(-maxY, maxY),
            )
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val next = (scale * zoomChange).coerceIn(1f, 5f)
            scale = next
            offset = clamp(offset + panChange, next)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        }
                    )
                }
                .transformable(transformState)
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(width)
                    .height(height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RectangleShape,
                        clip = false,
                        ambientColor = Color(0xFF3C2D19).copy(alpha = if (palette.isDark) 0.65f else 0.24f),
                        spotColor = Color(0xFF3C2D19).copy(alpha = if (palette.isDark) 0.65f else 0.28f),
                    )
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "成图预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                )
            }

            AnimatedVisibility(
                visible = scale > 1.01f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Surface(
                    color = palette.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        scale = 1f
                        offset = Offset.Zero
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.FitScreen,
                            contentDescription = null,
                            tint = palette.ink2,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "适应",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = palette.ink2,
                        )
                    }
                }
            }
        }
    }
}