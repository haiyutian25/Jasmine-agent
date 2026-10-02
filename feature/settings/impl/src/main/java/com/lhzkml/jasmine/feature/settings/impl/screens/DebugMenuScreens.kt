package com.lhzkml.jasmine.feature.settings.impl.screens

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.menu.DropdownMenu
import com.lhzkml.jasmine.core.widgets.menu.DropdownMenuItem
import com.lhzkml.jasmine.core.widgets.menu.ExposedDropdownMenuAnchorType
import com.lhzkml.jasmine.core.widgets.menu.ExposedDropdownMenuBox
import com.lhzkml.jasmine.core.widgets.menu.ExposedDropdownMenuDefaults
import com.lhzkml.jasmine.core.widgets.menu.MenuDefaults
import com.lhzkml.jasmine.core.widgets.textfield.OutlinedTextField
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugMenuVariant
import com.lhzkml.jasmine.feature.settings.impl.R
import androidx.compose.foundation.shape.RoundedCornerShape

/** 返回箭头尺寸、行间距、下拉箭头的图标尺寸、长菜单的行数。 */
private val DebugMenuIconSize = 24.dp
private val DebugMenuSpacing = 18.dp
private val DebugMenuLeadingIconSize = 20.dp
private val DebugMenuOffset = DpOffset(0.dp, 8.dp)
private val DebugMenuBorderWidth = 1.dp

/** 长菜单那一档的行数。 */
private const val DebugMenuScrollRowCount = 24

/**
 * `core:widgets` 菜单的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。四档：
 * 基本（[DropdownMenu] + [DropdownMenuItem]：普通 / 带前图标 / 禁用 / 带快捷键提示）、
 * 长菜单（配 `scrollState`）、Exposed 下拉（只读锚点 + 可编辑锚点，走 `menuAnchor` 与
 * [ExposedDropdownMenuAnchorType]）、自定义外观（形状 / 描边 / 底色 / 偏移）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var exposedExpanded by rememberSaveable { mutableStateOf(false) }
    var editableExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var editableText by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugMenuTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugMenuIconSize)
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
                    text = stringResource(R.string.debug_menu_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(DebugMenuSpacing))

                when (variant) {
                    // 长菜单：把外部 scrollState 交给 DropdownMenu，菜单内容可滚动。
                    SettingsDebugMenuVariant.SCROLL -> {
                        Box {
                            Button(onClick = { expanded = true }) {
                                Text(stringResource(R.string.debug_menu_open))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                scrollState = scrollState
                            ) {
                                repeat(DebugMenuScrollRowCount) { index ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.debug_menu_item_row,
                                                    index + 1
                                                )
                                            )
                                        },
                                        onClick = { expanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Exposed 下拉：一个只读锚点（PrimaryNotEditable）+ 一个可编辑锚点（PrimaryEditable）。
                    SettingsDebugMenuVariant.EXPOSED -> {
                        val options =
                            listOf(
                                stringResource(R.string.debug_menu_option, 1),
                                stringResource(R.string.debug_menu_option, 2),
                                stringResource(R.string.debug_menu_option, 3),
                            )
                        ExposedDropdownMenuBox(
                            expanded = exposedExpanded,
                            onExpandedChange = { exposedExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = options[selectedIndex],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.debug_menu_label)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(exposedExpanded)
                                },
                                modifier =
                                    Modifier.menuAnchor(
                                            ExposedDropdownMenuAnchorType.PrimaryNotEditable
                                        )
                                        .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = exposedExpanded,
                                onDismissRequest = { exposedExpanded = false }
                            ) {
                                options.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedIndex = index
                                            exposedExpanded = false
                                        },
                                        contentPadding =
                                            ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(DebugMenuSpacing))

                        ExposedDropdownMenuBox(
                            expanded = editableExpanded,
                            onExpandedChange = { editableExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                label = { Text(stringResource(R.string.debug_menu_editable_label)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(editableExpanded)
                                },
                                modifier =
                                    Modifier.menuAnchor(
                                            ExposedDropdownMenuAnchorType.PrimaryEditable
                                        )
                                        .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = editableExpanded,
                                onDismissRequest = { editableExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(options[0]) },
                                    onClick = {
                                        editableText = options[0]
                                        editableExpanded = false
                                    },
                                    contentPadding =
                                        ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // 自定义外观：形状 / 描边 / 底色 / 偏移都从主题取。
                    SettingsDebugMenuVariant.CUSTOM -> {
                        Box {
                            Button(onClick = { expanded = true }) {
                                Text(stringResource(R.string.debug_menu_open))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                offset = DebugMenuOffset,
                                shape = AppShapes.medium,
                                containerColor = currentTheme.card,
                                shadowElevation = MenuDefaults.ShadowElevation,
                                border = BorderStroke(DebugMenuBorderWidth, currentTheme.border)
                            ) {
                                DebugMenuBasicItems(
                                    currentTheme = currentTheme,
                                    onDismiss = { expanded = false }
                                )
                            }
                        }
                    }

                    // 基本：普通 / 带前图标 / 禁用 / 带快捷键提示。
                    else -> {
                        // 关键：按钮和菜单放进同一个「贴紧的 Box」，菜单的父节点边界才等于按钮，
                        // 于是贴着按钮弹出（照官方 MenuSample 的写法）。直接放在下面的滚动 Column 里，
                        // 父节点边界会变成整个可滚区域，菜单就会跑到屏幕底部。
                        Box {
                            Button(onClick = { expanded = true }) {
                                Text(stringResource(R.string.debug_menu_open))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DebugMenuBasicItems(
                                    currentTheme = currentTheme,
                                    onDismiss = { expanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 基本四项：普通 / 带前图标 / 禁用 / 带快捷键提示（尾部放一个 Text）。 */
@Composable
private fun DebugMenuBasicItems(
    currentTheme: CssVariables,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.debug_menu_item_plain)) },
        onClick = onDismiss
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.debug_menu_item_with_icon)) },
        onClick = onDismiss,
        leadingIcon = {
            Icon(
                imageVector = LucideIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(DebugMenuLeadingIconSize)
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.debug_menu_item_disabled)) },
        onClick = {},
        enabled = false
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.debug_menu_item_shortcut)) },
        onClick = onDismiss,
        trailingIcon = { Text(stringResource(R.string.debug_menu_shortcut)) }
    )
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugMenuTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugMenuVariant.SCROLL -> R.string.debug_menu_entry_scroll
        SettingsDebugMenuVariant.EXPOSED -> R.string.debug_menu_entry_exposed
        SettingsDebugMenuVariant.CUSTOM -> R.string.debug_menu_entry_custom
        else -> R.string.debug_menu_entry_basic
    }
