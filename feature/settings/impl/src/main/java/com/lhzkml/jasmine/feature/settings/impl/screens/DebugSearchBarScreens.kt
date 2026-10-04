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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.button.IconButton
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.core.widgets.appbar.TopAppBar
import com.lhzkml.jasmine.core.widgets.searchbar.ExpandedDockedSearchBar
import com.lhzkml.jasmine.core.widgets.searchbar.ExpandedFullScreenSearchBar
import com.lhzkml.jasmine.core.widgets.searchbar.SearchBar
import com.lhzkml.jasmine.core.widgets.searchbar.SearchBarDefaults
import com.lhzkml.jasmine.core.widgets.searchbar.SearchBarValue
import com.lhzkml.jasmine.core.widgets.searchbar.rememberSearchBarState
import com.lhzkml.jasmine.feature.settings.api.SettingsDebugSearchBarVariant
import com.lhzkml.jasmine.feature.settings.impl.R

/** 演示列表的行数：够长才像真页面（展开态的结果区域里也能滚）。 */
private const val DebugSearchBarRowCount = 30

/** 展开态里"搜索结果"的占位条数。 */
private const val DebugSearchBarSuggestionCount = 6

/** 行间距；返回箭头图标尺寸。 */
private val DebugSearchBarRowSpacing = 14.dp
private val DebugSearchBarIconSize = 24.dp

/**
 * `core:widgets` 搜索框（**新版 state API**）的独立预览页。
 *
 * 页面自己的顶栏是我们的小号 [TopAppBar]，被测的搜索框紧贴其下；**刻意不套**设置流那套
 * `SettingsPage` 顶栏 —— 这一页只属于组件调试，不参与 app 现有的顶部导航。
 *
 * 3 档：折叠态一档（基础 [SearchBar]，配全屏展开 —— 点输入框就展开）、展开态两档
 * （[ExpandedFullScreenSearchBar] 走 Dialog + 预测返回 / [ExpandedDockedSearchBar] 走 Popup，
 * 进来就是展开状态）。三档底下都垫着一份可滚动的长列表。
 */
@Composable
fun DebugSearchBarScreen(
    variant: String,
    currentTheme: CssVariables,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startsExpanded =
        variant == SettingsDebugSearchBarVariant.EXPANDED_FULL_SCREEN ||
            variant == SettingsDebugSearchBarVariant.EXPANDED_DOCKED
    val state =
        rememberSearchBarState(
            initialValue = if (startsExpanded) SearchBarValue.Expanded else SearchBarValue.Collapsed
        )
    val textFieldState = rememberTextFieldState()

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = state,
            onSearch = {},
            placeholder = { Text(stringResource(R.string.debug_search_bar_placeholder)) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(debugSearchBarTitleRes(variant))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.debug_top_app_bar_back),
                            modifier = Modifier.size(DebugSearchBarIconSize)
                        )
                    }
                }
            )

            // 折叠态那一半：展开态的两档也要它 —— 停靠展开的 Popup 是相对它定位的。
            SearchBar(state = state, inputField = inputField)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_search_bar_hint),
                    fontSize = 12.sp,
                    color = currentTheme.mutedForeground
                )

                Spacer(modifier = Modifier.height(DebugSearchBarRowSpacing))

                repeat(DebugSearchBarRowCount) { index ->
                    Text(
                        text = stringResource(R.string.debug_search_bar_body, index + 1),
                        fontSize = 15.sp,
                        color = currentTheme.foreground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        // 展开态那一半：全屏的走 Dialog、停靠的走 Popup，两者都要求 state 处于展开态。
        if (variant == SettingsDebugSearchBarVariant.EXPANDED_DOCKED) {
            ExpandedDockedSearchBar(state = state, inputField = inputField) {
                DebugSearchBarSuggestions(currentTheme = currentTheme)
            }
        } else {
            ExpandedFullScreenSearchBar(state = state, inputField = inputField) {
                DebugSearchBarSuggestions(currentTheme = currentTheme)
            }
        }
    }
}

/** 展开态里的"搜索结果"占位内容。 */
@Composable
private fun DebugSearchBarSuggestions(currentTheme: CssVariables) {
    Text(
        text = stringResource(R.string.debug_search_bar_suggestions),
        fontSize = 12.sp,
        color = currentTheme.mutedForeground,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 4.dp)
    )
    repeat(DebugSearchBarSuggestionCount) { index ->
        Text(
            text = stringResource(R.string.debug_search_bar_content, index + 1),
            fontSize = 15.sp,
            color = currentTheme.foreground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

/** 页面标题就取变体名（复用调试页里那几个标签）。 */
private fun debugSearchBarTitleRes(variant: String): Int =
    when (variant) {
        SettingsDebugSearchBarVariant.EXPANDED_FULL_SCREEN ->
            R.string.debug_search_bar_entry_expanded_full_screen
        SettingsDebugSearchBarVariant.EXPANDED_DOCKED ->
            R.string.debug_search_bar_entry_expanded_docked
        else -> R.string.debug_search_bar_entry_search_bar
    }
