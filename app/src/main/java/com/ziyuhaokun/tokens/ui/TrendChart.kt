package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.UsageAgg

private enum class TrendMetric(val label: String) { COST("费用"), TOKENS("Token") }

/** 柱状趋势 + 顶部指标切换。范围决定粒度（小时 / 天），由 UsageAgg.trend 提供定长序列。 */
@Composable
fun TrendChart(points: List<UsageAgg.TrendPoint>, modifier: Modifier = Modifier) {
    var metric by remember { mutableStateOf(TrendMetric.COST) }
    val values = points.map { if (metric == TrendMetric.COST) it.cost else it.tokens.toDouble() }
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    val labelStep = maxOf(1, points.size / 6)
    val barColor = if (metric == TrendMetric.COST) Palette.Cost else Palette.Accent

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segmented(
                options = TrendMetric.entries.map { it.label },
                selectedIndex = metric.ordinal,
                onSelect = { metric = TrendMetric.entries[it] },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            points.forEach { point ->
                val value = if (metric == TrendMetric.COST) point.cost else point.tokens.toDouble()
                val fraction = (value / max).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4 + fraction * 132).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (value > 0) barColor.copy(alpha = 0.85f) else Palette.Line),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            points.forEachIndexed { index, point ->
                Text(
                    text = if (index % labelStep == 0 || index == points.lastIndex) point.label else "",
                    color = Palette.Dim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.SurfaceHigh)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                color = if (selected) Palette.Text else Palette.Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Palette.Line else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clickable { onSelect(index) },
            )
        }
    }
}
