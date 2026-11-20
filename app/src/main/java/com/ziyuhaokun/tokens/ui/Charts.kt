package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 平滑面积曲线：三次贝塞尔穿过相邻点中点，末点一个实心圆，
 * 底部一条虚线基线 —— 和桌面端「今天」那张图一致。
 */
@Composable
fun AreaChart(
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (values.size < 2) return@Canvas
            val baseline = size.height - 1.dp.toPx()
            val max = values.max().takeIf { it > 0.0 } ?: 1.0
            val step = size.width / (values.size - 1)
            val points = values.mapIndexed { index, value ->
                val ratio = (value / max).toFloat().coerceIn(0f, 1f)
                Offset(index * step, baseline - ratio * baseline * 0.9f)
            }

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val previous = points[i - 1]
                    val current = points[i]
                    val midX = (previous.x + current.x) / 2f
                    cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
                }
            }

            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, baseline)
                lineTo(0f, baseline)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(Palette.TextSoft.copy(alpha = 0.26f), Color.Transparent),
                    startY = 0f,
                    endY = baseline,
                ),
            )
            drawPath(
                path = line,
                color = Palette.TextSoft,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawLine(
                color = Palette.Hairline,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f)),
            )
            drawCircle(
                color = Palette.Text,
                radius = 3.5.dp.toPx(),
                center = points.last(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(startLabel, color = Palette.Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(endLabel, color = Palette.Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/**
 * 刻度环：56 根刻度，按进度点亮；中间放百分比。
 * 桌面端用来表示配额，这里表示「缓存读占 tokens 的比例」。
 */
@Composable
fun DialRing(
    progress: Float,
    centerText: String,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val ticks = 64
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f
            val inner = outer - 7.dp.toPx()
            val active = (progress.coerceIn(0f, 1f) * ticks).toInt()
            for (i in 0 until ticks) {
                // 从正上方开始顺时针
                val angle = Math.toRadians((i * 360.0 / ticks) - 90.0)
                val start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner,
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer,
                )
                drawLine(
                    color = if (i < active) Palette.TextSoft else Palette.Faint,
                    start = start,
                    end = end,
                    strokeWidth = if (i < active) 2.dp.toPx() else 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                color = Palette.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
            )
            Text(text = caption, color = Palette.Dim, fontSize = 9.sp)
        }
    }
}
