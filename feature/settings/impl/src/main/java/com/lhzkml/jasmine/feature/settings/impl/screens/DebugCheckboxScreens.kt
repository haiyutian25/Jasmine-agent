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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.checkbox.Checkbox
import com.lhzkml.jasmine.core.widgets.checkbox.CheckboxDefaults
import com.lhzkml.jasmine.core.widgets.checkbox.TriStateCheckbox
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugCheckboxVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 返回箭头尺寸、行间距、复选框与标签之间的间距。 */
private val DebugCheckboxIconSize = 24.dp
private val DebugCheckboxRowSpacing = 10.dp
private val DebugCheckboxLabelGap = 12.dp

/**
 * `core:widgets` 复选框的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。三档：
 * 两态（勾选 / 未勾选 / 禁用 / 禁用+勾选）、三态（父项 + 三个子项：点父项全开或全关，点子项会
 * 回到"部分选中"）、自定义配色（[CheckboxDefaults.colors]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCheckboxScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var checked by rememberSaveable { mutableStateOf(true) }
    var unchecked by rememberSaveable { mutableStateOf(false) }
    val children = remember { mutableStateListOf(true, true, true) }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugCheckboxTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugCheckboxIconSize)
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
                    text = stringResource(R.string.debug_checkbox_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(18.dp))

                when (variant) {
                    SettingsDebugCheckboxVariant.TRI_STATE -> {
                        val parentState =
                            when {
                                children.all { it } -> ToggleableState.On
                                children.none { it } -> ToggleableState.Off
                                else -> ToggleableState.Indeterminate
                            }
                        DebugCheckboxRow(
                            label = stringResource(debugCheckboxParentStateRes(parentState)),
                            currentTheme = currentTheme
                        ) {
                            TriStateCheckbox(
                                state = parentState,
                                onClick = {
                                    val next = parentState != ToggleableState.On
                                    for (index in children.indices) {
                                        children[index] = next
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))

                        children.forEachIndexed { index, childChecked ->
                            DebugCheckboxRow(
                                label = stringResource(R.string.debug_checkbox_child, index + 1),
                                currentTheme = currentTheme
                            ) {
                                Checkbox(
                                    checked = childChecked,
                                    onCheckedChange = { children[index] = it }
                                )
                            }
                            if (index < children.lastIndex) {
                                Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))
                            }
                        }
                    }

                    SettingsDebugCheckboxVariant.COLORS -> {
                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_custom_on),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = null,
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = currentTheme.accent,
                                        checkmarkColor = currentTheme.accentForeground
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))

                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_custom_off),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = null,
                                colors =
                                    CheckboxDefaults.colors(
                                        uncheckedColor = currentTheme.accent
                                    )
                            )
                        }
                    }

                    else -> {
                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_checked),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { checked = it })
                        }

                        Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))

                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_unchecked),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(checked = unchecked, onCheckedChange = { unchecked = it })
                        }

                        Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))

                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_disabled),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(checked = false, onCheckedChange = null, enabled = false)
                        }

                        Spacer(modifier = Modifier.height(DebugCheckboxRowSpacing))

                        DebugCheckboxRow(
                            label = stringResource(R.string.debug_checkbox_disabled_checked),
                            currentTheme = currentTheme
                        ) {
                            Checkbox(checked = true, onCheckedChange = null, enabled = false)
                        }
                    }
                }
            }
        }
    }
}

/** 一行：复选框 + 右侧标签。 */
@Composable
private fun DebugCheckboxRow(
    label: String,
    currentTheme: CssVariables,
    checkbox: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        checkbox()
        Text(
            text = label,
            fontSize = 15.sp,
            color = currentTheme.foreground,
            modifier = Modifier.padding(start = DebugCheckboxLabelGap)
        )
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugCheckboxTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugCheckboxVariant.TRI_STATE -> R.string.debug_checkbox_entry_tri_state
        SettingsDebugCheckboxVariant.COLORS -> R.string.debug_checkbox_entry_colors
        else -> R.string.debug_checkbox_entry_two_state
    }

/** 父项三态对应的文案。 */
private fun debugCheckboxParentStateRes(state: ToggleableState): Int =
    when (state) {
        ToggleableState.On -> R.string.debug_checkbox_parent_state_on
        ToggleableState.Off -> R.string.debug_checkbox_parent_state_off
        else -> R.string.debug_checkbox_parent_state_mixed
    }
