package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.activity.compose.BackHandler
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
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.fabmenu.FloatingActionButtonMenu
import com.lhzkml.jasmine.core.widgets.fabmenu.FloatingActionButtonMenuItem
import com.lhzkml.jasmine.core.widgets.fabmenu.FloatingActionButtonMenuScope
import com.lhzkml.jasmine.core.widgets.fabmenu.ToggleFloatingActionButton
import com.lhzkml.jasmine.core.widgets.fabmenu.ToggleFloatingActionButtonDefaults
import com.lhzkml.jasmine.core.widgets.fabmenu.ToggleFloatingActionButtonDefaults.animateIcon
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugFabMenuVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 演示列表的行数：够长才像真页面。 */
private const val DebugFabMenuRowCount = 24

/** 行间距；返回箭头与菜单条目里的图标尺寸。 */
private val DebugFabMenuRowSpacing = 14.dp
private val DebugFabMenuIconSize = 24.dp

/**
 * `core:widgets` FAB 菜单的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏 —— 这一页只属于
 * 组件调试，不参与 app 现有的顶部导航。
 *
 * 三档只改**按钮本体**（尺寸 / 圆角 / 图标尺寸成对取），菜单条目固定 56dp —— 上游就是这么做的：
 * `FloatingActionButtonMenu` 的条目走 `FabMenuBaselineTokens`，只有 `ToggleFloatingActionButton`
 * 跟着 FAB 的 baseline / medium / large 三档走。
 */
@Composable
fun DebugFabMenuScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 展开时按返回键先收起菜单（与真机上的 FAB 菜单一致）。
    BackHandler(enabled = expanded) { expanded = false }

    val containerSize: (Float) -> Dp =
        when (variant) {
            SettingsDebugFabMenuVariant.MEDIUM ->
                ToggleFloatingActionButtonDefaults.containerSizeMedium()
            SettingsDebugFabMenuVariant.LARGE ->
                ToggleFloatingActionButtonDefaults.containerSizeLarge()
            else -> ToggleFloatingActionButtonDefaults.containerSize()
        }
    val containerCornerRadius: (Float) -> Dp =
        when (variant) {
            SettingsDebugFabMenuVariant.MEDIUM ->
                ToggleFloatingActionButtonDefaults.containerCornerRadiusMedium()
            SettingsDebugFabMenuVariant.LARGE ->
                ToggleFloatingActionButtonDefaults.containerCornerRadiusLarge()
            else -> ToggleFloatingActionButtonDefaults.containerCornerRadius()
        }
    val iconSize: (Float) -> Dp =
        when (variant) {
            SettingsDebugFabMenuVariant.MEDIUM ->
                ToggleFloatingActionButtonDefaults.iconSizeMedium()
            SettingsDebugFabMenuVariant.LARGE -> ToggleFloatingActionButtonDefaults.iconSizeLarge()
            else -> ToggleFloatingActionButtonDefaults.iconSize()
        }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugFabMenuTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugFabMenuIconSize)
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
                    text = stringResource(R.string.debug_fab_menu_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(DebugFabMenuRowSpacing))

                repeat(DebugFabMenuRowCount) { index ->
                    Text(
                        text = stringResource(R.string.debug_fab_menu_body, index + 1),
                        fontSize = 15.sp,
                        color = currentTheme.foreground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        FloatingActionButtonMenu(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = !expanded },
                    containerSize = containerSize,
                    containerCornerRadius = containerCornerRadius
                ) {
                    // 过半就把加号换成关闭（和上游样例一样：形状/尺寸/颜色都由 progress 驱动）。
                    Icon(
                        imageVector =
                            if (checkedProgress > 0.5f) LucideIcons.Close else LucideIcons.Plus,
                        contentDescription = stringResource(R.string.debug_fab_menu_toggle),
                        modifier = Modifier.animateIcon({ checkedProgress }, size = iconSize)
                    )
                }
            }
        ) {
            DebugFabMenuItems()
        }
    }
}

/** 菜单条目：4 条固定 56dp 的胶囊条目（不随档位变）。 */
@Composable
private fun FloatingActionButtonMenuScope.DebugFabMenuItems() {
    val items =
        listOf(
            LucideIcons.Copy to R.string.debug_fab_menu_item_copy,
            LucideIcons.Download to R.string.debug_fab_menu_item_download,
            LucideIcons.Upload to R.string.debug_fab_menu_item_upload,
            LucideIcons.Trash to R.string.debug_fab_menu_item_delete,
        )
    items.forEach { (icon, labelRes) ->
        FloatingActionButtonMenuItem(
            onClick = {},
            text = { Text(stringResource(labelRes)) },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(DebugFabMenuIconSize)
                )
            }
        )
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugFabMenuTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugFabMenuVariant.MEDIUM -> R.string.debug_fab_menu_entry_medium
        SettingsDebugFabMenuVariant.LARGE -> R.string.debug_fab_menu_entry_large
        else -> R.string.debug_fab_menu_entry_baseline
    }
