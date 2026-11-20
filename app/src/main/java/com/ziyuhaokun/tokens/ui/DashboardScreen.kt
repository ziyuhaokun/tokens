package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageAgg

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // 顶部：标题 + 一级视图切换 + 设置（两个页面共用同一个头）
        ScreenHeader(
            subtitle = usage?.accountHandle ?: "vibecafe.ai",
            tab = tab,
            onTab = onTab,
            onSettings = onSettings,
        )

        Spacer(Modifier.height(16.dp))
        RangeTabs(selected = state.range, onSelect = onRange)
        Spacer(Modifier.height(16.dp))

        if (state.error != null) {
            ErrorCard(message = state.error, staleAt = if (state.stale) state.fetchedAt else null)
            Spacer(Modifier.height(12.dp))
        }

        if (usage == null) {
            EmptyCard(loading = state.loading)
        } else {
            val totals = UsageAgg.totals(usage)
            val trend = UsageAgg.trend(usage, state.range)
            val hourly = state.range.isHourly

            HeroCard(
                range = state.range,
                usage = usage,
                totals = totals,
                trend = trend.map { it.cost },
                startLabel = if (hourly) "00:00" else (trend.firstOrNull()?.label ?: ""),
                endLabel = if (hourly) "现在" else "今天",
                loading = state.loading,
            )

            Spacer(Modifier.height(12.dp))
            if (isLargeText()) {
                // 一列放两张：大字号下并排会把数字挤到换行。
                CacheCard(cachedInput = totals.cachedInput, tokens = totals.tokens)
                Spacer(Modifier.height(12.dp))
                ActivityCard(
                    activeSeconds = totals.activeSeconds,
                    sessions = totals.sessions,
                    hosts = totals.hosts,
                )
            } else {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            Spacer(Modifier.height(12.dp))
            RankingCard(
                title = "按工具",
                rows = UsageAgg.bySource(usage).take(6),
                hint = "${totals.sources} 个来源",
            )

            Spacer(Modifier.height(12.dp))
            RankingCard(
                title = "按模型",
                rows = UsageAgg.byModel(usage).take(6),
                hint = "费用 / Tokens",
            )

            val projects = UsageAgg.byProject(usage)
            val onlyUnknown = projects.isNotEmpty() && projects.all { it.key == "unknown" }
            Spacer(Modifier.height(12.dp))
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

        Spacer(Modifier.height(16.dp))
        Footer(
            fetchedAt = state.fetchedAt,
            stale = state.stale,
            loading = state.loading,
            onRefresh = onRefresh,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HeroCard(
    range: Range,
    usage: Usage,
    totals: UsageAgg.Totals,
    trend: List<Double>,
    startLabel: String,
    endLabel: String,
    loading: Boolean,
) {
    val (integer, decimals) = Fmt.moneyParts(totals.cost)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(range.label)
            Spacer(Modifier.weight(1f))
            if (loading) {
                SmallLabel("刷新中…")
            } else {
                SmallLabel("${usage.buckets.size} buckets")
            }
        }

        Spacer(Modifier.height(10.dp))

        val large = isLargeText()
        // 大字号下金额本身不再跟着放大（不然一行放不下），正文照常放大。
        val moneySize = when {
            !large -> 42
            LocalFontScale.current >= 1.3f -> 30
            else -> 36
        }
        if (large) {
            BigMoney(integer = integer, decimals = decimals, size = moneySize)
            Spacer(Modifier.height(2.dp))
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
                    Spacer(Modifier.height(2.dp))
                    SmallLabel("预估费用")
                }
                Column(horizontalAlignment = Alignment.Start) {
                    StatPair("Token", Fmt.tokens(totals.tokens))
                    Spacer(Modifier.height(6.dp))
                    StatPair("活跃", Fmt.duration(totals.activeSeconds))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        AreaChart(
            values = trend,
            startLabel = startLabel,
            endLabel = endLabel,
        )
    }
}

@Composable
private fun CacheCard(cachedInput: Long, tokens: Long, modifier: Modifier = Modifier) {
    val ratio = if (tokens > 0) (cachedInput.toFloat() / tokens.toFloat()) else 0f
    SoftCard(modifier = modifier, contentPadding = 14) {
        CardLabel("缓存读")
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DialRing(
                progress = ratio,
                centerText = "${(ratio * 100).toInt()}%",
                caption = "占 tokens",
                size = 116.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                SmallLabel("缓存读")
                Text(
                    text = Fmt.tokens(cachedInput),
                    color = Palette.TextSoft,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                SmallLabel("总计")
                Text(
                    text = Fmt.tokens(tokens),
                    color = Palette.TextSoft,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
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
    SoftCard(modifier = modifier, contentPadding = 14) {
        CardLabel("活跃")
        Spacer(Modifier.height(18.dp))
        Text(
            text = Fmt.duration(activeSeconds),
            color = Palette.Text,
            fontSize = if (isLargeText()) 24.sp else 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(2.dp))
        SmallLabel("生成耗时合计")
        Spacer(Modifier.height(18.dp))
        StatPair("Sessions", sessions.toString())
        Spacer(Modifier.height(6.dp))
        StatPair("设备", "$hosts 台")
        if (sessions == 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "服务端无 sessions",
                color = Palette.Faint,
                fontSize = 10.sp,
                maxLines = 1,
            )
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
    SoftCard(contentPadding = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(title)
            Spacer(Modifier.weight(1f))
            if (hint != null) SmallLabel(hint)
        }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text(
                text = "这个区间没有数据",
                color = Palette.Dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            rows.forEach { row ->
                RankingRow(row = row, maxCost = maxCost, showSessions = showSessions)
            }
        }
        if (footnote != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = footnote, color = Palette.Faint, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun ErrorCard(message: String, staleAt: Long?) {
    SoftCard(contentPadding = 14) {
        Text(text = message, color = Palette.Warn, fontSize = 12.sp)
        if (staleAt != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "显示上次缓存 · ${Fmt.clock(staleAt)}",
                color = Palette.Dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
internal fun EmptyCard(loading: Boolean) {
    SoftCard(contentPadding = 20) {
        Text(
            text = if (loading) "正在拉取…" else "没有数据",
            color = Palette.TextSoft,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "选中区间没有上传记录，或账号还没同步过。桌面端 / CLI 同步后这里会出现数据。",
            color = Palette.Dim,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun Footer(fetchedAt: Long?, stale: Boolean, loading: Boolean, onRefresh: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (stale) Palette.Dim else Palette.Ok),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "更新于 ${Fmt.clock(fetchedAt)} · ${Fmt.ago(fetchedAt)}",
            color = Palette.Dim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (loading) "刷新中…" else "刷新",
            color = if (loading) Palette.Dim else Palette.Muted,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !loading, onClick = onRefresh)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
