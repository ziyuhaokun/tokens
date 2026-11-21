package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageAgg
import java.time.LocalTime

/** 首页要展示的全部聚合结果。按 (usage, range) 算一次，不在每次重组时重算。 */
private class DashboardModel(
    val totals: UsageAgg.Totals,
    val chart: ChartWindow,
    val bySource: List<UsageAgg.Row>,
    val byModel: List<UsageAgg.Row>,
    val byProject: List<UsageAgg.Row>,
) {
    // 排行卡只显示前几项。在模型里算一次，不要在每次重组里重新 take 出新列表（每次都是新对象，卡片就跟着白重组）。
    val topSources: List<UsageAgg.Row> = bySource.take(6)
    val topModels: List<UsageAgg.Row> = byModel.take(6)
    val topProjects: List<UsageAgg.Row> = byProject.take(5)
}

/** 图上实际画哪一段 + 两端标签。 */
private class ChartWindow(
    val values: List<Double>,
    val startLabel: String,
    val endLabel: String,
    /** 每个点对应的时间标签（按住查看时气泡里显示）。 */
    val pointLabels: List<String> = emptyList(),
)

private fun buildModel(usage: Usage, range: Range): DashboardModel = DashboardModel(
    totals = UsageAgg.totals(usage),
    chart = chartWindow(UsageAgg.trend(usage, range), range),
    bySource = UsageAgg.bySource(usage),
    byModel = UsageAgg.byModel(usage),
    byProject = UsageAgg.byProject(usage),
)

/**
 * 「今天」只画到当前小时，并从第一个有用量的小时开始（桌面端「9:00 → 现在」）；
 * 否则后半天全是 0，曲线会拖一条长长的平线，末点也不在「现在」。
 */
private fun chartWindow(trend: List<UsageAgg.TrendPoint>, range: Range): ChartWindow {
    if (trend.isEmpty()) return ChartWindow(emptyList(), "", "")
    return when (range) {
        Range.TODAY -> {
            val end = LocalTime.now().hour.coerceIn(1, trend.lastIndex)
            val firstActive = trend.indexOfFirst { it.cost > 0.0 || it.tokens > 0 }
            val start = when {
                firstActive < 0 -> 0
                firstActive >= end -> end - 1
                else -> firstActive
            }
            val slice = trend.subList(start, end + 1)
            ChartWindow(slice.map { it.cost }, "${slice.first().label}:00", "现在", slice.map { "${it.label}:00" })
        }

        Range.H24 -> ChartWindow(trend.map { it.cost }, "${trend.first().label}:00", "现在", trend.map { "${it.label}:00" })
        else -> ChartWindow(trend.map { it.cost }, trend.first().label, "今天", trend.map { it.label })
    }
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onRange: (Range) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onPrint: () -> Unit,
) {
    val usage = state.usage
    ScreenScaffold(refreshing = state.loading, onRefresh = onRefresh) {
        Spacer(Modifier.height(10.dp))

        // 顶部：标题 + 一级视图切换 + 设置（两个页面共用同一个头）
        ScreenHeader(
            subtitle = Fmt.handle(usage?.accountHandle),
            tab = tab,
            onTab = onTab,
            onSettings = onSettings,
        )

        Spacer(Modifier.height(10.dp))
        RangeTabs(selected = state.range, onSelect = onRange)
        Spacer(Modifier.height(10.dp))

        if (state.error != null) {
            ErrorCard(message = state.error, staleAt = if (state.stale) state.fetchedAt else null)
            Spacer(Modifier.height(TokensSpace.CardGap))
        }

        if (usage == null) {
            EmptyCard(loading = state.loading)
        } else {
            val model = remember(usage, state.range) { buildModel(usage, state.range) }
            val totals = model.totals

            HeroCard(range = state.range, totals = totals, chart = model.chart, loading = state.loading, onPrint = onPrint)

            Spacer(Modifier.height(TokensSpace.CardGap))
            if (isLargeText()) {
                // 一列放两张：大字号下并排会把数字挤到换行。
                CacheCard(cachedInput = totals.cachedInput, tokens = totals.tokens)
                Spacer(Modifier.height(TokensSpace.CardGap))
                ActivityCard(
                    activeSeconds = totals.activeSeconds,
                    sessions = totals.sessions,
                    hosts = totals.hosts,
                )
            } else {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(TokensSpace.CardGap),
                ) {
                    CacheCard(
                        cachedInput = totals.cachedInput,
                        tokens = totals.tokens,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ActivityCard(
                        activeSeconds = totals.activeSeconds,
                        sessions = totals.sessions,
                        hosts = totals.hosts,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            Spacer(Modifier.height(TokensSpace.CardGap))
            RankingCard(
                title = "按工具",
                rows = model.topSources,
                hint = "${totals.sources} 个来源",
            )

            Spacer(Modifier.height(TokensSpace.CardGap))
            RankingCard(
                title = "按模型",
                rows = model.topModels,
                hint = "费用 / Tokens",
            )

            val projects = model.byProject
            val onlyUnknown = projects.isNotEmpty() && projects.all { it.key == "unknown" }
            Spacer(Modifier.height(TokensSpace.CardGap))
            RankingCard(
                title = "按项目",
                rows = model.topProjects,
                hint = if (onlyUnknown) "服务端未收集项目名" else "费用 / Sessions",
                showSessions = !onlyUnknown,
                footnote = if (onlyUnknown) {
                    "上传设置里 uploadProject 关闭时（默认），所有用量都归到 unknown。"
                } else null,
            )
        }

        Spacer(Modifier.height(12.dp))
        Footer(
            fetchedAt = state.fetchedAt,
            stale = state.stale,
            loading = state.loading,
            onRefresh = onRefresh,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HeroCard(
    range: Range,
    totals: UsageAgg.Totals,
    chart: ChartWindow,
    loading: Boolean,
    onPrint: () -> Unit,
) {
    val nf = LocalNumberFormats.current
    val (integer, decimals) = nf.moneyParts(totals.cost)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(range.label)
            Spacer(Modifier.width(8.dp))
            if (loading) SmallLabel("刷新中…")
            Spacer(Modifier.weight(1f))
            // 把当前范围打成一张可保存 / 分享的小票。右侧贴边，点击区域仍然 44dp。
            TextAction(text = "打印小票", onClick = onPrint, modifier = Modifier.offset(x = 10.dp))
        }

        Spacer(Modifier.height(4.dp))

        val large = isLargeText()
        // 大字号下金额本身不再跟着放大（不然一行放不下），正文照常放大。
        val moneySize = when {
            !large -> TokensType.Display
            LocalFontScale.current >= 1.3f -> 32.sp
            else -> 38.sp
        }
        // 金额整数段（含符号和千分位）到 7 个字符（例如 ¥274,240）就不再和右侧统计挤一行：
        // 并排时左边只剩一半宽，大数字只能缩得很小。改成上下排，金额独占一整行。
        val stacked = large || integer.length >= 7
        if (stacked) {
            AnimatedBigMoney(cost = totals.cost, size = moneySize)
            SmallLabel("预估费用")
            Spacer(Modifier.height(12.dp))
            // 两项统计放不下一行时自动折到下一行，不会被挤出卡片。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatPair("Token", nf.tokens(totals.tokens))
                StatPair("活跃", Fmt.duration(totals.activeSeconds))
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedBigMoney(cost = totals.cost, size = moneySize)
                    SmallLabel("预估费用")
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    StatPair("Token", nf.tokens(totals.tokens))
                    Spacer(Modifier.height(6.dp))
                    StatPair("活跃", Fmt.duration(totals.activeSeconds))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        AreaChart(
            values = chart.values,
            startLabel = chart.startLabel,
            endLabel = chart.endLabel,
            // 顶部 22dp 留给峰值标签 / 按住查看的气泡，所以比原来的 126dp 高一些，曲线本身的高度基本不变。
            height = 146.dp,
            description = trendDescription(range.label, nf, totals.cost, chart.values, chart.pointLabels),
            pointLabels = chart.pointLabels,
            formatValue = { nf.money(it) },
        )
    }
}

/**
 * 大金额：数值变化时从旧值滚到新值。
 * 单独成一个 Composable：滚动时每帧重组只发生在这里，不会带动整张卡片。
 * 字号按最终金额定（见 [BigMoney] 的 sizing），滚动过程中不会忽大忽小。
 */
@Composable
private fun AnimatedBigMoney(cost: Double, size: androidx.compose.ui.unit.TextUnit) {
    val nf = LocalNumberFormats.current
    val shown = rememberCountUp(target = cost)
    val (integer, decimals) = nf.moneyParts(shown)
    BigMoney(
        integer = integer,
        decimals = decimals,
        size = size,
        sizing = nf.moneyParts(cost),
        modifier = Modifier.semantics { contentDescription = nf.money(cost) },
    )
}

@Composable
private fun CacheCard(cachedInput: Long, tokens: Long, modifier: Modifier = Modifier) {
    val nf = LocalNumberFormats.current
    val ratio = if (tokens > 0) (cachedInput.toFloat() / tokens.toFloat()) else 0f
    // SpaceBetween：卡片被右边的活跃卡拉高时，多出来的空白均分到圆环上下，圆环不再贴顶；
    // 没被拉高时空白为 0，间距由圆环外面的 8dp padding 保证，和原来一样。
    SoftCard(
        modifier = modifier,
        contentPadding = TokensSpace.CardPaddingCompact,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        CardLabel("缓存读")
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            DialRing(
                progress = ratio,
                value = "${(ratio * 100).toInt()}",
                size = 104.dp,
                description = "缓存读占 tokens ${(ratio * 100).toInt()}%",
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                SmallLabel("缓存读")
                Text(
                    text = nf.tokens(cachedInput),
                    color = Palette.TextSoft,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                SmallLabel("总计")
                Text(
                    text = nf.tokens(tokens),
                    color = Palette.TextSoft,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activeSeconds: Long,
    sessions: Int,
    hosts: Int,
    modifier: Modifier = Modifier,
) {
    SoftCard(modifier = modifier, contentPadding = TokensSpace.CardPaddingCompact) {
        CardLabel("活跃")
        Spacer(Modifier.height(12.dp))
        // 一年的活跃时长能到「2459h 46m」：放不下就整体缩小，不再裁掉末尾的 m。
        FitText(
            text = Fmt.duration(activeSeconds),
            color = Palette.Text,
            size = if (isLargeText()) 26.sp else 32.sp,
            letterSpacing = (-0.5).sp,
        )
        SmallLabel("生成耗时合计")
        Spacer(Modifier.height(12.dp))
        StatPair("Sessions", sessions.toString())
        Spacer(Modifier.height(6.dp))
        // 等宽字体里空格占一整格，数字和「台」之间不留空格。
        StatPair("设备", "${hosts}台")
        if (sessions == 0) {
            Spacer(Modifier.height(10.dp))
            SmallLabel("服务端无 sessions")
        }
    }
}

@Composable
private fun RankingCard(
    title: String,
    rows: List<UsageAgg.Row>,
    hint: String? = null,
    showSessions: Boolean = false,
    footnote: String? = null,
) {
    val maxCost = rows.maxOfOrNull { it.cost }?.takeIf { it > 0.0 } ?: 1.0
    SoftCard(contentPadding = TokensSpace.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(title)
            Spacer(Modifier.weight(1f))
            if (hint != null) SmallLabel(hint)
        }
        Spacer(Modifier.height(4.dp))
        if (rows.isEmpty()) {
            Text(
                text = "这个区间没有数据",
                color = Palette.Dim,
                fontSize = TokensType.Body,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            rows.forEach { row ->
                RankingRow(row = row, maxCost = maxCost, showSessions = showSessions)
            }
        }
        if (footnote != null) {
            Spacer(Modifier.height(10.dp))
            SmallLabel(footnote)
        }
    }
}

@Composable
internal fun ErrorCard(message: String, staleAt: Long?) {
    SoftCard(contentPadding = TokensSpace.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Palette.Warn),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = message, color = Palette.Warn, fontSize = TokensType.Body)
        }
        if (staleAt != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "显示上次缓存 · ${Fmt.clock(staleAt)}",
                color = Palette.Dim,
                fontSize = TokensType.Caption,
            )
        }
    }
}

@Composable
internal fun EmptyCard(loading: Boolean) {
    SoftCard {
        Text(
            text = if (loading) "正在拉取…" else "没有数据",
            color = Palette.TextSoft,
            fontSize = TokensType.CardTitle,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "选中区间没有上传记录，或账号还没同步过。桌面端 / CLI 同步后这里会出现数据。",
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            lineHeight = 18.sp,
        )
    }
}

/** 底栏：状态点 + 更新时间，右侧刷新。也可以直接下拉页面刷新。 */
@Composable
internal fun Footer(fetchedAt: Long?, stale: Boolean, loading: Boolean, onRefresh: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (stale) Palette.Dim else Palette.Ok),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "更新于 ${Fmt.clock(fetchedAt)} · ${Fmt.ago(fetchedAt)}",
            color = Palette.Dim,
            fontSize = TokensType.Caption,
            modifier = Modifier.weight(1f),
        )
        TextAction(
            text = if (loading) "刷新中…" else "刷新",
            onClick = onRefresh,
            enabled = !loading,
        )
    }
}

/**
 * 趋势图给读屏用的整段描述。图本身只能靠手指按住拖动看每一天，读屏用户做不到，
 * 所以把最关键的三个数——合计、最高（哪一天）、最新——直接写进描述里。
 */
internal fun trendDescription(
    rangeLabel: String,
    nf: com.ziyuhaokun.tokens.data.NumberFormats,
    total: Double,
    values: List<Double>,
    labels: List<String>,
): String {
    val peakIndex = values.indices.maxByOrNull { values[it] }
    val peak = if (peakIndex != null && values[peakIndex] > 0.0) {
        val at = labels.getOrNull(peakIndex)?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        "，最高 ${nf.money(values[peakIndex])}$at"
    } else {
        ""
    }
    val latest = values.lastOrNull()?.let { "，最新 ${nf.money(it)}" }.orEmpty()
    return "$rangeLabel 费用趋势，合计 ${nf.money(total)}$peak$latest"
}
