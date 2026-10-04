package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.radio.RadioButton
import com.lhzkml.jasmine.core.widgets.radio.RadioButtonDefaults
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugRadioButtonVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 返回箭头尺寸、行间距、单选按钮与标签之间的间距、单选组每行高度。 */
private val DebugRadioIconSize = 24.dp
private val DebugRadioRowSpacing = 10.dp
private val DebugRadioLabelGap = 12.dp
private val DebugRadioGroupRowHeight = 48.dp

/** 单选组里的选项个数。 */
private const val DebugRadioOptionCount = 3

/**
 * `core:widgets` 单选按钮的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。四档：
 * 基本（单个可切换）、单选组（照官方 `RadioGroupSample`：整行可点 + `selectableGroup`）、
 * 启用 / 禁用矩阵、自定义配色（[RadioButtonDefaults.colors]）。
 */
@Composable
fun DebugRadioButtonScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var basicSelected by rememberSaveable { mutableStateOf(true) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugRadioButtonTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugRadioIconSize)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_radio_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(18.dp))

                when (variant) {
                    // 照官方 RadioGroupSample：整行可点，配 selectableGroup 让无障碍当成一组。
                    SettingsDebugRadioButtonVariant.GROUP ->
                        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                            repeat(DebugRadioOptionCount) { index ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(DebugRadioGroupRowHeight)
                                        .selectable(
                                            selected = index == selectedIndex,
                                            onClick = { selectedIndex = index },
                                            role = Role.RadioButton
                                        )
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = index == selectedIndex, onClick = null)
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.debug_radio_option,
                                                index + 1
                                            ),
                                        fontSize = 15.sp,
                                        color = currentTheme.foreground,
                                        modifier = Modifier.padding(start = DebugRadioLabelGap)
                                    )
                                }
                            }
                        }

                    SettingsDebugRadioButtonVariant.DISABLED -> {
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_enabled_selected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(selected = true, onClick = {}, enabled = true)
                        }
                        Spacer(modifier = Modifier.height(DebugRadioRowSpacing))
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_enabled_unselected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(selected = false, onClick = {}, enabled = true)
                        }
                        Spacer(modifier = Modifier.height(DebugRadioRowSpacing))
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_disabled_selected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(selected = true, onClick = null, enabled = false)
                        }
                        Spacer(modifier = Modifier.height(DebugRadioRowSpacing))
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_disabled_unselected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(selected = false, onClick = null, enabled = false)
                        }
                    }

                    SettingsDebugRadioButtonVariant.COLORS -> {
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_custom_selected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = null,
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = currentTheme.accent
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(DebugRadioRowSpacing))
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_custom_unselected),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = null,
                                colors =
                                    RadioButtonDefaults.colors(
                                        unselectedColor = currentTheme.accent
                                    )
                            )
                        }
                    }

                    else -> {
                        DebugRadioRow(
                            label = stringResource(R.string.debug_radio_basic_label),
                            currentTheme = currentTheme
                        ) {
                            RadioButton(
                                selected = basicSelected,
                                onClick = { basicSelected = !basicSelected }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一行：单选按钮 + 右侧标签。 */
@Composable
private fun DebugRadioRow(
    label: String,
    currentTheme: CssVariables,
    radio: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        radio()
        Text(
            text = label,
            fontSize = 15.sp,
            color = currentTheme.foreground,
            modifier = Modifier.padding(start = DebugRadioLabelGap)
        )
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugRadioButtonTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugRadioButtonVariant.GROUP -> R.string.debug_radio_entry_group
        SettingsDebugRadioButtonVariant.DISABLED -> R.string.debug_radio_entry_disabled
        SettingsDebugRadioButtonVariant.COLORS -> R.string.debug_radio_entry_colors
        else -> R.string.debug_radio_entry_basic
    }
