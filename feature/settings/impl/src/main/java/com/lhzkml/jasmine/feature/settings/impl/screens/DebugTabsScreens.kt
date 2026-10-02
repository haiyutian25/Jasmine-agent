package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.tabs.PrimaryScrollableTabRow
import com.lhzkml.jasmine.core.widgets.tabs.PrimaryTabRow
import com.lhzkml.jasmine.core.widgets.tabs.SecondaryTabRow
import com.lhzkml.jasmine.core.widgets.tabs.Tab
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugTabsVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 返回箭头尺寸、标签内图标尺寸、滚动版的两侧边缘内边距、内容区留白。 */
private val DebugTabsIconSize = 24.dp
private val DebugTabsTabIconSize = 20.dp
private val DebugTabsEdgePadding = 16.dp
private val DebugTabsContentPadding = 20.dp

/** 主要 / 次要两档的标签个数（最后一个演示禁用）；可滚动那档的个数。 */
private const val DebugTabsBasicCount = 3
private const val DebugTabsScrollableCount = 8

/**
 * `core:widgets` 标签页的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。四档：
 * 主要标签页（[PrimaryTabRow] ✓ 指示条在底部 ✓ 其中一个禁用 ✓）、次要标签页（[SecondaryTabRow] ✓
 * 只有一条细分割线 ✓）、带图标的标签页（[Tab] 同时给 icon 与 text ✓）、可滚动标签页
 *（[PrimaryScrollableTabRow] ✓ 8 个标签 ✓ 带 edgePadding ✓）。切换后下方内容区会显示当前档号 ✓。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTabsScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugTabsTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugTabsIconSize)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.debug_tabs_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(
                        start = DebugTabsContentPadding,
                        end = DebugTabsContentPadding,
                        top = 14.dp,
                        bottom = 14.dp
                    )
                )

                when (variant) {
                    SettingsDebugTabsVariant.SECONDARY ->
                        SecondaryTabRow(selectedTabIndex = selected) {
                            DebugTabsBasicTabs(selected = selected, onSelect = { selected = it })
                        }

                    SettingsDebugTabsVariant.ICON ->
                        PrimaryTabRow(selectedTabIndex = selected) {
                            repeat(DebugTabsBasicCount) { index ->
                                Tab(
                                    selected = index == selected,
                                    onClick = { selected = index },
                                    text = {
                                        Text(stringResource(R.string.debug_tabs_label, index + 1))
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = debugTabsIcon(index),
                                            contentDescription = null,
                                            modifier = Modifier.size(DebugTabsTabIconSize)
                                        )
                                    }
                                )
                            }
                        }

                    SettingsDebugTabsVariant.SCROLLABLE ->
                        PrimaryScrollableTabRow(
                            selectedTabIndex = selected,
                            edgePadding = DebugTabsEdgePadding
                        ) {
                            repeat(DebugTabsScrollableCount) { index ->
                                Tab(
                                    selected = index == selected,
                                    onClick = { selected = index },
                                    text = {
                                        Text(stringResource(R.string.debug_tabs_label, index + 1))
                                    }
                                )
                            }
                        }

                    else ->
                        PrimaryTabRow(selectedTabIndex = selected) {
                            DebugTabsBasicTabs(selected = selected, onSelect = { selected = it })
                        }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.debug_tabs_content, selected + 1),
                    fontSize = 15.sp,
                    color = currentTheme.foreground,
                    modifier = Modifier.padding(horizontal = DebugTabsContentPadding)
                )
            }
        }
    }
}

/** 主要 / 次要两档共用的三个文字标签（最后一个禁用，演示禁用态）。 */
@Composable
private fun DebugTabsBasicTabs(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    repeat(DebugTabsBasicCount) { index ->
        Tab(
            selected = index == selected,
            onClick = { onSelect(index) },
            text = { Text(stringResource(R.string.debug_tabs_label, index + 1)) },
            enabled = index != DebugTabsBasicCount - 1
        )
    }
}

/** 带图标那档的三个图标（用库里已有的 Lucide 图标）。 */
private fun debugTabsIcon(index: Int): ImageVector =
    when (index) {
        0 -> LucideIcons.Check
        1 -> LucideIcons.Settings
        else -> LucideIcons.Plus
    }

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugTabsTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugTabsVariant.SECONDARY -> R.string.debug_tabs_entry_secondary
        SettingsDebugTabsVariant.ICON -> R.string.debug_tabs_entry_icon
        SettingsDebugTabsVariant.SCROLLABLE -> R.string.debug_tabs_entry_scrollable
        else -> R.string.debug_tabs_entry_primary
    }
