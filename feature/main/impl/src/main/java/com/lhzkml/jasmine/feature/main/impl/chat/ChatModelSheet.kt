package com.lhzkml.jasmine.feature.main.impl.chat

import com.lhzkml.jasmine.core.ui.theme.AppShapes
import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.ContextUsage
import com.lhzkml.jasmine.core.agent.ContextUsageSource
import com.lhzkml.jasmine.core.widgets.bottomsheet.BottomSheet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.RectangleShape
import com.lhzkml.jasmine.core.widgets.button.Button as WidgetsButton
import com.lhzkml.jasmine.core.widgets.button.ButtonDefaults
import com.lhzkml.jasmine.core.widgets.text.Text as WidgetsText
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.main.impl.R
import java.text.NumberFormat
import java.util.Locale


/** 模型选择行的行高（历史对话行已随侧边栏一起搬走）。 */
private val ChatModelRowPaddingVertical = 11.dp

private val ChatPickerListMaxHeight = 380.dp

/** 档位之间的间距。 */
private val ChatContextStepGap = 8.dp

/** 自定义输入框的内边距。 */
private val ChatContextStepPaddingHorizontal = 12.dp
private val ChatContextStepPaddingVertical = 8.dp

/** 可选档位，第一个是核心的默认值。 */
private val ChatContextWindowSteps = listOf(
    200_000L to "200K",
    400_000L to "400K",
    600_000L to "600K",
    800_000L to "800K",
    1_000_000L to "1M",
)

/** 环的外径：照 ZCode 那个入口的 14px。 */
private val ChatContextRingSize = 14.dp

/** 面板里那条分段横条的高度，照 ZCode 的 h-2。 */
private val ChatContextBarHeight = 8.dp

/** 分段之间留一条细缝，免得分段连成一片看不出分界。 */
private val ChatContextBarSegmentGap = 2.dp

/** 分类行前面的色块，照 ZCode 的 size-2。 */
private val ChatContextDotSize = 8.dp

/** 分类行之间的间距。 */
private val ChatContextRowGap = 8.dp

/**
 * 色阶的 5 档混色比例，照 ZCode：第 1 档是主色本身，最后一档只剩 28% 主色。
 *
 * 颜色只跟**排序后的位次**有关（占比越大越深），所以某一块的占比一变、它的颜色也跟着变 ——
 * 这一点与 ZCode 相同。
 */
private val ChatContextToneMix = listOf(1f, 0.78f, 0.58f, 0.42f, 0.28f)

// ── Model picker ───────────────────────────────────────────────────────

/**
 * 模型选择：列出所有 provider 下已配置的模型，按 provider 分组（模型名 + 所属 provider）。
 *
 * 选中后由 `ChatViewModel.handleModelSelected` 落库并**重建 ADK session** ——
 * 一个 session 绑定一个模型，所以切换模型会重新挂载（转写记录本身保留）。
 */
@Composable
internal fun ModelSheet(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ChatAction.ModelPickerDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_model_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_pick_model_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.height(12.dp))

            val options = state.providers.flatMap { provider ->
                provider.models.map { model -> provider to model }
            }
            if (options.isEmpty()) {
                // 还没在「模型提供商」里配过任何模型 —— 给出可执行的下一步，而不是空列表。
                Text(
                    text = stringResource(R.string.chat_pick_model_empty),
                    fontSize = ChatBodyFontSize,
                    color = currentTheme.mutedForeground,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ChatPickerListMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { (provider, model) ->
                        ModelRow(
                            providerId = provider.id,
                            providerName = provider.name,
                            modelId = model.modelId,
                            isCurrent = provider.id == state.activeProviderId &&
                                model.id == state.activeModelId,
                            currentTheme = currentTheme,
                            onSelect = {
                                onAction(ChatAction.ModelSelected(provider.id, model.id))
                            },
                        )
                    }
                }
            }

            ContextWindowSection(
                window = state.contextWindow,
                currentTheme = currentTheme,
                onSelect = { onAction(ChatAction.ContextWindowSelected(it)) },
            )
        }
    }
}

// ── Context window picker ──────────────────────────────────────────────

/**
 * 当前会话的上下文窗口：几个固定档位点一下即选，「不设上限」选它就没有占比可算，
 * 底下还能自己填一个数。
 *
 * 它决定环与面板的分母，所以放在模型面板里 —— 换模型和改窗口是同一件事的两面。
 */
@Composable
private fun ContextWindowSection(
    window: Long,
    currentTheme: CssVariables,
    onSelect: (Long) -> Unit,
) {
    var custom by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatDividerHeight)
                .background(currentTheme.border)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_context_window_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = currentTheme.foreground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = contextWindowLabel(window),
                fontSize = ChatMetaFontSize,
                fontFamily = FontFamily.Monospace,
                color = currentTheme.mutedForeground
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 五个档位一行排开、按钮宽度由标签自己撑开；默认外观的按钮比较宽，整行放不下时
        // 直接横向滚动，不折行也不裁字。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatContextStepGap),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ChatContextWindowSteps.forEach { (tokens, label) ->
                ContextWindowStep(
                    label = label,
                    onClick = { onSelect(tokens) },
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 自定义：填一个以 K 为单位的数（比如 300 = 300K），确定即生效。
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = custom,
                onValueChange = { typed -> custom = typed.filter(Char::isDigit).take(7) },
                singleLine = true,
                textStyle = TextStyle(
                    color = currentTheme.foreground,
                    fontSize = ChatBodyFontSize
                ),
                cursorBrush = SolidColor(currentTheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_context_window_custom"),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.small)
                            .background(currentTheme.subtleSurface)
                            .padding(
                                horizontal = ChatContextStepPaddingHorizontal,
                                vertical = ChatContextStepPaddingVertical,
                            )
                    ) {
                        if (custom.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_context_window_custom_hint),
                                fontSize = ChatBodyFontSize,
                                color = currentTheme.mutedForeground
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(modifier = Modifier.width(ChatContextStepGap))
            val customTokens = custom.toLongOrNull()?.times(1000)
            ContextWindowStep(
                label = stringResource(R.string.chat_context_window_custom_apply),
                enabled = customTokens != null,
                onClick = { customTokens?.let(onSelect) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.chat_context_window_hint),
            fontSize = ChatMetaFontSize,
            color = currentTheme.mutedForeground
        )
    }
}

/**
 * 一个档位：直接用自有 [WidgetsButton] 的默认外观（primary 填充），不做任何外观定制。
 *
 * 内容是自有的 [WidgetsText]，这样文字才吃得到按钮下发的内容色与字型。宽度由标签自己撑开；
 * 一行放不下时整行横向滚动（见调用处）。
 */
@Composable
private fun ContextWindowStep(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    WidgetsButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag("chat_context_window_$label"),
    ) {
        WidgetsText(text = label)
    }
}

/** 当前窗口的显示名：固定档位用它的简写，别的数按 K/M 显示。 */
private fun contextWindowLabel(tokens: Long): String {
    val step = ChatContextWindowSteps.firstOrNull { it.first == tokens }
    return step?.second ?: tokenCountLabel(tokens)
}

/** 「200K」「1M」「128K」这种按千 / 百万取整的写法；不是整千就退回系统的紧凑记法。 */
private fun tokenCountLabel(tokens: Long): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0L -> "${tokens / 1_000_000}M"
    tokens >= 1_000 && tokens % 1_000 == 0L -> "${tokens / 1_000}K"
    else -> compactTokenText(tokens, 1)
}

@Composable
private fun ModelRow(
    providerId: String,
    providerName: String,
    modelId: String,
    isCurrent: Boolean,
    currentTheme: CssVariables,
    onSelect: () -> Unit,
) {
    // 保留原有的“无外观”定制（透明底 / 无边框 / 无阴影 / 零内边距），
    // 只把行为容器换成 core:widgets 的自有 Button。
    WidgetsButton(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_model_${providerId}_$modelId"),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        elevation = null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ChatModelRowPaddingVertical)
        ) {
            Text(
                text = modelId,
                fontSize = ChatBodyFontSize,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) currentTheme.primary else currentTheme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = providerName,
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Context usage ──────────────────────────────────────────────────────

private fun contextTone(index: Int, currentTheme: CssVariables): Color {
    val mix = ChatContextToneMix[index.coerceIn(0, ChatContextToneMix.lastIndex)]
    return lerp(currentTheme.primary, currentTheme.card, 1f - mix)
}

/**
 * 输入框里的环形入口。
 *
 * 环身画「最近一次请求放进窗口的 token / 平台配的窗口」，与 ZCode 那个入口同一个意思；没配窗口
 * 或还没有用量时就只有一条淡轨道。点它打开 [ContextUsageSheet]。
 */
@Composable
internal fun ContextUsageRing(
    usage: ContextUsage?,
    currentTheme: CssVariables,
    onClick: () -> Unit,
) {
    val window = usage?.modelContextWindow ?: 0L
    val used = usage?.usedTokens ?: 0L
    val progress = if (window > 0) (used.toFloat() / window).coerceIn(0f, 1f) else 0f
    val trackColor = currentTheme.mutedForeground.copy(alpha = 0.25f)
    val progressColor = currentTheme.mutedForeground.copy(alpha = 0.7f)

    WidgetsButton(
        onClick = onClick,
        modifier = Modifier
            .size(ChatSendButtonSize)
            .testTag("chat_context_usage_btn"),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        elevation = null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Canvas(modifier = Modifier.size(ChatContextRingSize)) {
            // 线宽取 ZCode 那个环的比例（24 的 viewBox 里 stroke 是 4），跟着尺寸缩放。
            val stroke = size.minDimension * 4f / 24f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(
                color = trackColor,
                radius = arcSize.minDimension / 2f,
                center = center,
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * 面板本体：标题 + 右侧总量、按来源的分段横条、分类行、收尾一条分隔线。
 *
 * 布局与 ZCode 的工具栏面板一致，只是按你的要求去掉了「平均缓存命中率」那一行。分类里
 * **五个来源都列**，为 0 的也列 —— 技能与 MCP 工具还要补，先把位置留出来。
 */
@Composable
internal fun ContextUsageSheet(
    state: ChatState,
    currentTheme: CssVariables,
    onAction: (ChatAction) -> Unit,
) {
    BottomSheet(
        onDismiss = { onAction(ChatAction.ContextPanelDismissed) },
        currentTheme = currentTheme,
        modifier = Modifier.testTag("chat_context_usage_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            val usage = state.contextUsage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_context_usage_title),
                    fontSize = ChatBodyFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTheme.foreground
                )
                Spacer(modifier = Modifier.weight(1f))
                if (usage != null) {
                    Text(
                        text = contextUsageSummary(usage),
                        fontSize = ChatMetaFontSize,
                        fontFamily = FontFamily.Monospace,
                        color = currentTheme.mutedForeground
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 五个来源都列（技能与 MCP 工具的功能还没做，现在是 0，先把位置留着）；排序只影响
            // 先后与配色，0 的那些自然落到最后。
            val rows = usage?.breakdown.orEmpty().sortedByDescending { it.tokens }
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_context_usage_empty),
                    fontSize = ChatMetaFontSize,
                    color = currentTheme.mutedForeground
                )
                return@Column
            }

            val total = rows.sumOf { it.tokens }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatContextBarHeight)
                    .clip(CircleShape)
            ) {
                // 条上只铺有内容的那几段；色阶仍按**它在分类行里的位次**取，好让条与行对得上。
                val barRows = rows.withIndex().filter { (_, bucket) -> bucket.tokens > 0 }
                barRows.forEachIndexed { position, (index, bucket) ->
                    Box(
                        modifier = Modifier
                            .weight(bucket.tokens.toFloat() / total.toFloat())
                            .height(ChatContextBarHeight)
                            .background(contextTone(index, currentTheme))
                    )
                    if (position != barRows.lastIndex) {
                        Spacer(modifier = Modifier.width(ChatContextBarSegmentGap))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            rows.forEachIndexed { index, bucket ->
                if (index > 0) Spacer(modifier = Modifier.height(ChatContextRowGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(ChatContextDotSize)
                            .clip(RoundedCornerShape(2.dp))
                            .background(contextTone(index, currentTheme))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(bucket.source.labelRes()),
                        fontSize = ChatMetaFontSize,
                        color = currentTheme.mutedForeground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        // 分类占比按这一轮请求内部各来源的份额算，与 ZCode 同一口径。
                        text = contextPercentText(bucket.tokens.toDouble() / total.toDouble()),
                        fontSize = ChatMetaFontSize,
                        fontFamily = FontFamily.Monospace,
                        color = currentTheme.foreground
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatDividerHeight)
                    .background(currentTheme.border)
            )
            // 当前模型自己的参数：这是「模型提供商」里预设的那两个数，作为参考放在这里 ——
            // 会话的窗口就是按它（或它的默认值）在第一次附着时定下的。
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.chat_context_model_section),
                fontSize = ChatMetaFontSize,
                color = currentTheme.mutedForeground
            )
            Spacer(modifier = Modifier.height(6.dp))
            ContextModelParamRow(
                label = stringResource(R.string.chat_context_model_window),
                value = state.activeModel?.contextLength,
                currentTheme = currentTheme,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ContextModelParamRow(
                label = stringResource(R.string.chat_context_model_output),
                value = state.activeModel?.maxOutputLength,
                currentTheme = currentTheme,
            )
        }
    }
}

/** 「模型设定」里的一行：名目 + 右侧 mono 数值；没配过就写「未设置」。 */
@Composable
private fun ContextModelParamRow(
    label: String,
    value: Int?,
    currentTheme: CssVariables,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = ChatMetaFontSize,
            color = currentTheme.mutedForeground
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (value != null && value > 0) {
                tokenCountLabel(value.toLong())
            } else {
                stringResource(R.string.chat_context_model_unset)
            },
            fontSize = ChatMetaFontSize,
            fontFamily = FontFamily.Monospace,
            color = currentTheme.foreground
        )
    }
}

/** 分类名，与核心的 [ContextUsageSource] 一一对应。 */
private fun ContextUsageSource.labelRes(): Int = when (this) {
    ContextUsageSource.SYSTEM_PROMPT -> R.string.chat_context_system_prompt
    ContextUsageSource.SYSTEM_TOOL_SCHEMAS -> R.string.chat_context_system_tools
    ContextUsageSource.SKILLS -> R.string.chat_context_skills
    ContextUsageSource.MCP_TOOL_SCHEMAS -> R.string.chat_context_mcp_tools
    ContextUsageSource.MESSAGES -> R.string.chat_context_messages
}

/** 「2.2万/100万 (2.2%)」；没配窗口时只给已用的那个数。 */
private fun contextUsageSummary(usage: ContextUsage): String {
    val used = compactTokenText(usage.usedTokens, 1)
    val window = usage.modelContextWindow ?: return used
    val ratio = usage.usedTokens.toDouble() / window.toDouble()
    return "$used/${compactTokenText(window, 0)} (${contextPercentText(ratio)})"
}

/** 「2.2万」「100万」：ICU 的紧凑十进制记法，中文给的正是万 / 亿。 */
private fun compactTokenText(tokens: Long, fractionDigits: Int): String =
    CompactDecimalFormat
        .getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
        .apply { maximumFractionDigits = fractionDigits }
        .format(tokens)

/** 百分比，1 位小数。 */
private fun contextPercentText(ratio: Double): String =
    NumberFormat.getPercentInstance(Locale.getDefault())
        .apply { maximumFractionDigits = 1 }
        .format(ratio.coerceIn(0.0, 1.0))
