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
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.button.TextButton
import com.lhzkml.jasmine.core.widgets.navigation.NavigationBar
import com.lhzkml.jasmine.core.widgets.navigation.NavigationBarItem
import com.lhzkml.jasmine.feature.settings.impl.R

/** 演示用的五个条目（图标 + 标签）；底栏按 3 项或 5 项两种常见形态各渲染一次。 */
private val DebugBottomBarIcons: List<ImageVector> =
    listOf(
        LucideIcons.Menu,
        LucideIcons.ChartColumn,
        LucideIcons.Plus,
        LucideIcons.Terminal,
        LucideIcons.Settings
    )

private val DebugBottomBarLabels: List<Int> =
    listOf(
        R.string.debug_bottom_bar_item_one,
        R.string.debug_bottom_bar_item_two,
        R.string.debug_bottom_bar_item_three,
        R.string.debug_bottom_bar_item_four,
        R.string.debug_bottom_bar_item_five
    )

/** 内容区滚动的行数，以及 3 项 / 5 项两档。 */
private const val DebugBottomBarBodyRows = 20
private const val DebugBottomBarSmallCount = 3
private const val DebugBottomBarLargeCount = 5

private val DebugBottomBarIconSize = 24.dp
private val DebugBottomBarBodySpacing = 14.dp

/**
 * `core:widgets` **标准底部导航栏**的独立预览页。
 *
 * 和顶部栏/侧边栏那几页一样：整页**刻意不套**设置流那套 `SettingsPage` 顶栏，页面自己的顶栏是
 * `core:widgets` 的 [TopAppBar]；底栏用的是 `NavigationBar` 本体，固定在底部（自带系统栏内边距），
 * 点条目可切换选中态，上面那个按钮能在 **3 项 / 5 项** 之间切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugBottomBarScreen(
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(0) }
    var itemCount by remember { mutableIntStateOf(DebugBottomBarSmallCount) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.debug_screen_section_bottom_bar)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = LucideIcons.ArrowLeft,
                        contentDescription = stringResource(R.string.debug_top_app_bar_back),
                        modifier = Modifier.size(DebugBottomBarIconSize)
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
                text = stringResource(R.string.debug_bottom_bar_hint),
                fontSize = 12.sp,
                color = currentTheme.mutedForeground
            )

            Spacer(modifier = Modifier.height(DebugBottomBarBodySpacing))

            TextButton(
                onClick = {
                    val next =
                        if (itemCount == DebugBottomBarSmallCount) {
                            DebugBottomBarLargeCount
                        } else {
                            DebugBottomBarSmallCount
                        }
                    itemCount = next
                    if (selected >= next) selected = next - 1
                }
            ) {
                Text(
                    text = stringResource(
                        R.string.debug_bottom_bar_switch,
                        if (itemCount == DebugBottomBarSmallCount) {
                            DebugBottomBarLargeCount
                        } else {
                            DebugBottomBarSmallCount
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(DebugBottomBarBodySpacing))

            repeat(DebugBottomBarBodyRows) { index ->
                Text(
                    text = stringResource(R.string.debug_top_app_bar_body, index + 1),
                    fontSize = 15.sp,
                    color = currentTheme.foreground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }

        NavigationBar {
            repeat(itemCount) { index ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { selected = index },
                    icon = {
                        Icon(
                            imageVector = DebugBottomBarIcons[index],
                            contentDescription = null,
                            modifier = Modifier.size(DebugBottomBarIconSize)
                        )
                    },
                    label = { Text(stringResource(DebugBottomBarLabels[index])) }
                )
            }
        }
    }
}
