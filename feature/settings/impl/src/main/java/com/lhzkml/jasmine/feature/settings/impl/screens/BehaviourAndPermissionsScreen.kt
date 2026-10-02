package com.lhzkml.jasmine.feature.settings.impl.screens

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import androidx.annotation.StringRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.data.model.AgentOutputLanguage
import com.lhzkml.jasmine.core.ui.components.Button
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.R

/**
 * **行为与权限**（菜单标题见 `settings_menu_agent_title`）设置页：模型侧的行为控制。眼下只有一项
 * 「模型回复语言」，后面会往这里加工具权限、审核这些。
 *
 * 语言是**全局**一个值（不做会话级），三态：跟随输入 / 跟随应用语言 / 固定某种语言 —— 见
 * [AgentOutputLanguage]。它最终进的是**系统指令**，所以改完从**下一次附着会话**起生效（底下写了这句）。
 */
@Composable
fun BehaviourAndPermissionsScreen(
    currentTheme: CssVariables,
    selected: String,
    onSelect: (String) -> Unit,
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
        Text(
            text = stringResource(R.string.agent_language_section),
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
            AgentOutputLanguage.CHOICES.forEachIndexed { index, value ->
                if (index > 0) OptionDivider(currentTheme)
                LanguageOptionRow(
                    label = stringResource(languageLabel(value)),
                    isSelected = value == selected,
                    currentTheme = currentTheme,
                    testTag = "behaviour_language_option_$value",
                    onClick = { onSelect(value) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.agent_language_hint),
            fontSize = 11.sp,
            color = currentTheme.mutedForeground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 每个取值对应的文案；认不出的按"跟随输入"显示。 */
@StringRes
private fun languageLabel(value: String): Int = when (value) {
    AgentOutputLanguage.FOLLOW_APP -> R.string.agent_language_follow_app
    AgentOutputLanguage.ENGLISH -> R.string.agent_language_english
    AgentOutputLanguage.SIMPLIFIED_CHINESE -> R.string.agent_language_simplified_chinese
    AgentOutputLanguage.TRADITIONAL_CHINESE -> R.string.agent_language_traditional_chinese
    else -> R.string.agent_language_follow_input
}

/** One selectable row: label left, leading-brand checkmark right when selected. */
@Composable
private fun LanguageOptionRow(
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
private fun OptionDivider(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(currentTheme.border.copy(alpha = 0.5f))
    )
}
