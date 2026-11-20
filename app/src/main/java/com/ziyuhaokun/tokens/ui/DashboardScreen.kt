package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import com.ziyuhaokun.tokens.data.UsageAgg

@Composable
fun DashboardScreen(
    state: DashboardState,
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("云端用量", color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = usage?.accountHandle ?: "vibecafe.ai",
                    color = Palette.Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "设置",
                color = Palette.Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        RangeRow(selected = state.range, onSelect = onRange)

        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(
                message = state.error,
                staleAt = if (state.stale) state.fetchedAt else null,
            )
        }

        Spacer(Modifier.height(14.dp))

        if (usage == null) {
            EmptyState(loading = state.loading)
        } else {
            val totals = UsageAgg.totals(usage)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "费用",
                    value = Fmt.money(totals.cost),
                    sub = "${usage.buckets.size} buckets",
                    valueColor = Palette.Cost,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Tokens",
                    value = Fmt.tokens(totals.tokens),
                    sub = "含缓存读/写",
                    valueColor = Palette.Accent,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "缓存读",
                    value = Fmt.tokens(totals.cachedInput),
                    sub = if (totals.tokens > 0) {
                        "${(totals.cachedInput * 100 / totals.tokens.coerceAtLeast(1))}% of tokens"
                    } else "—",
                    valueColor = Palette.Text,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "活跃",
                    value = Fmt.hours(totals.activeSeconds),
                    sub = "${totals.sessions} sessions · ${totals.hosts} 台机器",
                    valueColor = Palette.Ok,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(
                title = "趋势",
                hint = if (state.range.isHourly) "按小时" else "按天",
            ) {
                TrendChart(UsageAgg.trend(usage, state.range))
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(title = "按工具", hint = "费用 · Tokens") {
                RankingList(UsageAgg.bySource(usage).take(8), emptyText = "这个区间没有数据")
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(title = "按模型", hint = "费用 · Tokens") {
                RankingList(UsageAgg.byModel(usage).take(8), emptyText = "这个区间没有数据")
            }

            val projects = UsageAgg.byProject(usage)
            val onlyUnknown = projects.isNotEmpty() && projects.all { it.key == "unknown" }
            Spacer(Modifier.height(14.dp))
            SectionCard(
                title = "按项目",
                hint = if (onlyUnknown) "服务端未收集项目名" else "费用 · Sessions",
            ) {
                RankingList(projects.take(10), emptyText = "这个区间没有数据", withSessions = !onlyUnknown)
                if (onlyUnknown) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "上传设置里 uploadProject 关闭时（默认），所有用量都归到 unknown。",
                        color = Palette.Dim,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "更新于 " + Fmt.clock(state.fetchedAt) + if (state.stale) "（缓存）" else "",
                color = Palette.Dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (state.loading) "刷新中…" else "刷新",
                color = if (state.loading) Palette.Dim else Palette.Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.loading, onClick = onRefresh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ErrorBanner(message: String, staleAt: Long?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Palette.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Palette.Danger.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message, color = Palette.Danger, fontSize = 12.sp)
            if (staleAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "显示上次缓存 · " + Fmt.clock(staleAt),
                    color = Palette.Muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Palette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.Line),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (loading) "正在拉取…" else "没有数据",
                color = Palette.Text,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "选中区间没有上传记录，或账号还没同步过。桌面端/CLI 同步后这里会出现数据。",
                color = Palette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}
