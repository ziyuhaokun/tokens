package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
)

/** 图上实际画哪一段 + 两端标签。 */
private class ChartWindow(val values: List<Double>, val startLabel: String, val endLabel: String)

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
            ChartWindow(slice.map { it.cost }, "${slice.first().label}:00", "现在")
        }

        Range.H24 -> ChartWindow(trend.map { it.cost }, "${trend.first().label}:00", "现在")
        else -> ChartWindow(trend.map { it.cost }, trend.first().label, "今天")
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
) {
    val usage = state.usage
    ScreenScaffold(refreshing = state.loading, onRefresh = onRefresh) {
        Spacer(Modifier.height(10.dp))

        // 顶部：标题 + 一级视图切换 + 设置（两个页面共用同一个头）
        ScreenHeader(
            subtitle = usage?.accountHandle ?: "vibecafe.ai",
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

            HeroCard(range = state.range, totals = totals, chart = model.chart, loading = state.loading)

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
                rows = model.bySource.take(6),
                hint = "${totals.sources} 个来源",
            )

            Spacer(Modifier.height(TokensSpace.CardGap))
            RankingCard(
                title = "按模型",
                rows = model.byModel.take(6),
                hint = "费用 / Tokens",
            )

            val projects = model.byProject
            val onlyUnknown = projects.isNotEmpty() && projects.all { it.key == "unknown" }
            Spacer(Modifier.height(TokensSpace.CardGap))
            RankingCard(
                title = "按项目",
                rows = projects.take(5),
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
) {
    val (integer, decimals) = Fmt.moneyParts(totals.cost)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(range.label)
            Spacer(Modifier.weight(1f))
            if (loading) SmallLabel("刷新中…")
        }

        Spacer(Modifier.height(8.dp))

        val large = isLargeText()
        // 大字号下金额本身不再跟着放大（不然一行放不下），正文照常放大。
        val moneySize = when {
            !large -> TokensType.Display
            LocalFontScale.current >= 1.3f -> 32.sp
            else -> 38.sp
        }
        if (large) {
            BigMoney(integer = integer, decimals = decimals, size = moneySize)
            SmallLabel("预估费用")
            Spacer(Modifier.height(12.dp))
            Row {
                StatPair("Token", Fmt.tokens(totals.tokens))
                Spacer(Modifier.width(18.dp))
                StatPair("活跃", Fmt.duration(totals.activeSeconds))
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    BigMoney(integer = integer, decimals = decimals, size = moneySize)
                    SmallLabel("预估费用")
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    StatPair("Token", Fmt.tokens(totals.tokens))
                    Spacer(Modifier.height(6.dp))
                    StatPair("活跃", Fmt.duration(totals.activeSeconds))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        AreaChart(
            values = chart.values,
            startLabel = chart.startLabel,
            endLabel = chart.endLabel,
            description = "${range.label} 费用趋势，合计 ${Fmt.money(totals.cost)}",
        )
    }
}

@Composable
private fun CacheCard(cachedInput: Long, tokens: Long, modifier: Modifier = Modifier) {
    val ratio = if (tokens > 0) (cachedInput.toFloat() / tokens.toFloat()) else 0f
    SoftCard(modifier = modifier, contentPadding = TokensSpace.CardPaddingCompact) {
        CardLabel("缓存读")
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DialRing(
                progress = ratio,
                value = "${(ratio * 100).toInt()}",
                caption = "占 tokens",
                size = 120.dp,
                description = "缓存读占 tokens ${(ratio * 100).toInt()}%",
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                SmallLabel("缓存读")
                Text(
                    text = Fmt.tokens(cachedInput),
                    color = Palette.TextSoft,
                    fontSize = TokensType.Value,
                    fontFamily = NumericFont,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                SmallLabel("总计")
                Text(
                    text = Fmt.tokens(tokens),
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
        Spacer(Modifier.height(16.dp))
        Text(
            text = Fmt.duration(activeSeconds),
            color = Palette.Text,
            fontSize = if (isLargeText()) 26.sp else 32.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
        )
        SmallLabel("生成耗时合计")
        Spacer(Modifier.height(16.dp))
        StatPair("Sessions", sessions.toString())
        Spacer(Modifier.height(6.dp))
        StatPair("设备", "$hosts 台")
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
