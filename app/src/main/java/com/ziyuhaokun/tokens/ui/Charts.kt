package com.ziyuhaokun.tokens.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 平滑面积曲线：三次贝塞尔穿过相邻点，下方从强调色渐隐到透明，
 * 末点一个实心圆，底部一条虚线基线 —— 和桌面端「今天」那张图一致。
 */
@Composable
fun AreaChart(
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    description: String? = null,
) {
    val colors = LocalTokensColors.current
    val motion = LocalMotionEnabled.current
    // 入场：从基线长起来。关掉动态效果时直接到 1。
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(values, motion) {
        if (!motion) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
    }

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
                ),
        ) {
            drawUsageArea(values, colors.accent, colors.hairline, reveal.value)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(startLabel, color = Palette.Dim, fontSize = TokensType.Caption, fontFamily = NumericFont)
            Spacer(Modifier.weight(1f))
            Text(endLabel, color = Palette.Text, fontSize = TokensType.Caption, fontWeight = FontWeight.Medium)
        }
    }
}

/** 首页和桌面 4×4 小组件共用的面积图。 */
internal fun DrawScope.drawUsageArea(
    values: List<Double>,
    color: Color,
    baselineColor: Color,
    reveal: Float = 1f,
) {
    if (values.isEmpty()) return
    val dotRadius = 4.dp.toPx()
    val baseline = size.height - 1.dp.toPx()
    val top = dotRadius + 2.dp.toPx()
    val max = values.maxOf { if (it.isFinite()) it else 0.0 }.takeIf { it > 0.0 } ?: 1.0
    val right = size.width - dotRadius
    val step = right / values.lastIndex.coerceAtLeast(1)
    val points = values.mapIndexed { index, value ->
        val ratio = if (value.isFinite()) (value / max).toFloat().coerceIn(0f, 1f) else 0f
        val x = if (values.size == 1) right else index * step
        Offset(x, baseline - ratio * (baseline - top) * reveal)
    }
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val midX = (previous.x + current.x) / 2f
            cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
        }
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(points.last().x, baseline)
        lineTo(points.first().x, baseline)
        close()
    }
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.03f)),
            startY = top,
            endY = baseline,
        ),
    )
    drawLine(
        color = baselineColor,
        start = Offset(0f, baseline),
        end = Offset(size.width, baseline),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
    )
    drawPath(
        path = line,
        color = color,
        style = Stroke(
            width = (if (values.size > 60) 1.5.dp else 2.5.dp).toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawCircle(color, dotRadius, points.last())
}

/**
 * 刻度表盘：外圈一圈细刻度（按进度点亮）+ 内圈一条粗进度弧，中间大号数字。
 * 桌面端用来表示配额剩余，这里表示「缓存读占 tokens 的比例」。
 */
@Composable
fun DialRing(
    progress: Float,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    unit: String = "%",
    size: Dp = 132.dp,
    description: String? = null,
) {
    val colors = LocalTokensColors.current
    val motion = LocalMotionEnabled.current
    // 不要按开关条件调用 Composable：开关变化时槽位会错位。
    // 统一调用，只换 animationSpec（精简 = 立刻到位）。
    val animated = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (motion) tween(620) else snap(),
        label = "dial",
    )
    val scale = size.value / 132f
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description ?: "$caption $value$unit" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val ticks = 72
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outer = this.size.minDimension / 2f
            val tickLength = 7.dp.toPx() * scale
            val inner = outer - tickLength
            val arcWidth = (6.dp.toPx() * scale).coerceAtLeast(3.dp.toPx())
            val arcRadius = inner - 6.dp.toPx() * scale - arcWidth / 2f
            val p = animated.value
            val active = (p * ticks).toInt()

            // 外圈刻度：已到的亮、没到的暗。
            for (i in 0 until ticks) {
                val angle = Math.toRadians((i * 360.0 / ticks) - 90.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val lit = i < active
                drawLine(
                    color = if (lit) colors.textSoft else colors.faint,
                    start = Offset(center.x + cosA * inner, center.y + sinA * inner),
                    end = Offset(center.x + cosA * outer, center.y + sinA * outer),
                    strokeWidth = (if (lit) 1.8.dp.toPx() else 1.4.dp.toPx()) * scale.coerceAtLeast(0.8f),
                    cap = StrokeCap.Round,
                )
            }

            // 内圈：底轨 + 进度弧（从正上方顺时针）。
            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            drawArc(
                color = colors.hairline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = arcWidth),
            )
            if (p > 0f) {
                drawArc(
                    color = colors.accent.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = arcWidth, cap = StrokeCap.Round),
                )
            }
        }
        // 数字固定在圆环正中心，不跟下面的小字一起「整块居中」（整块居中会让数字比圆心高一截）。
        // lineHeight = 字号 + Trim.Both：行框贴着字形，去掉上下的字体留白，视觉中心才是真中心。
        val valueSp = (34 * scale).coerceAtLeast(22f)
        val tightLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        val density = LocalDensity.current
        // 小字紧贴数字下方：数字字形半高约 0.355em，再加 2dp 间隙和小字自己的半高。
        val captionOffset = with(density) {
            valueSp.sp.toDp() * 0.355f + 2.dp + TokensType.Micro.toDp() / 2
        }
        // 左边放一个透明的单位占位，和右边真单位等宽：数字本身才会水平居中，而不是「数字+%」整体居中。
        Row(modifier = Modifier.clearAndSetSemantics { }) {
            Text(
                text = unit,
                color = Color.Transparent,
                fontSize = TokensType.Caption,
                style = TextStyle(lineHeight = TokensType.Caption, lineHeightStyle = tightLine),
                modifier = Modifier.alignByBaseline().padding(end = 1.dp),
            )
            Text(
                text = value,
                color = Palette.Text,
                fontSize = valueSp.sp,
                fontWeight = FontWeight.Light,
                style = TextStyle(lineHeight = valueSp.sp, lineHeightStyle = tightLine),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = unit,
                color = Palette.Muted,
                fontSize = TokensType.Caption,
                style = TextStyle(lineHeight = TokensType.Caption, lineHeightStyle = tightLine),
                modifier = Modifier.alignByBaseline().padding(start = 1.dp),
            )
        }
        Text(
            text = caption,
            color = Palette.Dim,
            fontSize = TokensType.Micro,
            style = TextStyle(lineHeight = TokensType.Micro, lineHeightStyle = tightLine),
            modifier = Modifier.offset(y = captionOffset).clearAndSetSemantics { },
        )
    }
}
