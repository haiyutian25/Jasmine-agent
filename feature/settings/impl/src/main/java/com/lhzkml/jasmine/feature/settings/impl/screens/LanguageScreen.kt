package com.lhzkml.jasmine.feature.settings.impl.screens

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.repository.AppLanguage
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.LanguageAction
import com.lhzkml.jasmine.feature.settings.impl.LanguageState
import com.lhzkml.jasmine.feature.settings.impl.R

/**
 * Language settings page: choose between following the system locale, English or Simplified Chinese.
 *
 * Pure function of [state] + [onAction]: selection and the current locale both live in
 * [com.lhzkml.jasmine.feature.settings.impl.LanguageViewModel], and applying a choice is an action
 * (the platform call happens behind `AppLanguageRepository`). The only composition value read here is
 * the ambient configuration, used to tell the ViewModel "the app was recreated, settle to the
 * authoritative value" — no platform API is called from the View.
 */
@Composable
fun LanguageScreen(
    state: LanguageState,
    onAction: (LanguageAction) -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { onAction(LanguageAction.SystemLocaleSettled) }

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
        Text(
            text = stringResource(R.string.language_section),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = currentTheme.mutedForeground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, AppShapes.large)
        ) {
            LanguageRow(
                label = stringResource(R.string.language_follow_system),
                isSelected = state.selected == AppLanguage.SYSTEM,
                currentTheme = currentTheme,
                testTag = "language_option_follow_system",
                onClick = { onAction(LanguageAction.Selected(AppLanguage.SYSTEM)) }
            )
            LanguageDivider(currentTheme)
            LanguageRow(
                label = stringResource(R.string.language_english),
                isSelected = state.selected == AppLanguage.ENGLISH,
                currentTheme = currentTheme,
                testTag = "language_option_english",
                onClick = { onAction(LanguageAction.Selected(AppLanguage.ENGLISH)) }
            )
            LanguageDivider(currentTheme)
            LanguageRow(
                label = stringResource(R.string.language_chinese),
                isSelected = state.selected == AppLanguage.CHINESE,
                currentTheme = currentTheme,
                testTag = "language_option_chinese",
                onClick = { onAction(LanguageAction.Selected(AppLanguage.CHINESE)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    currentTheme: CssVariables,
    testTag: String,
    onClick: () -> Unit
) {
    // 保留原有的“无外观”定制（透明底 / 无边框 / 无阴影 / 零内边距，外观由 Row 自绘），
    // 只把行为容器换成 core:widgets 的自有 Button。
    WidgetsButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        elevation = null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = currentTheme.foreground
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(currentTheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = null,
                        tint = currentTheme.background,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageDivider(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(currentTheme.border.copy(alpha = 0.5f))
    )
}
