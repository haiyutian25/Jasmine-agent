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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.ui.icons.LucideIcons
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
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
    /** 自动压缩的两个**全局默认**（0 = 不设置）；口径见 `UserPreferences` 里的同名字段。 */
    autoCompactTokenLimit: Int,
    effectiveContextWindowPercent: Int,
    /** 用户改完任一条线（失焦 / 回车时提交一次），两个值一起回。 */
    onCompactionChanged: (Int, Int) -> Unit,
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

        Text(
            text = stringResource(R.string.agent_compaction_section),
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
            CompactionNumberRow(
                label = stringResource(R.string.agent_compact_token_limit_label),
                hint = stringResource(R.string.agent_compact_token_limit_hint),
                value = autoCompactTokenLimit,
                currentTheme = currentTheme,
                testTag = "behaviour_compact_token_limit",
                onCommit = { onCompactionChanged(it, effectiveContextWindowPercent) }
            )
            OptionDivider(currentTheme)
            CompactionNumberRow(
                label = stringResource(R.string.agent_compact_percent_label),
                hint = stringResource(R.string.agent_compact_percent_hint),
                value = effectiveContextWindowPercent,
                currentTheme = currentTheme,
                testTag = "behaviour_compact_percent",
                onCommit = { onCompactionChanged(autoCompactTokenLimit, it) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.agent_compaction_hint),
            fontSize = 11.sp,
            color = currentTheme.mutedForeground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 一行「标签 + 数字输入」。
 *
 * 提交时机是**失焦或回车**（不是每一次按键）—— 半途中的 "1" 不该被当成用户的选择写进偏好，
 * 那会让压缩线在一瞬间掉到几百 token 上、把好好的对话压掉。
 *
 * 输入非法（非数字）时退回当前值；空串按 0（= 不设置）处理，这正是这个字段的表达法。
 */
@Composable
private fun CompactionNumberRow(
    label: String,
    hint: String,
    value: Int,
    currentTheme: CssVariables,
    testTag: String,
    onCommit: (Int) -> Unit,
) {
    // 输入中的文本：外部值变了（比如落盘失败回滚）要跟着回到权威值。
    var text by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
    fun commit() {
        val parsed = text.trim().toIntOrNull() ?: 0
        val normalized = parsed.coerceAtLeast(0)
        text = if (normalized > 0) normalized.toString() else ""
        if (normalized != value) onCommit(normalized)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = currentTheme.foreground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(9) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = currentTheme.foreground,
            ),
            cursorBrush = SolidColor(currentTheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) commit() }
                .testTag(testTag)
                .padding(vertical = 2.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        fontSize = 14.sp,
                        color = currentTheme.mutedForeground,
                    )
                }
                inner()
            }
        )
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
private fun OptionDivider(currentTheme: CssVariables) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(currentTheme.border.copy(alpha = 0.5f))
    )
}
