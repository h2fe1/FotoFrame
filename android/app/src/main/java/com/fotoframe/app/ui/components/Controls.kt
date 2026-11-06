package com.fotoframe.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fotoframe.app.core.compose.LayoutDensity
import com.fotoframe.app.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DensitySelector(
    density: LayoutDensity,
    onDensity: (LayoutDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val values = LayoutDensity.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        values.forEachIndexed { index, value ->
            SegmentedButton(
                selected = value == density,
                onClick = { onDensity(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                modifier = Modifier.height(40.dp),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = palette.accentSoft,
                    activeContentColor = palette.ink,
                    activeBorderColor = palette.accent,
                    inactiveContainerColor = palette.surface,
                    inactiveContentColor = palette.ink2,
                    inactiveBorderColor = palette.line,
                ),
            ) {
                Text(value.label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSelector(
    longEdge: Int,
    onLongEdge: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val values = listOf(1600, 2400, 3200)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        values.forEachIndexed { index, value ->
            SegmentedButton(
                selected = value == longEdge,
                onClick = { onLongEdge(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                modifier = Modifier.height(40.dp),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = palette.accentSoft,
                    activeContentColor = palette.ink,
                    activeBorderColor = palette.accent,
                    inactiveContainerColor = palette.surface,
                    inactiveContentColor = palette.ink2,
                    inactiveBorderColor = palette.line,
                ),
            ) {
                Text(value.toString())
            }
        }
    }
}