package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.button.Button
import com.lhzkml.jasmine.core.widgets.progress.CircularProgressIndicator
import com.lhzkml.jasmine.core.widgets.progress.CircularWavyProgressIndicator
import com.lhzkml.jasmine.core.widgets.progress.LinearProgressIndicator
import com.lhzkml.jasmine.core.widgets.progress.LinearWavyProgressIndicator
import com.lhzkml.jasmine.core.widgets.pulltorefresh.PullToRefreshBox
import com.lhzkml.jasmine.core.widgets.pulltorefresh.PullToRefreshDefaults
import com.lhzkml.jasmine.core.widgets.pulltorefresh.rememberPullToRefreshState
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugProgressVariant
import com.lhzkml.jasmine.feature.settings.impl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 返回箭头尺寸、行间距、每档推进一次的步长与"刷新"时长。 */
private val DebugProgressIconSize = 24.dp
private val DebugProgressSpacing = 22.dp
private const val DebugProgressStep = 0.2f
private const val DebugProgressRefreshMillis = 1500L

/** 下拉刷新那档的列表行数。 */
private const val DebugProgressRowCount = 24

/**
 * `core:widgets` 进度指示器 / 下拉刷新的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，**刻意不套**设置流那套 `SettingsPage` 顶栏。三档：
 * 线性（确定 / 不确定 + 一个"推进"按钮）、圆形（确定 / 不确定）、下拉刷新（[PullToRefreshBox]
 * 包着一个可滚列表，松手后转 1.5 秒）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugProgressScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize().background(currentTheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugProgressTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugProgressIconSize)
                        )
                    }
                }
            )

            when (variant) {
                SettingsDebugProgressVariant.CIRCULAR ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(DebugProgressSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_progress_hint),
                            fontSize = 12.sp,
                            color = currentTheme.mutedForeground
                        )
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_determinate),
                            currentTheme = currentTheme
                        ) {
                            CircularProgressIndicator(progress = { progress })
                        }
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_indeterminate),
                            currentTheme = currentTheme
                        ) {
                            CircularProgressIndicator()
                        }
                        Button(
                            onClick = {
                                progress = if (progress >= 1f) 0f else progress + DebugProgressStep
                            }
                        ) {
                            Text(stringResource(R.string.debug_progress_advance))
                        }
                    }

                SettingsDebugProgressVariant.PULL_TO_REFRESH ->
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                delay(DebugProgressRefreshMillis)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.debug_progress_pull_hint),
                                fontSize = 12.sp,
                                color = currentTheme.mutedForeground
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            repeat(DebugProgressRowCount) { index ->
                                Text(
                                    text =
                                        stringResource(R.string.debug_progress_row, index + 1),
                                    fontSize = 15.sp,
                                    color = currentTheme.foreground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                SettingsDebugProgressVariant.WAVY_LINEAR ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(DebugProgressSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_progress_hint),
                            fontSize = 12.sp,
                            color = currentTheme.mutedForeground
                        )
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_determinate),
                            currentTheme = currentTheme
                        ) {
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_indeterminate),
                            currentTheme = currentTheme
                        ) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Button(
                            onClick = {
                                progress = if (progress >= 1f) 0f else progress + DebugProgressStep
                            }
                        ) {
                            Text(stringResource(R.string.debug_progress_advance))
                        }
                    }

                SettingsDebugProgressVariant.WAVY_CIRCULAR ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(DebugProgressSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_progress_hint),
                            fontSize = 12.sp,
                            color = currentTheme.mutedForeground
                        )
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_determinate),
                            currentTheme = currentTheme
                        ) {
                            CircularWavyProgressIndicator(progress = { progress })
                        }
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_indeterminate),
                            currentTheme = currentTheme
                        ) {
                            CircularWavyProgressIndicator()
                        }
                        Button(
                            onClick = {
                                progress = if (progress >= 1f) 0f else progress + DebugProgressStep
                            }
                        ) {
                            Text(stringResource(R.string.debug_progress_advance))
                        }
                    }

                SettingsDebugProgressVariant.PULL_TO_REFRESH_LOADING -> {
                    val state = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                delay(DebugProgressRefreshMillis)
                                isRefreshing = false
                            }
                        },
                        state = state,
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = state,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.debug_progress_pull_hint),
                                fontSize = 12.sp,
                                color = currentTheme.mutedForeground
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            repeat(DebugProgressRowCount) { index ->
                                Text(
                                    text =
                                        stringResource(R.string.debug_progress_row, index + 1),
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

                else ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(DebugProgressSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.debug_progress_hint),
                            fontSize = 12.sp,
                            color = currentTheme.mutedForeground
                        )
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_determinate),
                            currentTheme = currentTheme
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        DebugProgressLabelled(
                            label = stringResource(R.string.debug_progress_indeterminate),
                            currentTheme = currentTheme
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Button(
                            onClick = {
                                progress = if (progress >= 1f) 0f else progress + DebugProgressStep
                            }
                        ) {
                            Text(stringResource(R.string.debug_progress_advance))
                        }
                    }
            }
        }
    }
}

/** 一行：标签 + 下面那个指示器。 */
@Composable
private fun DebugProgressLabelled(
    label: String,
    currentTheme: CssVariables,
    indicator: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, color = currentTheme.mutedForeground)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            indicator()
        }
    }
}

/** 页面标题就取档位名（复用调试页里那几个标签）。 */
private fun debugProgressTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugProgressVariant.CIRCULAR -> R.string.debug_progress_entry_circular
        SettingsDebugProgressVariant.PULL_TO_REFRESH ->
            R.string.debug_progress_entry_pull_to_refresh
        SettingsDebugProgressVariant.WAVY_LINEAR -> R.string.debug_progress_entry_wavy_linear
        SettingsDebugProgressVariant.WAVY_CIRCULAR -> R.string.debug_progress_entry_wavy_circular
        SettingsDebugProgressVariant.PULL_TO_REFRESH_LOADING ->
            R.string.debug_progress_entry_pull_to_refresh_loading
        else -> R.string.debug_progress_entry_linear
    }
