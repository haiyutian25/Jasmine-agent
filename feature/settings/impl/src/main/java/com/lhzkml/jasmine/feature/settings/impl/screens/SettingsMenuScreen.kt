package com.lhzkml.jasmine.feature.settings.impl.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.R

// ── Grouped-list dimensions ────────────────────────────────────────────

/** Row leading icon size and its spacing to the text column. */
private val MenuRowIconSize = 20.dp
private val MenuRowIconTextSpacing = 14.dp

/** Row inner padding; divider hairline width; spacing between group cards. */
private val MenuRowPaddingHorizontal = 16.dp
private val MenuRowPaddingVertical = 14.dp
private val MenuRowDividerHeight = 1.dp
private val MenuGroupSpacing = 16.dp

/** Title typography. */
private val MenuRowTitleFontSize = 14.sp

/**
 * Settings menu rendered as a single grouped card (iOS-style list section):
 * one rounded container holding the three entries separated by hairline
 * dividers. Each row is a leading monochrome icon + title only, with
 * no trailing chevron — the whole row is the tap target.
 *
 * Entries: "Appearance & Themes" -> appearance page, "Font" -> font page,
 * "Language" -> language page (the app's own UI language), "Behaviour & permissions" ->
 * the model-side controls (reply language, tool permissions, approvals…),
 * "Model Providers" -> provider feature.
 */
@Composable
fun SettingsMenuScreen(
    currentTheme: CssVariables,
    onOpenAppearance: () -> Unit,
    onOpenFont: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenBehaviourAndPermissions: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenUsageStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingsGroupCard(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth()) {
            SettingsMenuRow(
                icon = LucideIcons.Palette,
                title = stringResource(R.string.settings_menu_appearance_title),
                onClick = onOpenAppearance,
                currentTheme = currentTheme,
                testTag = "settings_menu_appearance_entry"
            )
            SettingsRowDivider(currentTheme = currentTheme)
            SettingsMenuRow(
                icon = LucideIcons.FormatSize,
                title = stringResource(R.string.settings_menu_font_title),
                onClick = onOpenFont,
                currentTheme = currentTheme,
                testTag = "settings_menu_font_entry"
            )
            SettingsRowDivider(currentTheme = currentTheme)
            SettingsMenuRow(
                icon = LucideIcons.Languages,
                title = stringResource(R.string.language_title),
                onClick = onOpenLanguage,
                currentTheme = currentTheme,
                testTag = "settings_menu_language_entry"
            )
        }

        // 行为与权限：模型侧的行为控制（眼下是回复语言，后面还会往里加），所以单独一组。
        Spacer(modifier = Modifier.height(MenuGroupSpacing))

        SettingsGroupCard(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth()) {
            SettingsMenuRow(
                icon = LucideIcons.Sparkles,
                title = stringResource(R.string.settings_menu_agent_title),
                onClick = onOpenBehaviourAndPermissions,
                currentTheme = currentTheme,
                testTag = "settings_menu_behaviour_entry"
            )
        }

        // Model providers are a separate feature — their own group card.
        Spacer(modifier = Modifier.height(MenuGroupSpacing))

        SettingsGroupCard(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth()) {
            SettingsMenuRow(
                icon = LucideIcons.Bot,
                title = stringResource(R.string.settings_menu_provider_title),
                onClick = onOpenProviders,
                currentTheme = currentTheme,
                testTag = "settings_menu_provider_entry"
            )
        }

        // 用量统计也是自己一组：它读的是核心那些会话文件，跟外观、语言没关系。
        Spacer(modifier = Modifier.height(MenuGroupSpacing))

        SettingsGroupCard(currentTheme = currentTheme, modifier = Modifier.fillMaxWidth()) {
            SettingsMenuRow(
                icon = LucideIcons.ChartColumn,
                title = stringResource(R.string.usage_stats_title),
                onClick = onOpenUsageStats,
                currentTheme = currentTheme,
                testTag = "settings_menu_usage_entry"
            )
        }
    }
}

/**
 * Rounded card container for a group of list rows: `card` surface with a
 * hairline `border`, corners clipped so row ripples never bleed outside.
 */
@Composable
private fun SettingsGroupCard(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(currentTheme.radiusMd)
    Column(
        modifier = modifier
            .clip(shape)
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, shape),
        content = content
    )
}

/**
 * One tappable list row: leading icon + title.
 * Uses the behavior-only [Button] so the grouped card owns all chrome.
 */
@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    currentTheme: CssVariables,
    testTag: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        testTag = testTag
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MenuRowPaddingHorizontal, vertical = MenuRowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = currentTheme.foreground,
                modifier = Modifier.size(MenuRowIconSize)
            )

            Spacer(modifier = Modifier.width(MenuRowIconTextSpacing))

            Text(
                text = title,
                fontSize = MenuRowTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground
            )
        }
    }
}

/** Hairline separator between rows inside a group card. */
@Composable
private fun SettingsRowDivider(currentTheme: CssVariables) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuRowDividerHeight)
            .background(currentTheme.border)
    )
}
