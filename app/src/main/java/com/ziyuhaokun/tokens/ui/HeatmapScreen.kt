package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** 月份标签列宽：11sp 的「11月」正好放得下。 */
private val HeatLabelWidth = 34.dp

/** 标签列和热力区之间的留白。 */
private val HeatLabelGap = 6.dp

private val HeatCellGap = 2.dp
private val HeatCellRadius = 2.dp

/**
 * 行高下限。月份标签是 11sp、行高约 16dp，行距再紧标签就会上下叠字，
 * 所以格子小的时候行高由文字决定，格子在自己那一行里垂直居中。
 * 这是字体倍率 1.0 时的值，实际按 LocalDensity.fontScale 放大。
 */
private val HeatMinRowHeight = 16.dp

/** 年份标签带（「2025」）的高度，只出现在跨年的那一行上方。 */
private val HeatYearBand = 16.dp

private val HeatTickHeight = 16.dp

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
    ScreenScaffold(refreshing = state.loading, onRefresh = onRefresh) {
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
            Spacer(Modifier.height(TokensSpace.CardGap))
        }

        if (data == null) {
            EmptyCard(loading = state.loading)
            Spacer(Modifier.height(TokensSpace.CardGap))
            Footer(fetchedAt = state.fetchedAt, stale = state.stale, loading = state.loading, onRefresh = onRefresh)
            Spacer(Modifier.height(28.dp))
            return@ScreenScaffold
        }

        YearHero(data = data, metric = state.metric)
        Spacer(Modifier.height(TokensSpace.CardGap))
        HeatCard(data = data, metric = state.metric, onMetric = onMetric)
        Spacer(Modifier.height(TokensSpace.CardGap))
        YearStats(data = data, metric = state.metric)
        Spacer(Modifier.height(TokensSpace.CardGap))
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
            fontSize = TokensType.Caption,
        )
        Spacer(Modifier.height(8.dp))
        // BigMoney 自己会把 "$" 拆成小号前缀，这里不用手工拆。
        if (metric == YearMetric.COST) {
            val (integer, decimals) = Fmt.moneyParts(data.totalCost)
            BigMoney(integer = integer, decimals = decimals)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${data.activeDays} 天有用量 · ${Fmt.tokens(data.totalTokens)} tokens",
                color = Palette.Dim,
                fontSize = TokensType.Caption,
            )
        } else {
            val (integer, rest) = Fmt.tokensParts(data.totalTokens)
            BigMoney(integer = integer, decimals = rest)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${data.activeDays} 天有用量 · ${Fmt.money(data.totalCost)}",
                color = Palette.Dim,
                fontSize = TokensType.Caption,
            )
        }
    }
}

/**
 * 一行热力格的几何。绘制和点击命中共用同一份，避免两处各算一遍又算不齐。
 */
private data class HeatRowLayout(
    val month: Int,
    val year: Int,
    val days: List<LocalDate?>,
    /** 这一行上方的年份标签带高度：只有跨年那一行才有。 */
    val band: Dp,
    /** 格子顶边相对热力区顶部的偏移。 */
    val top: Dp,
)

private data class HeatGridLayout(
    val rowHeight: Dp,
    val rows: List<HeatRowLayout>,
    val height: Dp,
)

/**
 * 365 天窗口必然横跨两个同月（例如 2025-11 和 2026-11），光看「11月」
 * 分不清是哪一年，所以在跨年处留一条年份标签带。首行也带一条，
 * 让第一段的年份有出处。
 */
private fun heatGridLayout(
    months: List<Pair<Int, List<LocalDate?>>>,
    cell: Dp,
    textScale: Float,
    fallbackYear: Int,
): HeatGridLayout {
    val rowHeight = maxOf(cell + HeatCellGap, HeatMinRowHeight * textScale)
    val bandHeight = HeatYearBand * textScale
    var top = 0.dp
    var lastYear: Int? = null
    val rows = months.map { (month, days) ->
        val year = days.firstNotNullOfOrNull { it }?.year ?: lastYear ?: fallbackYear
        val band = if (year != lastYear) bandHeight else 0.dp
        val rowTop = top + band
        top = rowTop + rowHeight
        lastYear = year
        HeatRowLayout(month = month, year = year, days = days, band = band, top = rowTop)
    }
    return HeatGridLayout(rowHeight = rowHeight, rows = rows, height = top)
}

/**
 * 日历格：每行一个月，每列一天（1..31）。手机宽度下这样比
 * GitHub 那种 53 列的周视图好读得多，也正好对应参考图「一年，一看就看全」。
 *
 * 格子整块画在一张 Canvas 上（13 × 31 ≈ 400 个格），点击靠 pointerInput 命中测试；
 * 一个月 400 个可点 Box 的写法在低端机上光是组合就要几十毫秒。
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
    // 标签字号随主题放大，行高和年份带也要跟着放大，否则大字号下月份会叠在一起。
    val textScale = LocalDensity.current.fontScale

    SoftCard(contentPadding = TokensSpace.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("每日用量")
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${data.activeDays}/${data.days.size} 天",
                color = Palette.Dim,
                fontSize = TokensType.Micro,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            MetricSwitch(selected = metric, onSelect = onMetric)
        }

        Spacer(Modifier.height(12.dp))

        // 月份标签列 + 31 天的格子。格子边长按可用宽度算出来，
        // 底色、选中描边、点击命中都用同一份几何。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = (maxWidth - HeatLabelWidth - HeatLabelGap).coerceAtLeast(HeatCellGap)
            val cell = ((gridWidth - HeatCellGap * 30) / 31).coerceAtLeast(1.dp)
            val layout = remember(data, cell, textScale) {
                heatGridLayout(months, cell, textScale, data.end.year)
            }
            val tickHeight = HeatTickHeight * textScale

            Column {
                // 日期刻度：1 / 10 / 20 / 31，跟格子同列对齐。
                Row {
                    Spacer(Modifier.width(HeatLabelWidth + HeatLabelGap))
                    for (day in 1..31) {
                        Box(
                            modifier = Modifier.weight(1f).height(tickHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day == 1 || day == 10 || day == 20 || day == 31) {
                                Text(
                                    text = "$day",
                                    color = Palette.Dim,
                                    fontSize = TokensType.Micro,
                                    fontFamily = NumericFont,
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
                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth()) {
                    // 月份 / 年份标签列：行高和 Canvas 里那一行完全一致，所以天然对齐。
                    Column(Modifier.width(HeatLabelWidth)) {
                        layout.rows.forEach { row ->
                            if (row.band > 0.dp) {
                                Box(
                                    modifier = Modifier.height(row.band),
                                    contentAlignment = Alignment.BottomStart,
                                ) {
                                    Text(
                                        text = "${row.year}",
                                        color = Palette.Dim,
                                        fontSize = TokensType.Micro,
                                        fontFamily = NumericFont,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.height(layout.rowHeight),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = "${row.month}月",
                                    color = Palette.Dim,
                                    fontSize = TokensType.Micro,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(HeatLabelGap))
                    Canvas(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(layout.height)
                            .semantics {
                                contentDescription = "过去一年每日用量热力图，${data.activeDays} 天有用量"
                            }
                            .pointerInput(data, cell, textScale) {
                                detectTapGestures { offset ->
                                    val gapPx = HeatCellGap.toPx()
                                    val cellPx = cell.toPx()
                                    val rowHeightPx = layout.rowHeight.toPx()
                                    // 点在格子间隙里也算这一格，手指不用那么准。
                                    val column = (offset.x / (cellPx + gapPx)).toInt()
                                    if (column !in 0..30) return@detectTapGestures
                                    val row = layout.rows.firstOrNull { row ->
                                        val cellTop = row.top.toPx() + (rowHeightPx - cellPx) / 2f
                                        offset.y >= cellTop - gapPx / 2f &&
                                            offset.y <= cellTop + cellPx + gapPx / 2f
                                    } ?: return@detectTapGestures
                                    // date == null 是窗口外的空格，不画也不响应。
                                    val date = row.days[column] ?: return@detectTapGestures
                                    selected = if (selected == date) null else date
                                }
                            },
                    ) {
                        val gapPx = HeatCellGap.toPx()
                        val cellPx = (size.width - gapPx * 30) / 31f
                        val rowHeightPx = layout.rowHeight.toPx()
                        val corner = CornerRadius(HeatCellRadius.toPx())
                        val outline = 1.5.dp.toPx()
                        layout.rows.forEach { row ->
                            val cellTop = row.top.toPx() + (rowHeightPx - cellPx) / 2f
                            row.days.forEachIndexed { index, date ->
                                if (date == null) return@forEachIndexed
                                val level = levelOf(byDate[date], metric, thresholds)
                                val left = index * (cellPx + gapPx)
                                drawRoundRect(
                                    color = if (level == 0) empty else ramp[level - 1],
                                    topLeft = Offset(left, cellTop),
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = corner,
                                )
                                if (date == selected) {
                                    // 描边往里收半条线，不然会被格子边界切掉一半。
                                    val half = outline / 2f
                                    drawRoundRect(
                                        color = colors.text,
                                        topLeft = Offset(left + half, cellTop + half),
                                        size = Size(cellPx - outline, cellPx - outline),
                                        cornerRadius = corner,
                                        style = Stroke(width = outline),
                                    )
                                }
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
            color = Palette.Dim,
            fontSize = TokensType.Micro,
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Field)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        YearMetric.entries.forEach { metric ->
            val isOn = metric == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOn) Palette.CardTop else Color.Transparent)
                    .then(if (isOn) Modifier.border(1.dp, Palette.Hairline, RoundedCornerShape(9.dp)) else Modifier)
                    .semantics { this.selected = isOn }
                    .clickable(role = Role.Tab) { if (!isOn) onSelect(metric) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = metric.label,
                    color = if (isOn) Palette.Text else Palette.Dim,
                    fontSize = TokensType.Label,
                    fontWeight = if (isOn) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Legend(thresholds: DoubleArray, metric: YearMetric, empty: Color, ramp: List<Color>) {
    val swatch = RoundedCornerShape(2.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("少", color = Palette.Dim, fontSize = TokensType.Micro)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(11.dp).height(11.dp).clip(swatch).background(empty))
        Spacer(Modifier.width(3.dp))
        ramp.forEach { color ->
            Box(Modifier.width(11.dp).height(11.dp).clip(swatch).background(color))
            Spacer(Modifier.width(3.dp))
        }
        Text("多", color = Palette.Dim, fontSize = TokensType.Micro)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (metric == YearMetric.COST) "按每日费用" else "按每日 tokens",
            color = Palette.Dim,
            fontSize = TokensType.Micro,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayDetail(date: LocalDate?, entry: DayUsage?, metric: YearMetric) {
    if (date == null) {
        Text(
            text = "点任意一格看当天明细",
            color = Palette.Dim,
            fontSize = TokensType.Caption,
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
            fontSize = TokensType.Body,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (metric == YearMetric.COST) Fmt.money(cost) else Fmt.tokens(tokens),
            color = Palette.Text,
            fontSize = TokensType.Value,
            fontFamily = NumericFont,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (metric == YearMetric.COST) Fmt.tokens(tokens) else Fmt.money(cost),
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            fontFamily = NumericFont,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (active > 0) "活跃 ${Fmt.duration(active)}" else "无会话记录",
        color = Palette.Dim,
        fontSize = TokensType.Micro,
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
        Text(label, color = Palette.Dim, fontSize = TokensType.Caption)
        Spacer(Modifier.height(3.dp))
        // 值里常带「天」这种中文：不用等宽字体，否则空格被撑得很宽。
        Text(value, color = Palette.Text, fontSize = TokensType.Title, fontWeight = FontWeight.Light, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(hint, color = Palette.Dim, fontSize = TokensType.Micro)
    }
}

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

/**
 * 用非零日的分位数当色阶阈值：不同账号的量级差几个数量级，固定阈值会一片死黑。
 *
 * 阈值必须是 Double：取整会把 $0.3 和 $1.9 压成同一档，小额日全都一个颜色。
 */
internal fun thresholds(data: YearData, metric: YearMetric): DoubleArray {
    val values = data.days.map { valueOf(it, metric) }.filter { it > 0.0 }.sorted()
    if (values.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0)
    fun at(p: Double): Double = values[((values.size - 1) * p).toInt()]
    return doubleArrayOf(at(0.4), at(0.7), at(0.9))
}

internal fun levelOf(day: DayUsage?, metric: YearMetric, thresholds: DoubleArray): Int {
    if (day == null || !day.hasUsage) return 0
    val value = valueOf(day, metric)
    return when {
        value <= thresholds[0] -> 1
        value <= thresholds[1] -> 2
        value <= thresholds[2] -> 3
        else -> 4
    }
}
