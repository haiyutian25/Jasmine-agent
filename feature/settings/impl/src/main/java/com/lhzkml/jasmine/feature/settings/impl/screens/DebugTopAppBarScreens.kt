package com.lhzkml.jasmine.feature.settings.impl.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.CenterAlignedTopAppBar
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBarDefaults
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBarScrollBehavior
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugTopAppBarVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 演示列表的行数：够长才能真正滚动、把中号/大号顶部栏折叠起来。 */
private const val DebugTopAppBarRowCount = 30

/** 行间距；返回箭头图标尺寸。 */
private val DebugTopAppBarRowSpacing = 14.dp
private val DebugTopAppBarIconSize = 24.dp

/**
 * `core:widgets` 顶部栏变体的**独立预览页**。
 *
 * 页面自己的顶栏就是被测的那个组件（[TopAppBar] / [CenterAlignedTopAppBar]），**刻意不套**设置流
 * 那套 `SettingsPage` 顶栏 —— 这一页只属于组件调试，不参与 app 现有的顶部导航。用的是常驻的
 * `pinned` 滚动行为：列表滚动时顶栏留在原位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTopAppBarScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = stringResource(debugTopAppBarTitleRes(variant))
    val navigationIcon: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = LucideIcons.ArrowLeft,
                contentDescription = stringResource(R.string.debug_top_app_bar_back),
                modifier = Modifier.size(DebugTopAppBarIconSize)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (variant) {
                SettingsDebugTopAppBarVariant.CENTER_ALIGNED ->
                    CenterAlignedTopAppBar(
                        title = { Text(title) },
                        navigationIcon = navigationIcon,
                        scrollBehavior = scrollBehavior
                    )

                else ->
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = navigationIcon,
                        scrollBehavior = scrollBehavior
                    )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_top_app_bar_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(DebugTopAppBarRowSpacing))

                repeat(DebugTopAppBarRowCount) { index ->
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
}

/** 页面标题就取变体名（复用调试页里那几个标签）。 */
private fun debugTopAppBarTitleRes(variant: String): Int = when (variant) {
    SettingsDebugTopAppBarVariant.CENTER_ALIGNED -> R.string.debug_top_app_bar_center_aligned
    else -> R.string.debug_top_app_bar_small
}
