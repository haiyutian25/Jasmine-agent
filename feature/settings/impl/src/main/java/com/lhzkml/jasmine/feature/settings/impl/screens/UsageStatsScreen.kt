package com.lhzkml.jasmine.feature.settings.impl.screens

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lhzkml.jasmine.core.widgets.icon.Icon
import com.lhzkml.jasmine.core.widgets.text.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.AppUsage
import com.lhzkml.jasmine.core.agent.ModelUsage
import com.lhzkml.jasmine.core.widgets.pulltorefresh.PullToRefreshBox
import com.lhzkml.jasmine.core.widgets.pulltorefresh.PullToRefreshDefaults
import com.lhzkml.jasmine.core.widgets.pulltorefresh.rememberPullToRefreshState
import com.lhzkml.jasmine.core.ui.theme.CssVariables
import com.lhzkml.jasmine.feature.settings.impl.R
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── 尺寸 ──────────────────────────────────────────────────────────────

/** 活动格子图：一行一周，一列一天（周一到周日）。 */
private const val UsageHeatmapDaysPerWeek = 7

/** 表头（一…日）的字号。 */
private val UsageHeatmapLabelFontSize = 10.sp

/**
 * 数据区（表格）的固定高度。
 *
 * 高度写死是刻意的：卡片的大小由它决定，不是反过来被格子撑开 —— 每月 4~6 行都在同一块画布上平分高度，
 * 格子因此随卡片自适应成横向的长方形，卡片也不会这个月高、下个月矮。
 */
private val UsageHeatmapGridHeight = 150.dp

/**
 * 热力卡能翻多少个月：装机的当月摆在正中间（`UsageHeatmapMiddlePage`）、两头各留一半 —— 100 年足够
 * 翻不到边，而页数是个定值，`HorizontalPager` 才不需要无限页码。
 */
private const val UsageHeatmapPageCount = 1200
private const val UsageHeatmapMiddlePage = UsageHeatmapPageCount / 2

/**
 * 色阶的固定分档（当天 token）：达到一档就点亮一档，`≥1M` 才是最深的那档。
 *
 * | 当天 | 档位 |
 * |---|---|
 * | > 0 | 1（最浅） |
 * | ≥ 50K | 2 |
 * | ≥ 200K | 3 |
 * | ≥ 1M | 4（最深） |
 *
 * 想调就改这个列表（它给的是"第 2 / 3 / 4 档的门槛"）。
 */
private val UsageHeatmapLevelTokens = listOf(50_000L, 200_000L, 1_000_000L)

private val UsageCardCorner = 12.dp
private val UsageCardPadding = 16.dp
private val UsageCardSpacing = 16.dp

/** 表头（一…日）与格子图之间的空隙。 */
private val UsageHeatmapHeaderGap = 6.dp

/**
 * 格子之间横竖都留这么点缝、每个角都收这么点圆。
 *
 * 数值取自 GitHub 个人主页的贡献热力图（实测：格子 10px、`border-spacing: 3px`、`border-radius: 2px`）：
 * 缝就是 3px 那个绝对值；圆角按它的比例放大（2px / 10px = 20%，我们这格子高 ~30dp 所以取 4dp），
 * 不然在这么大的格子上会显得是直角。
 */
private val UsageHeatmapGap = 3.dp
private val UsageHeatmapCorner = 4.dp
private val UsageHeatmapLegendCell = 8.dp

/** 图例的小色块只有 8dp，圆角按 GitHub 原值来（2px），别用格子那个放大过的。 */
private val UsageHeatmapLegendCorner = 2.dp
private val UsageRowSpacing = 8.dp

/**
 * 每格一圈细边，**0 档（没花 token）也画** —— GitHub 就是 `.5px rgba(31,35,40,.05)` 的边配一层淡底，
 * 靠它把"没点亮的日子"也显出来（我们直接用主题的 hairline 边 `border`）。
 */
private val UsageHeatmapCellBorderWidth = 1.dp

/** 「模型用量」那张表：模型名自适应、Token 与占比两列定宽，表头与数据行共用这几个宽度才对得齐。 */
private val UsageModelsTokensColumnWidth = 68.dp
private val UsageModelsShareColumnWidth = 48.dp
private val UsageModelsColumnGap = 8.dp
private val UsageModelsHeaderDivider = 1.dp

/** 标题、数值、说明三种字号。 */
private val UsageTitleFontSize = 14.sp
private val UsageValueFontSize = 22.sp
private val UsageBodyFontSize = 13.sp
private val UsageMetaFontSize = 11.sp

/** 色阶第 1..4 档各混多少主色（第 0 档是空轨道色）。 */
private val UsageHeatmapMixes = listOf(0.2f, 0.45f, 0.7f, 1f)

/**
 * 「使用统计 → 应用用量」：累计花了多少、连着花了几天、哪天花过（可以按月翻的格子图），以及这些钱
 * 花在哪些模型上。
 *
 * 数据全来自核心（它从会话文件里读回来，**全部时间、不再按月截断**），页面自己不算任何数、也不引图
 * 表库 —— 格子图与表格都是自绘。
 */
@Composable
fun UsageStatsScreen(
    usage: AppUsage,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    // 刷新入口改成下拉刷新（形状变换指示器 PullToRefreshDefaults.LoadingIndicator）：
    // 原来的「刷新」按钮已删掉，页面内容整体交给 PullToRefreshBox，下拉过阈值即触发 onRefresh。
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(UsageCardSpacing),
        ) {
            UsageSummaryCard(usage = usage, currentTheme = currentTheme)
            UsageHeatmapCard(usage = usage, currentTheme = currentTheme)

            UsageModelsCard(usage = usage, isLoading = isLoading, currentTheme = currentTheme)
        }
    }
}

/** 两张统计卡：累计 Token 数与连续天数。 */
@Composable
private fun UsageSummaryCard(usage: AppUsage, currentTheme: CssVariables) {
    UsageCard(currentTheme = currentTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(UsageRowSpacing)) {
            UsageLabeledValue(
                label = stringResource(R.string.usage_stats_total),
                value = compactTokens(usage.totalTokens),
                currentTheme = currentTheme,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                UsageLabeledValue(
                    label = stringResource(R.string.usage_stats_streak_current),
                    value = stringResource(R.string.usage_stats_days, usage.currentStreakDays),
                    currentTheme = currentTheme,
                )
                UsageLabeledValue(
                    label = stringResource(R.string.usage_stats_streak_longest),
                    value = stringResource(R.string.usage_stats_days, usage.longestStreakDays),
                    currentTheme = currentTheme,
                )
            }
        }
    }
}

/**
 * 「Token 活动」：**一屏一个月**，可左右滑动换月；颜色越深花得越多。
 *
 * 每一屏的格子数就是那个月的天数（28/29/30/31），7 列一周（周一到周日），1 号落在它自己那一列上（前
 * 面空几格由 1 号是周几决定），到月末最后一天为止、后面不再有格子。格子样式照 GitHub 主页的贡献热力
 * 图（圆角、每格一圈细边、格间留缝，见 `UsageHeatmapCell`）；格子区高度写死（见 `UsageHeatmapGridHeight`），
 * 格子在里面按权重自适应成横向的长方形 —— 卡片的大小既不跟着格子跑，也不跟着翻月跳。
 *
 * 往右滑是更早的月份：只要本机记录能追到那么早，中间那些没花过 token 的月份照样翻得过去（只是格子全
 * 空）；比**最早有记录的那个月**还早的月份没有东西可看，格子区换成一句提示。往左滑是往后的月份，未来
 * 的月份跟"没有活动"一样是空网格。
 */
@Composable
private fun UsageHeatmapCard(usage: AppUsage, currentTheme: CssVariables) {
    val today = remember { LocalDate.now() }
    val currentMonth = remember(today) { YearMonth.from(today) }
    // 表头：周一到周日，用当前语言的最短写法（中文就是「一…日」）。
    val weekdayLabels = remember {
        val locale = Locale.getDefault()
        (1..UsageHeatmapDaysPerWeek).map { day ->
            DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale)
        }
    }
    // 全部时间的逐日数据，翻到哪个月都查得到（核心不再按月截断）。
    val tokensByDate = remember(usage) { usage.days.associate { it.date to it.tokens } }
    // 有记录的最早那天、以及它所在的月份：比这个月更早的月份没有东西可看。
    val earliestDate = remember(usage) {
        usage.days.minOfOrNull { it.date }?.let { text ->
            runCatching { LocalDate.parse(text) }.getOrNull()
        }
    }
    val earliestMonth = remember(earliestDate) { earliestDate?.let(YearMonth::from) }
    val dayFullFormat = remember { DateTimeFormatter.ofPattern("yyyy/M/d") }

    // 翻月：一屏一个月，装机的当月摆在正中间，两头留得足够宽（正常翻不到边）。
    val pagerState = rememberPagerState(initialPage = UsageHeatmapMiddlePage) {
        UsageHeatmapPageCount
    }
    val viewedMonth = currentMonth.plusMonths(
        (pagerState.currentPage - UsageHeatmapMiddlePage).toLong()
    )

    UsageCard(currentTheme = currentTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(UsageRowSpacing)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.usage_stats_heatmap_title),
                    fontSize = UsageTitleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = currentTheme.foreground,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.usage_stats_heatmap_month,
                        viewedMonth.year,
                        viewedMonth.monthValue,
                    ),
                    fontSize = UsageMetaFontSize,
                    color = currentTheme.mutedForeground,
                )
            }

            // 表头与格子图同权重分列，列才对得齐。
            Column(verticalArrangement = Arrangement.spacedBy(UsageHeatmapHeaderGap)) {
                // 表头：周一…周日
                Row(horizontalArrangement = Arrangement.spacedBy(UsageHeatmapGap)) {
                    repeat(UsageHeatmapDaysPerWeek) { weekday ->
                        UsageHeatmapWeekdayLabel(
                            label = weekdayLabels[weekday],
                            currentTheme = currentTheme,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 格子区：高度固定，一屏一个月，左右滑动换月（往右滑 = 更早）。没数据可看的月份换成提示。
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(UsageHeatmapGridHeight),
                ) { page ->
                    val pageMonth =
                        currentMonth.plusMonths((page - UsageHeatmapMiddlePage).toLong())
                    // 比最早那份记录还早：这一屏没有东西可看。没有记录时，就以当月为界。
                    val beforeRecords = earliestMonth?.let { pageMonth < it }
                        ?: (pageMonth < currentMonth)
                    if (beforeRecords) {
                        UsageHeatmapNoHistory(
                            earliest = earliestDate,
                            dayFormat = dayFullFormat,
                            currentTheme = currentTheme,
                        )
                    } else {
                        UsageHeatmapMonth(
                            month = pageMonth,
                            tokensByDate = tokensByDate,
                            currentTheme = currentTheme,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.usage_stats_heatmap_less),
                    fontSize = UsageMetaFontSize,
                    color = currentTheme.mutedForeground,
                )
                repeat(4) { index ->
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(UsageHeatmapLegendCell)
                            .height(UsageHeatmapLegendCell)
                            .clip(RoundedCornerShape(UsageHeatmapLegendCorner))
                            .background(heatmapColor(index + 1, currentTheme))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.usage_stats_heatmap_more),
                    fontSize = UsageMetaFontSize,
                    color = currentTheme.mutedForeground,
                )
            }
        }
    }
}

/**
 * 一个月的格子：一行一周、一列一天，第 n 格就是这个月的第 n 天，月初/月末凑不满的位置留空。
 *
 * 每个月的行数不同（4~6 行），但整块高度是外面给的定值，所以行高会跟着月份变、卡片本身不变。
 */
@Composable
private fun UsageHeatmapMonth(
    month: YearMonth,
    tokensByDate: Map<String, Long>,
    currentTheme: CssVariables,
) {
    val firstOfMonth = remember(month) { month.atDay(1) }
    val daysInMonth = remember(month) { month.lengthOfMonth() }
    // 1 号前面要空几格：它得落在自己那一列上（周一时为 0）。
    val leadingBlanks = remember(firstOfMonth) { firstOfMonth.dayOfWeek.value - 1 }
    // 行数 = 装下整月天数所需的周数。
    val rows = remember(leadingBlanks, daysInMonth) {
        (leadingBlanks + daysInMonth + UsageHeatmapDaysPerWeek - 1) / UsageHeatmapDaysPerWeek
    }

    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(UsageHeatmapGap),
    ) {
        repeat(rows) { week ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(UsageHeatmapGap),
            ) {
                repeat(UsageHeatmapDaysPerWeek) { weekday ->
                    val day = week * UsageHeatmapDaysPerWeek + weekday - leadingBlanks + 1
                    UsageHeatmapCell(
                        level = if (day in 1..daysInMonth) {
                            heatmapLevel(
                                tokensByDate[firstOfMonth.withDayOfMonth(day).toString()] ?: 0L
                            )
                        } else {
                            -1
                        },
                        currentTheme = currentTheme,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** 比本机最早那份记录还早的月份：没有格子可画，换成一句提示（顺带说明记录是从哪天起的）。 */
@Composable
private fun UsageHeatmapNoHistory(
    earliest: LocalDate?,
    dayFormat: DateTimeFormatter,
    currentTheme: CssVariables,
) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.usage_stats_heatmap_before_start),
            fontSize = UsageBodyFontSize,
            color = currentTheme.mutedForeground,
        )
        if (earliest != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.usage_stats_heatmap_earliest,
                    earliest.format(dayFormat),
                ),
                fontSize = UsageMetaFontSize,
                color = currentTheme.mutedForeground,
            )
        }
    }
}

/**
 * 一格：宽高都由调用方给（`weight(1f)` 分宽度 + `fillMaxHeight()` 分高度），所以它是横向的长方形、
 * 跟着卡片自适应；`level` 为 -1 表示这格不属于本月（空位：什么都不画）。
 *
 * 样式照 GitHub 个人主页的贡献热力图：小圆角、**每一格（含 0 档）都有一圈细边**、底用主题里对应的
 * 那层色 —— 0 档因此不是"什么都没有"，而是一块只有细边的淡底，靠它把没点亮的日子也显出来。
 */
@Composable
private fun UsageHeatmapCell(
    level: Int,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = if (level < 0) {
            modifier
        } else {
            modifier
                .clip(RoundedCornerShape(UsageHeatmapCorner))
                .background(heatmapColor(level, currentTheme))
                .border(
                    width = UsageHeatmapCellBorderWidth,
                    color = currentTheme.border,
                    shape = RoundedCornerShape(UsageHeatmapCorner),
                )
        }
    )
}

/** 表头里的一格：一…日。宽度与格子同权重（列才对得齐），文字居中。 */
@Composable
private fun UsageHeatmapWeekdayLabel(
    label: String,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            fontSize = UsageHeatmapLabelFontSize,
            color = currentTheme.mutedForeground,
        )
    }
}

/**
 * 「模型用量」：直接列模型。
 *
 * 不分供应商那一层 —— 那会和下面的模型行重复；同一个模型 id 在不同供应商下也算一行（按模型名
 * 合并），窗口内谁花得多谁排前面。
 */
@Composable
private fun UsageModelsCard(
    usage: AppUsage,
    isLoading: Boolean,
    currentTheme: CssVariables,
) {
    val models: List<ModelUsage> = usage.models

    UsageCard(currentTheme = currentTheme) {
        Column(verticalArrangement = Arrangement.spacedBy(UsageRowSpacing)) {
            Text(
                text = stringResource(R.string.usage_stats_models_title),
                fontSize = UsageTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = currentTheme.foreground,
            )
            when {
                models.isNotEmpty() -> {
                    val total = models.sumOf { it.tokens }
                    // 表头：三列与下面每一行完全同构（模型名抢剩余宽度，Token 与占比定宽右对齐），
                    // 表头与数据才竖着对齐成一张表。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UsageModelsHeaderCell(
                            text = stringResource(R.string.usage_stats_models_column_model),
                            alignment = TextAlign.Start,
                            modifier = Modifier.weight(1f),
                            currentTheme = currentTheme,
                        )
                        Spacer(modifier = Modifier.width(UsageModelsColumnGap))
                        UsageModelsHeaderCell(
                            text = stringResource(R.string.usage_stats_models_column_tokens),
                            alignment = TextAlign.End,
                            modifier = Modifier.width(UsageModelsTokensColumnWidth),
                            currentTheme = currentTheme,
                        )
                        Spacer(modifier = Modifier.width(UsageModelsColumnGap))
                        UsageModelsHeaderCell(
                            text = stringResource(R.string.usage_stats_models_column_share),
                            alignment = TextAlign.End,
                            modifier = Modifier.width(UsageModelsShareColumnWidth),
                            currentTheme = currentTheme,
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(UsageModelsHeaderDivider)
                            .background(currentTheme.border)
                    )
                    models.forEach { model ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.modelId,
                                fontSize = UsageBodyFontSize,
                                color = currentTheme.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(UsageModelsColumnGap))
                            Text(
                                text = compactTokens(model.tokens),
                                fontSize = UsageMetaFontSize,
                                color = currentTheme.foreground,
                                maxLines = 1,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(UsageModelsTokensColumnWidth),
                            )
                            Spacer(modifier = Modifier.width(UsageModelsColumnGap))
                            Text(
                                text = percentText(shareOf(model.tokens, total)),
                                fontSize = UsageMetaFontSize,
                                color = currentTheme.mutedForeground,
                                maxLines = 1,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(UsageModelsShareColumnWidth),
                            )
                        }
                    }
                }
                isLoading -> Text(
                    text = stringResource(R.string.usage_stats_loading),
                    fontSize = UsageBodyFontSize,
                    color = currentTheme.mutedForeground,
                )
                else -> Text(
                    text = stringResource(R.string.usage_stats_empty),
                    fontSize = UsageBodyFontSize,
                    color = currentTheme.mutedForeground,
                )
            }
        }
    }
}

/** 表头里的一格：小字、静音前景，宽度与对齐由调用方给（必须与数据行同宽）。 */
@Composable
private fun UsageModelsHeaderCell(
    text: String,
    alignment: TextAlign,
    currentTheme: CssVariables,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = UsageMetaFontSize,
        color = currentTheme.mutedForeground,
        maxLines = 1,
        textAlign = alignment,
        modifier = modifier,
    )
}

/** 一张卡：`card` 底、hairline 边、圆角，内容自带内边距。 */
@Composable
private fun UsageCard(
    currentTheme: CssVariables,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UsageCardCorner))
            .background(currentTheme.card)
            .border(1.dp, currentTheme.border, RoundedCornerShape(UsageCardCorner))
            .padding(UsageCardPadding)
    ) {
        content()
    }
}

/** 「名字 + 数值」两行。 */
@Composable
private fun UsageLabeledValue(label: String, value: String, currentTheme: CssVariables) {
    Column {
        Text(
            text = label,
            fontSize = UsageMetaFontSize,
            color = currentTheme.mutedForeground,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = UsageValueFontSize,
            fontWeight = FontWeight.SemiBold,
            color = currentTheme.foreground,
        )
    }
}

// ── 计算与格式化 ──────────────────────────────────────────────────────

/**
 * 一格的色阶：按**当天花了多少 token** 固定分档，不跟别的日子比。
 *
 * 相对分档会骗人 —— 本月只有一天有数据时，那天自动成了「最忙」，于是刚聊几句就顶格变黑。固定
 * 档位下，几 K 的一天是浅的，几十 K 才深。
 */
private fun heatmapLevel(tokens: Long): Int {
    if (tokens <= 0) return 0
    return 1 + UsageHeatmapLevelTokens.count { tokens >= it }
}

/**
 * 档位色直接取上游 ZCode 的 `--color-usage-heatmap-0..4` —— 那边**正好是 5 档**，
 * 与本页的 0..4 档一一对应，不用再自己用 `lerp` 往主色里混。
 * 档 0 是"空面色"（有底但不着色，配格子的细边才看得出这个月有几天）；1..4 由浅到深。
 */
private fun heatmapColor(level: Int, currentTheme: CssVariables): Color =
    when (level.coerceIn(0, 4)) {
        0 -> currentTheme.zcode.usageHeatmap0
        1 -> currentTheme.zcode.usageHeatmap1
        2 -> currentTheme.zcode.usageHeatmap2
        3 -> currentTheme.zcode.usageHeatmap3
        else -> currentTheme.zcode.usageHeatmap4
    }

private fun shareOf(tokens: Long, total: Long): Double =
    if (total <= 0) 0.0 else tokens.toDouble() / total.toDouble()

/** 「2.2万」：Android 上要用 `android.icu` 那个紧凑记法（`java.text` 的版本编不过）。 */
private fun compactTokens(tokens: Long): String =
    CompactDecimalFormat
        .getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT)
        .format(tokens)

private fun percentText(ratio: Double): String =
    NumberFormat.getPercentInstance(Locale.getDefault())
        .apply { maximumFractionDigits = if (ratio >= 0.1) 0 else 1 }
        .format(ratio.coerceIn(0.0, 1.0))
