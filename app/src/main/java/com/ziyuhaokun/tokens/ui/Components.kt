package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.UsageAgg

/** 桌面端那种卡片：圆角 24、上亮下暗的轻微渐变、1dp 发丝描边、内边距 18。 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Palette.CardTop, Palette.CardBottom)))
            .border(1.dp, Palette.HairlineSoft, RoundedCornerShape(24.dp))
            .padding(contentPadding.dp),
        content = content,
    )
}

@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Palette.TextSoft,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = Palette.Dim, fontSize = 11.sp, modifier = modifier)
}

/** "$578" 大号细体 + ".22" 小号灰 —— 桌面端金额排版。 */
@Composable
fun BigMoney(integer: String, decimals: String, modifier: Modifier = Modifier, size: Int = 42) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = integer,
            color = Palette.Text,
            fontSize = size.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
        )
        Text(
            text = decimals,
            color = Palette.Muted,
            fontSize = (size * 0.5).sp,
            fontWeight = FontWeight.Light,
        )
    }
}

/** 右侧那种 label / value 两行小统计。 */
@Composable
fun StatPair(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = Palette.Text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Palette.Dim,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 时间范围：纯文字，选中项白色，未选中灰色。 */
@Composable
fun RangeTabs(selected: Range, onSelect: (Range) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Range.entries.forEach { range ->
            val active = range == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(range) }
                    .padding(vertical = 4.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = range.label,
                        color = if (active) Palette.Text else Palette.Dim,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(if (active) 16.dp else 0.dp)
                            .height(1.5.dp)
                            .background(if (active) Palette.Text else Palette.CardTop),
                    )
                }
            }
        }
    }
}

/**
 * 排行行：名字 + 极细进度条（底边一条浅灰，按占比填充），
 * 右列金额 + tokens。没有彩色。
 */
@Composable
fun RankingRow(
    row: UsageAgg.Row,
    maxCost: Double,
    showSessions: Boolean = false,
    divider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                color = Palette.TextSoft,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (showSessions) {
                Text(
                    text = "${row.sessions} · ${Fmt.duration(row.activeSeconds)}",
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
                color = Palette.Dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(58.dp),
            )
        }
        // 只留一条发丝分隔，参考图的列表就是这样干净
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (divider) Palette.HairlineSoft else Palette.CardTop),
        )
    }
}

@Composable
fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.HairlineSoft),
    )
}
