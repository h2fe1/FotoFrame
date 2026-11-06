package com.fotoframe.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fotoframe.app.model.BatchItem
import com.fotoframe.app.model.CaptureFormats
import com.fotoframe.app.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    item: BatchItem,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val palette = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.ink,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = palette.ink3.copy(alpha = 0.5f),
            )
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                item.camera?.displayName ?: "未识别机型",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = palette.line)
            Spacer(Modifier.height(14.dp))
            Text(
                "拍摄信息",
                style = MaterialTheme.typography.titleSmall,
                color = palette.ink2,
            )
            Spacer(Modifier.height(8.dp))

            val exif = item.exif
            InfoRow("文件", item.name, mono = true)
            InfoRow("机型", item.camera?.displayName ?: "未识别")
            InfoRow("识别", item.matchInfo.ifBlank { "—" })
            if (item.width > 0 && item.height > 0) {
                InfoRow("尺寸", "${item.width} × ${item.height} px", mono = true)
            }
            InfoRow("镜头", CaptureFormats.lens(exif))
            InfoRow("厂商", exif?.make?.trim().takeIf { !it.isNullOrEmpty() } ?: "—")
            InfoRow("日期", CaptureFormats.date(exif?.dateTimeOriginal))
            InfoRow("光圈", CaptureFormats.aperture(exif?.fNumber))
            InfoRow("快门", CaptureFormats.shutter(exif?.exposureTime))
            InfoRow("ISO", CaptureFormats.iso(exif))
            InfoRow("补偿", CaptureFormats.bias(exif?.exposureBias))
            val lat = exif?.latitude
            val lon = exif?.longitude
            if (lat != null && lon != null) {
                InfoRow("地点", CaptureFormats.coordinate(lat, lon))
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val enabled = item.camera != null
                OutlinedButton(
                    onClick = onCopy,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("复制")
                }
                Button(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.accentInk,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("分享")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "所有照片只在本机处理",
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        }
    }
}

@Composable
private fun InfoRow(
    key: String,
    value: String,
    mono: Boolean = false,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink3,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = palette.ink,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}