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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.badge.Badge
import com.lhzkml.jasmine.core.widgets.badge.BadgedBox
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugBadgeVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 锚点图标尺寸、返回箭头图标尺寸、间距。 */
private val DebugBadgeAnchorSize = 36.dp
private val DebugBadgeIconSize = 24.dp
private val DebugBadgeTopSpacing = 36.dp

/**
 * `core:widgets` 徽标的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。三档都是同一个
 * [Badge]（只是内容有无/长短不同），由 [BadgedBox] 挂到锚点图标的右上角 —— 徽标自己不接收点击，
 * 所以这一页只看位置与尺寸。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugBadgeScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugBadgeTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugBadgeIconSize)
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
                    text = stringResource(R.string.debug_badge_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(DebugBadgeTopSpacing))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BadgedBox(badge = { DebugBadgeContent(variant) }) {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = null,
                            tint = currentTheme.foreground,
                            modifier = Modifier.size(DebugBadgeAnchorSize)
                        )
                    }
                }
            }
        }
    }
}

/** 三档的差别只在内容：无内容 = 6dp 小圆点，有内容 = 16dp 高、随文字撑宽的胶囊。 */
@Composable
private fun DebugBadgeContent(variant: String) {
    when (variant) {
        SettingsDebugBadgeVariant.COUNT ->
            Badge { Text(stringResource(R.string.debug_badge_count)) }

        SettingsDebugBadgeVariant.LONG_COUNT ->
            Badge { Text(stringResource(R.string.debug_badge_long_count)) }

        else -> Badge()
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugBadgeTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugBadgeVariant.COUNT -> R.string.debug_badge_entry_count
        SettingsDebugBadgeVariant.LONG_COUNT -> R.string.debug_badge_entry_long_count
        else -> R.string.debug_badge_entry_dot
    }
