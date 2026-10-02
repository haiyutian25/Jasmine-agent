package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.card.Card
import com.lhzkml.jasmine.core.widgets.card.CardDefaults
import com.lhzkml.jasmine.core.widgets.card.ElevatedCard
import com.lhzkml.jasmine.core.widgets.card.OutlinedCard
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugCardVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 返回箭头尺寸、卡片内边距、卡片之间的间距、标题/正文行距。 */
private val DebugCardIconSize = 24.dp
private val DebugCardInnerPadding = 16.dp
private val DebugCardSpacing = 14.dp
private val DebugCardTitleSize = 16.sp
private val DebugCardBodySize = 14.sp

/**
 * `core:widgets` 卡片的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。四档：
 * 填充卡片（[Card] ✓ 用 `FilledCardTokens` ✓）、抬升卡片（[ElevatedCard] ✓ 带阴影 ✓）、
 * 描边卡片（[OutlinedCard] ✓ 一圈 1dp 描边 ✓）、以及可点击 / 禁用 / 自定义底色
 *（走 `Card(onClick = …)`、`enabled = false` 与 [CardDefaults.cardColors]）✓。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCardScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var taps by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugCardTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugCardIconSize)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(DebugCardSpacing)
            ) {
                Text(
                    text = stringResource(R.string.debug_card_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                when (variant) {
                    SettingsDebugCardVariant.ELEVATED -> {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            DebugCardBody(title = stringResource(R.string.debug_card_title))
                        }
                    }

                    SettingsDebugCardVariant.OUTLINED -> {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            DebugCardBody(title = stringResource(R.string.debug_card_title))
                        }
                    }

                    SettingsDebugCardVariant.INTERACTIVE -> {
                        // 可点击：点了计数会变，带涟漪。
                        Card(
                            onClick = { taps++ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DebugCardBody(
                                title = stringResource(R.string.debug_card_title),
                                body = stringResource(R.string.debug_card_taps, taps)
                            )
                        }
                        // 禁用：不响应点击，容器色按 38% 不透明度压到表面色上。
                        Card(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DebugCardBody(title = stringResource(R.string.debug_card_disabled))
                        }
                        // 自定义底色：走 CardDefaults.cardColors(...)。
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                        ) {
                            DebugCardBody(title = stringResource(R.string.debug_card_custom))
                        }
                    }

                    else -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            DebugCardBody(title = stringResource(R.string.debug_card_title))
                        }
                    }
                }
            }
        }
    }
}

/** 卡片里统一的「标题 + 正文」两行（正文可替换成别的文案）。 */
@Composable
private fun DebugCardBody(
    title: String,
    body: String = stringResource(R.string.debug_card_body)
) {
    Column(modifier = Modifier.padding(DebugCardInnerPadding)) {
        Text(
            text = title,
            fontSize = DebugCardTitleSize,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            fontSize = DebugCardBodySize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugCardTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugCardVariant.ELEVATED -> R.string.debug_card_entry_elevated
        SettingsDebugCardVariant.OUTLINED -> R.string.debug_card_entry_outlined
        SettingsDebugCardVariant.INTERACTIVE -> R.string.debug_card_entry_interactive
        else -> R.string.debug_card_entry_filled
    }
