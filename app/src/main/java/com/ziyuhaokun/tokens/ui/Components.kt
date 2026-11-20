package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.UsageAgg

@Composable
fun SectionCard(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Palette.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.Line),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    color = Palette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                if (hint != null) {
                    Text(text = hint, color = Palette.Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StatCard(label: String, value: String, sub: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Palette.SurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Palette.Line),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = label.uppercase(), color = Palette.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = sub, color = Palette.Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
fun RangeRow(selected: Range, onSelect: (Range) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Range.entries.forEach { range ->
            val active = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Palette.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = range.label,
                    color = if (active) Palette.Text else Palette.Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
fun RankingList(rows: List<UsageAgg.Row>, emptyText: String, withSessions: Boolean = false) {
    if (rows.isEmpty()) {
        Text(text = emptyText, color = Palette.Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        return
    }
    val maxCost = rows.maxOf { it.cost }.takeIf { it > 0.0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp, horizontal = 6.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((row.cost / maxCost).toFloat().coerceIn(0.02f, 1f) * 0.72f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Palette.Accent.copy(alpha = if (index == 0) 0.16f else 0.09f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.name,
                            color = Palette.Text,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (withSessions) {
                            Text(
                                text = "${row.sessions} · ${Fmt.hours(row.activeSeconds)}",
                                color = Palette.Dim,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = Fmt.money(row.cost),
                            color = Palette.Text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = Fmt.tokens(row.tokens),
                            color = Palette.Muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
