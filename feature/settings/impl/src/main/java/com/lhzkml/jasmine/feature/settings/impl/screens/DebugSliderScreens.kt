package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.slider.Slider
import com.lhzkml.jasmine.core.widgets.slider.SliderDefaults
import com.lhzkml.jasmine.feature.settings.impl.R

/** 图标尺寸、行距与标签字号。 */
private val DebugSliderIconSize = 24.dp
private val DebugSliderRowSpacing = 22.dp
private val DebugSliderLabelFontSize = 12.sp

/**
 * `core:widgets` **标准滑块**（单值）的独立预览页：连续 / 离散 / 自定义范围 / 自定义配色 / 禁用，
 * 一共 5 种用法。
 *
 * 照旧不套设置流那套 `SettingsPage` 顶栏：页内顶栏就是 `core:widgets` 的 [TopAppBar]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSliderScreen(
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var continuous by remember { mutableFloatStateOf(0.4f) }
    var stepped by remember { mutableFloatStateOf(2f) }
    var ranged by remember { mutableFloatStateOf(30f) }
    var tinted by remember { mutableFloatStateOf(0.5f) }
    val disabled = 0.6f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.debug_screen_section_slider)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = LucideIcons.ArrowLeft,
                        contentDescription = stringResource(R.string.debug_top_app_bar_back),
                        modifier = Modifier.size(DebugSliderIconSize)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.debug_slider_hint),
                fontSize = DebugSliderLabelFontSize,
                color = currentTheme.mutedForeground
            )

            Spacer(modifier = Modifier.height(DebugSliderRowSpacing))

            DebugSliderRow(
                label = stringResource(R.string.debug_slider_continuous),
                value = "%.2f".format(continuous),
                currentTheme = currentTheme
            ) {
                Slider(value = continuous, onValueChange = { continuous = it })
            }

            Spacer(modifier = Modifier.height(DebugSliderRowSpacing))

            DebugSliderRow(
                label = stringResource(R.string.debug_slider_steps),
                value = stepped.toInt().toString(),
                currentTheme = currentTheme
            ) {
                Slider(
                    value = stepped,
                    onValueChange = { stepped = it },
                    valueRange = 0f..4f,
                    steps = 3
                )
            }

            Spacer(modifier = Modifier.height(DebugSliderRowSpacing))

            DebugSliderRow(
                label = stringResource(R.string.debug_slider_range),
                value = ranged.toInt().toString(),
                currentTheme = currentTheme
            ) {
                Slider(
                    value = ranged,
                    onValueChange = { ranged = it },
                    valueRange = 0f..100f
                )
            }

            Spacer(modifier = Modifier.height(DebugSliderRowSpacing))

            DebugSliderRow(
                label = stringResource(R.string.debug_slider_colors),
                value = "%.2f".format(tinted),
                currentTheme = currentTheme
            ) {
                Slider(
                    value = tinted,
                    onValueChange = { tinted = it },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        activeTickColor = MaterialTheme.colorScheme.onTertiary
                    )
                )
            }

            Spacer(modifier = Modifier.height(DebugSliderRowSpacing))

            DebugSliderRow(
                label = stringResource(R.string.debug_slider_disabled),
                value = "%.2f".format(disabled),
                currentTheme = currentTheme
            ) {
                Slider(value = disabled, onValueChange = {}, enabled = false)
            }
        }
    }
}

/** 一行：上面是「标签 + 当前值」，下面是滑块本身。 */
@Composable
private fun DebugSliderRow(
    label: String,
    value: String,
    currentTheme: CssVariables,
    slider: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label  ·  $value",
            fontSize = DebugSliderLabelFontSize,
            color = currentTheme.mutedForeground
        )
        Spacer(modifier = Modifier.height(4.dp))
        slider()
    }
}
