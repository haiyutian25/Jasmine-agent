package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.bottomsheet.ModalBottomSheet
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.button.TextButton
import com.lhzkml.jasmine.core.widgets.switch.Switch
import com.lhzkml.jasmine.core.widgets.switch.SwitchDefaults
import com.lhzkml.jasmine.core.widgets.textfield.OutlinedTextField
import com.lhzkml.jasmine.core.widgets.textfield.TextField
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugSidebarVariant
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugTopAppBarVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** Row label column width; padding and spacing inside a group card. */
private val DebugLabelWidth = 92.dp
private val DebugGroupSpacing = 16.dp
private val DebugRowPaddingHorizontal = 16.dp
private val DebugRowPaddingVertical = 8.dp
private val DebugGroupBottomPadding = 12.dp

/** Title typography; gap above a sub-title inside a group card. */
private val DebugTitleFontSize = 12.sp
private val DebugSubTitleTopPadding = 10.dp

/**
 * 组件层（`core:widgets`）的独立调试页：每个状态下把自建组件各渲一遍，只为在真机上看真实效果。
 * 按钮点了不做事、输入框只接受输入，都不读任何业务状态 —— 它和聊天链路、和别的任何页面都没有关系。
 */
@Composable
fun DebugComponentsScreen(
    currentTheme: CssVariables,
    onOpenTopAppBar: (String) -> Unit,
    onOpenSidebar: (String) -> Unit,
    onOpenBottomBar: () -> Unit,
    onOpenSlider: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DebugStateGroup(
            title = stringResource(R.string.debug_screen_section_enabled),
            enabled = true,
            currentTheme = currentTheme,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugStateGroup(
            title = stringResource(R.string.debug_screen_section_disabled),
            enabled = false,
            currentTheme = currentTheme,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugSheetGroup(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugSwitchGroup(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        // 顶部栏：这一组只是入口，点进去是各自独立的页面（页面自己的顶栏就是被测的组件）。
        DebugTopAppBarGroup(
            currentTheme = currentTheme,
            onOpen = onOpenTopAppBar,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        // 侧边栏：同样只是入口，点进去是各自独立的页面。
        DebugSidebarGroup(
            currentTheme = currentTheme,
            onOpen = onOpenSidebar,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugBottomBarGroup(
            currentTheme = currentTheme,
            onOpen = onOpenBottomBar,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DebugGroupSpacing))

        DebugSliderGroup(
            currentTheme = currentTheme,
            onOpen = onOpenSlider,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 一组卡片：给定状态下，按钮与输入框各渲一遍。 */
@Composable
private fun DebugStateGroup(
    title: String,
    enabled: Boolean,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = title,
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugSubTitle(
            text = stringResource(R.string.debug_screen_section_buttons),
            currentTheme = currentTheme
        )
        DebugRow(label = "Filled", currentTheme = currentTheme) {
            Button(onClick = {}, enabled = enabled) { Text("Button") }
        }
        DebugRow(label = "Text", currentTheme = currentTheme) {
            TextButton(onClick = {}, enabled = enabled) { Text("Button") }
        }

        DebugSubTitle(
            text = stringResource(R.string.debug_screen_section_text_fields),
            currentTheme = currentTheme
        )
        DebugFieldRow(label = "Filled", currentTheme = currentTheme) {
            DebugTextField(label = "Filled", enabled = enabled, outlined = false)
        }
        DebugFieldRow(label = "Outlined", currentTheme = currentTheme) {
            DebugTextField(label = "Outlined", enabled = enabled, outlined = true)
        }

        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 底部弹层：点「打开」从底部弹出（带遮罩）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSheetGroup(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    var showSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_sheets),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugRow(label = "Modal", currentTheme = currentTheme) {
            Button(onClick = { showSheet = true }) {
                Text(stringResource(R.string.debug_screen_sheet_open))
            }
        }

        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Text(
                text = "Modal bottom sheet",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            Text(
                text = "下拉、点遮罩、按返回键都能关",
                fontSize = DebugTitleFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

/** 组内的小标题（按钮 / 输入框）。 */
@Composable
private fun DebugSubTitle(
    text: String,
    currentTheme: CssVariables
) {
    Text(
        text = text,
        fontSize = DebugTitleFontSize,
        color = currentTheme.mutedForeground,
        modifier = Modifier.padding(
            start = DebugRowPaddingHorizontal,
            top = DebugSubTitleTopPadding,
            bottom = DebugRowPaddingVertical
        )
    )
}

/** 一个按钮行：固定宽度的标签列 + 按钮本身。 */
@Composable
private fun DebugRow(
    label: String,
    currentTheme: CssVariables,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DebugRowPaddingHorizontal,
                vertical = DebugRowPaddingVertical
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = DebugTitleFontSize,
            color = currentTheme.mutedForeground,
            modifier = Modifier.width(DebugLabelWidth)
        )
        content()
    }
}

/** 一个输入框行：标签在上、输入框占满整行（输入框本身有宽度）。 */
@Composable
private fun DebugFieldRow(
    label: String,
    currentTheme: CssVariables,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DebugRowPaddingHorizontal,
                vertical = DebugRowPaddingVertical
            )
    ) {
        Text(
            text = label,
            fontSize = DebugTitleFontSize,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(bottom = DebugRowPaddingVertical)
        )
        content()
    }
}

/** 输入框样本：自带一份输入状态（只为能真的敲字，不接任何业务）。 */
@Composable
private fun DebugTextField(
    label: String,
    enabled: Boolean,
    outlined: Boolean
) {
    var text by remember { mutableStateOf("") }
    if (outlined) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Text") },
            singleLine = true
        )
    } else {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Text") },
            singleLine = true
        )
    }
}

/** 滑动开关：开 / 关 × 可用 / 禁用，外加一个带缩略图标的变体。 */
@Composable
private fun DebugSwitchGroup(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    var onState by remember { mutableStateOf(true) }
    var offState by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_switches),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugRow(label = "On", currentTheme = currentTheme) {
            Switch(checked = onState, onCheckedChange = { onState = it })
        }
        DebugRow(label = "Off", currentTheme = currentTheme) {
            Switch(checked = offState, onCheckedChange = { offState = it })
        }
        DebugRow(label = "Disabled on", currentTheme = currentTheme) {
            Switch(checked = true, onCheckedChange = null, enabled = false)
        }
        DebugRow(label = "Disabled off", currentTheme = currentTheme) {
            Switch(checked = false, onCheckedChange = null, enabled = false)
        }
        DebugRow(label = "With icon", currentTheme = currentTheme) {
            Switch(
                checked = onState,
                onCheckedChange = { onState = it },
                thumbContent = {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 顶部栏变体入口：点进去是各自**独立**的页面（不套设置流那套顶栏）。 */
@Composable
private fun DebugTopAppBarGroup(
    currentTheme: CssVariables,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_top_app_bar),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_top_app_bar_small),
            currentTheme = currentTheme,
            onClick = { onOpen(SettingsDebugTopAppBarVariant.SMALL) }
        )
        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_top_app_bar_center_aligned),
            currentTheme = currentTheme,
            onClick = { onOpen(SettingsDebugTopAppBarVariant.CENTER_ALIGNED) }
        )

        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 一个顶部栏入口行：标签 + 右侧「打开」。 */
@Composable
private fun DebugTopAppBarEntry(
    label: String,
    currentTheme: CssVariables,
    onClick: () -> Unit
) {
    DebugRow(label = label, currentTheme = currentTheme) {
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.debug_top_app_bar_open))
        }
    }
}

/** 侧边栏变体入口：6 种形态，各自一个独立页面。 */
@Composable
private fun DebugSidebarGroup(
    currentTheme: CssVariables,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_sidebar),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_sidebar_dismissible_drawer),
            currentTheme = currentTheme,
            onClick = { onOpen(SettingsDebugSidebarVariant.DISMISSIBLE_DRAWER) }
        )
        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_sidebar_permanent_drawer),
            currentTheme = currentTheme,
            onClick = { onOpen(SettingsDebugSidebarVariant.PERMANENT_DRAWER) }
        )
        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 底部导航栏入口：标准底栏一个变体，占一整页。 */
@Composable
private fun DebugBottomBarGroup(
    currentTheme: CssVariables,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_bottom_bar),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_bottom_bar_entry),
            currentTheme = currentTheme,
            onClick = onOpen
        )
        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}

/** 滑块入口：标准（单值）滑块一个变体，占一整页。 */
@Composable
private fun DebugSliderGroup(
    currentTheme: CssVariables,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_section_slider),
            fontSize = DebugTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(
                start = DebugRowPaddingHorizontal,
                top = DebugRowPaddingHorizontal,
                bottom = DebugRowPaddingVertical
            )
        )

        DebugTopAppBarEntry(
            label = stringResource(R.string.debug_slider_entry),
            currentTheme = currentTheme,
            onClick = onOpen
        )
        Spacer(modifier = Modifier.height(DebugGroupBottomPadding))
    }
}
