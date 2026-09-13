package com.lhzkml.jasmine.feature.greeting.impl.screens

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.greeting.impl.R
import com.lhzkml.jasmine.feature.greeting.impl.components.Button

/** The three selectable language modes. */
private enum class LanguageOption { FOLLOW_SYSTEM, ENGLISH, CHINESE }

/**
 * Language settings page: choose between following the system locale, English
 * or Simplified Chinese. Applies via [AppCompatDelegate.setApplicationLocales],
 * which persists the choice and recreates the activity with the new locale.
 *
 * Selection is derived from the persisted app locales (the single source of
 * truth) on every recomposition. [pendingChoice] is only a transient marker
 * for the tapped option: when the effective locale is unchanged the activity
 * is NOT recreated and [configuration] never invalidates, so nothing else
 * would move the checkmark. As soon as the configuration catches up (recreation
 * or external locale change), the marker is cleared and the derived value wins.
 */
@Composable
fun LanguageScreen(
    currentTheme: CssVariables,
    modifier: Modifier = Modifier
) {
    var pendingChoice by remember { mutableStateOf<LanguageOption?>(null) }
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) { pendingChoice = null }
    val selected = pendingChoice ?: deriveSelectedOption()

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
                .clip(RoundedCornerShape(currentTheme.radiusLg))
                .background(currentTheme.card)
                .border(1.dp, currentTheme.border, RoundedCornerShape(currentTheme.radiusLg))
        ) {
            LanguageRow(
                label = stringResource(R.string.language_follow_system),
                isSelected = selected == LanguageOption.FOLLOW_SYSTEM,
                currentTheme = currentTheme,
                testTag = "language_option_follow_system",
                onClick = {
                    pendingChoice = LanguageOption.FOLLOW_SYSTEM
                    applyLanguage(LanguageOption.FOLLOW_SYSTEM)
                }
            )
            LanguageDivider(currentTheme)
            LanguageRow(
                label = stringResource(R.string.language_english),
                isSelected = selected == LanguageOption.ENGLISH,
                currentTheme = currentTheme,
                testTag = "language_option_english",
                onClick = {
                    pendingChoice = LanguageOption.ENGLISH
                    applyLanguage(LanguageOption.ENGLISH)
                }
            )
            LanguageDivider(currentTheme)
            LanguageRow(
                label = stringResource(R.string.language_chinese),
                isSelected = selected == LanguageOption.CHINESE,
                currentTheme = currentTheme,
                testTag = "language_option_chinese",
                onClick = {
                    pendingChoice = LanguageOption.CHINESE
                    applyLanguage(LanguageOption.CHINESE)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun applyLanguage(option: LanguageOption) {
    val locales = when (option) {
        LanguageOption.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        LanguageOption.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        LanguageOption.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

/** Derives the currently applied option from the persisted app locales. */
private fun deriveSelectedOption(): LanguageOption {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    return if (currentLocales.isEmpty) {
        LanguageOption.FOLLOW_SYSTEM
    } else {
        when (currentLocales.get(0)?.language) {
            "zh" -> LanguageOption.CHINESE
            else -> LanguageOption.ENGLISH
        }
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
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        testTag = testTag
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
                        imageVector = Icons.Default.Check,
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
