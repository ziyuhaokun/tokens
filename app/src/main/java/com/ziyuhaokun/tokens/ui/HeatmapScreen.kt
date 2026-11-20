package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.DayUsage
import com.ziyuhaokun.tokens.data.YearData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 热力图看哪个指标。 */
enum class YearMetric(val label: String) {
    COST("费用"),
    TOKENS("Tokens"),
}

/**
 * 热力色阶：从主题正文色派生，深色下是「灰 → 白」，浅色下是「浅灰 → 近黑」。
 * 不写死两套色值，浅色主题里也不会出现看不见的白块。
 */
private fun heatRamp(colors: TokensColors): List<Color> = listOf(
    colors.text.copy(alpha = 0.20f),
    colors.text.copy(alpha = 0.38f),
    colors.text.copy(alpha = 0.62f),
    // 最亮一档跟随强调色：单色时就是正文色，和原来完全一致。
    colors.accent,
)

@Composable
fun HeatmapScreen(
    state: HeatmapState,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onMetric: (YearMetric) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val data = state.data
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader(
            subtitle = state.accountHandle ?: "vibecafe.ai",
            tab = tab,
            onTab = onTab,
            onSettings = onSettings,
        )
        Spacer(Modifier.height(18.dp))

        if (state.error != null) {
            ErrorCard(message = state.error, staleAt = if (state.stale) state.fetchedAt else null)
            Spacer(Modifier.height(12.dp))
        }

        if (data == null) {
            EmptyCard(loading = state.loading)
            Spacer(Modifier.height(16.dp))
            Footer(fetchedAt = state.fetchedAt, stale = state.stale, loading = state.loading, onRefresh = onRefresh)
            Spacer(Modifier.height(28.dp))
            return@Column
        }

        YearHero(data = data, metric = state.metric)
        Spacer(Modifier.height(12.dp))
        HeatCard(data = data, metric = state.metric, onMetric = onMetric)
        Spacer(Modifier.height(12.dp))
        YearStats(data = data, metric = state.metric)
        Spacer(Modifier.height(16.dp))
        Footer(fetchedAt = state.fetchedAt, stale = state.stale, loading = state.loading, onRefresh = onRefresh)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun YearHero(data: YearData, metric: YearMetric) {
    Column {
        Text(
            text = "过去一年",
            color = Palette.Muted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        if (metric == YearMetric.COST) {
            val (integer, decimals) = Fmt.moneyParts(data.totalCost)
            BigMoney(integer = integer, decimals = decimals)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${data.activeDays} 天有用量 · ${Fmt.tokens(data.totalTokens)} tokens",
                color = Palette.Dim,
                fontSize = 11.sp,
            )
        } else {
            val (integer, rest) = Fmt.tokensParts(data.totalTokens)
            BigMoney(integer = integer, decimals = rest)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${data.activeDays} 天有用量 · ${Fmt.money(data.totalCost)}",
                color = Palette.Dim,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 日历格：每行一个月，每列一天（1..31）。手机宽度下这样比
 * GitHub 那种 53 列的周视图好读得多，也正好对应参考图「一年，一看就看全」。
 */
@Composable
private fun HeatCard(data: YearData, metric: YearMetric, onMetric: (YearMetric) -> Unit) {
    val byDate = remember(data) { data.days.associateBy { it.date } }
    val thresholds = remember(data, metric) { thresholds(data, metric) }
    val colors = LocalTokensColors.current
    val ramp = remember(colors) { heatRamp(colors) }
    val empty = colors.hairlineSoft
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val months = remember(data) { monthsOf(data) }

    SoftCard(contentPadding = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("每日用量")
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${data.activeDays}/${data.days.size} 天",
                color = Palette.Dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            MetricSwitch(selected = metric, onSelect = onMetric)
        }

        Spacer(Modifier.height(12.dp))

        // 月份标签列 + 31 天的格子。格子边长按可用宽度算出来，
        // 标签行和格子行用同一个尺寸，否则会逐行累积累积偏移。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 1.dp
            val tickHeight = 12.dp
            val rowGap = 2.dp
            val cell = (maxWidth - LABEL_WIDTH - gap * 31) / 31

            Column {
                // 日期刻度：1 / 10 / 20 / 31
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.width(LABEL_WIDTH))
                    for (day in 1..31) {
                        Box(
                            modifier = Modifier.weight(1f).height(tickHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day == 1 || day == 10 || day == 20 || day == 31) {
                                Text(
                                    text = "$day",
                                    color = Palette.Faint,
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.wrapContentWidth(
                                        align = Alignment.CenterHorizontally,
                                        unbounded = true,
                                    ),
                                )
                            }
                        }
                    }
                }

                // 每个月一行。月份标签放在行内，跟格子同高，纵向天然对齐。
                months.forEach { (month, days) ->
                    Row(
                        modifier = Modifier.padding(bottom = rowGap),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        Box(
                            modifier = Modifier.width(LABEL_WIDTH).height(cell),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "${month}月",
                                color = Palette.Dim,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentHeight(
                                    align = Alignment.CenterVertically,
                                    unbounded = true,
                                ),
                            )
                        }
                        for (day in 1..31) {
                            val date = days.getOrNull(day - 1)
                            if (date == null) {
                                Box(Modifier.weight(1f).height(cell))
                            } else {
                                val entry = byDate[date]
                                val level = levelOf(entry, metric, thresholds)
                                val isSelected = selected == date
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(cell)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (level == 0) empty else ramp[level - 1])
                                        .then(
                                            if (isSelected) Modifier.border(1.dp, Palette.Text, RoundedCornerShape(2.dp))
                                            else Modifier
                                        )
                                        .clickable { selected = if (isSelected) null else date },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "${data.start.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))} – " +
                data.end.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)),
            color = Palette.Faint,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Legend(thresholds = thresholds, metric = metric, empty = empty, ramp = ramp)

        Spacer(Modifier.height(10.dp))
        HairlineDivider()
        Spacer(Modifier.height(10.dp))
        DayDetail(date = selected, entry = selected?.let { byDate[it] }, metric = metric)
    }
}

@Composable
private fun MetricSwitch(selected: YearMetric, onSelect: (YearMetric) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        YearMetric.entries.forEach { metric ->
            val isOn = metric == selected
            Text(
                text = metric.label,
                color = if (isOn) Palette.Text else Palette.Dim,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isOn) Palette.Hairline else Color.Transparent)
                    .clickable { onSelect(metric) }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Legend(thresholds: IntArray, metric: YearMetric, empty: Color, ramp: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("少", color = Palette.Faint, fontSize = 8.sp)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).background(empty))
        Spacer(Modifier.width(3.dp))
        ramp.forEach { color ->
            Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(3.dp))
        }
        Text("多", color = Palette.Faint, fontSize = 8.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (metric == YearMetric.COST) "按每日费用" else "按每日 tokens",
            color = Palette.Faint,
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun DayDetail(date: LocalDate?, entry: DayUsage?, metric: YearMetric) {
    if (date == null) {
        Text(
            text = "点任意一格看当天明细",
            color = Palette.Faint,
            fontSize = 10.sp,
        )
        return
    }
    val cost = entry?.cost ?: 0.0
    val tokens = entry?.tokens ?: 0L
    val active = entry?.activeSeconds ?: 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)),
            color = Palette.TextSoft,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (metric == YearMetric.COST) Fmt.money(cost) else Fmt.tokens(tokens),
            color = Palette.Text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (metric == YearMetric.COST) Fmt.tokens(tokens) else Fmt.money(cost),
            color = Palette.Dim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (active > 0) "活跃 ${Fmt.duration(active)}" else "无会话记录",
        color = Palette.Faint,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun YearStats(data: YearData, metric: YearMetric) {
    SoftCard {
        Row {
            StatBlock(
                label = "活跃天数",
                value = "${data.activeDays}",
                hint = "共 ${data.days.size} 天",
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                label = "最长连续",
                value = "${data.longestStreak} 天",
                hint = "每天都有用量",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            val best = data.bestDay
            StatBlock(
                label = "最高一天",
                value = best?.let { if (metric == YearMetric.COST) Fmt.money(it.cost) else Fmt.tokens(it.tokens) } ?: "—",
                hint = best?.date?.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) ?: "",
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                label = "日均",
                value = if (metric == YearMetric.COST) Fmt.money(data.averageCostPerActiveDay)
                else Fmt.tokens(data.averageTokensPerActiveDay),
                hint = "按有用量的天算",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Palette.Dim, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(2.dp))
        Text(hint, color = Palette.Faint, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

private val LABEL_WIDTH = 26.dp

private fun monthAbbrev(month: Int): String = "${month}月"

/**
 * 按「年-月」分行，返回 (月份, 该月在这个窗口里的日期)。
 *
 * 不能只按 monthValue 分组：365 天窗口一定横跨两个同月（例如 2025-11 和
 * 2026-11），按月份号分会把两年的同一个月摞进同一行，日期全错位。
 */
internal fun monthsOf(data: YearData): List<Pair<Int, List<LocalDate?>>> {
    val byMonth = data.days
        .groupBy { it.date.year * 100 + it.date.monthValue }
        .toSortedMap()
    return byMonth.map { (key, days) ->
        // 31 个槽位，按「日」摆到对应列。窗口边缘的月份只有部分日期，
        // 直接按顺序塞会让 21 号跑到第 1 列去。
        val slots = MutableList<LocalDate?>(31) { null }
        for (day in days) slots[day.date.dayOfMonth - 1] = day.date
        (key % 100) to slots
    }
}

private fun valueOf(day: DayUsage?, metric: YearMetric): Double =
    when (metric) {
        YearMetric.COST -> day?.cost ?: 0.0
        YearMetric.TOKENS -> (day?.tokens ?: 0L).toDouble()
    }

/** 用非零日的分位数当色阶阈值：不同账号的量级差几个数量级，固定阈值会一片死黑。 */
internal fun thresholds(data: YearData, metric: YearMetric): IntArray {
    val values = data.days.map { valueOf(it, metric) }.filter { it > 0.0 }.sorted()
    if (values.isEmpty()) return intArrayOf(0, 0, 0)
    fun at(p: Double): Int = values[((values.size - 1) * p).toInt()].toInt().coerceAtLeast(1)
    return intArrayOf(at(0.4), at(0.7), at(0.9))
}

internal fun levelOf(day: DayUsage?, metric: YearMetric, thresholds: IntArray): Int {
    if (day == null || !day.hasUsage) return 0
    val value = valueOf(day, metric).toInt()
    return when {
        value <= thresholds[0] -> 1
        value <= thresholds[1] -> 2
        value <= thresholds[2] -> 3
        else -> 4
    }
}
