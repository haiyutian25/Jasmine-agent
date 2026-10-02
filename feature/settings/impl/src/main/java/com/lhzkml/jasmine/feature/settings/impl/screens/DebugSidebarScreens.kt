package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.navigation.DismissibleDrawerSheet
import com.lhzkml.jasmine.core.widgets.navigation.DismissibleNavigationDrawer
import com.lhzkml.jasmine.core.widgets.navigation.DrawerValue
import com.lhzkml.jasmine.core.widgets.navigation.NavigationDrawerItem
import com.lhzkml.jasmine.core.widgets.navigation.PermanentDrawerSheet
import com.lhzkml.jasmine.core.widgets.navigation.PermanentNavigationDrawer
import com.lhzkml.jasmine.core.widgets.navigation.rememberDrawerState
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugSidebarVariant
import com.lhzkml.jasmine.feature.settings.impl.R
import kotlinx.coroutines.launch

/** 演示用的三个条目：图标 + 标签。 */
private val DebugSidebarIcons: List<ImageVector> =
    listOf(LucideIcons.Settings, LucideIcons.Plus, LucideIcons.Menu)

private val DebugSidebarLabels: List<Int> =
    listOf(
        R.string.debug_sidebar_item_one,
        R.string.debug_sidebar_item_two,
        R.string.debug_sidebar_item_three
    )

/** 右侧内容区滚动的行数。 */
private const val DebugSidebarBodyRows = 20

/** 图标尺寸与行距。 */
private val DebugSidebarIconSize = 24.dp
private val DebugSidebarBodySpacing = 14.dp

/**
 * `core:widgets` 侧边栏变体的**独立预览页**：可推开抽屉 / 常驻抽屉，一共 2 种。
 *
 * 和顶部栏那几页一样：整页**刻意不套**设置流那套 `SettingsPage` 顶栏，页面自己的顶栏就是
 * `core:widgets` 的 [TopAppBar]；页内列表可滚动，侧边栏点得动、能开合。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSidebarScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var selected by remember { mutableIntStateOf(0) }

    val drawerItems: @Composable () -> Unit = {
        DebugDrawerItems(
            selected = selected,
            onSelect = { selected = it },
            currentTheme = currentTheme
        )
    }

    when (variant) {
        SettingsDebugSidebarVariant.DISMISSIBLE_DRAWER ->
            DismissibleNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { DismissibleDrawerSheet { drawerItems() } }
            ) {
                DebugSidebarBody(
                    title = stringResource(R.string.debug_sidebar_dismissible_drawer),
                    currentTheme = currentTheme,
                    onBack = onBack,
                    onMenu = { scope.launch { drawerState.open() } },
                    modifier = modifier
                )
            }

        else ->
            PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet { drawerItems() } }) {
                DebugSidebarBody(
                    title = stringResource(R.string.debug_sidebar_permanent_drawer),
                    currentTheme = currentTheme,
                    onBack = onBack,
                    modifier = modifier
                )
            }
    }
}

/** 页面主体：我们自己的小号顶栏 + 一段可滚动的内容（和顶部栏那几页同一个形状）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSidebarBody(
    title: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = LucideIcons.ArrowLeft,
                        contentDescription = stringResource(R.string.debug_top_app_bar_back),
                        modifier = Modifier.size(DebugSidebarIconSize)
                    )
                }
            },
            actions = {
                if (onMenu != null) {
                    IconButton(onClick = onMenu) {
                        Icon(
                            imageVector = LucideIcons.Menu,
                            contentDescription = stringResource(R.string.debug_sidebar_open),
                            modifier = Modifier.size(DebugSidebarIconSize)
                        )
                    }
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
                text = stringResource(R.string.debug_sidebar_hint),
                fontSize = 12.sp,
                color = currentTheme.mutedForeground
            )

            Spacer(modifier = Modifier.height(DebugSidebarBodySpacing))

            repeat(DebugSidebarBodyRows) { index ->
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
    }
}

/** 抽屉里的条目（`NavigationDrawerItem`）。 */
@Composable
private fun DebugDrawerItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    currentTheme: CssVariables
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.debug_sidebar_drawer_headline),
            fontSize = 12.sp,
            color = currentTheme.mutedForeground,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
        )
        DebugSidebarLabels.forEachIndexed { index, labelRes ->
            NavigationDrawerItem(
                label = { Text(stringResource(labelRes)) },
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = DebugSidebarIcons[index],
                        contentDescription = null,
                        modifier = Modifier.size(DebugSidebarIconSize)
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
