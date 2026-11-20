package com.ziyuhaokun.tokens.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 平滑面积曲线：三次贝塞尔穿过相邻点，下方从强调色渐隐到透明，
 * 末点一个实心圆，底部一条虚线基线 —— 和桌面端「今天」那张图一致。
 *
 * 动效：
 * - 第一次出现：曲线从左到右「画」出来，末端点最后弹出；
 * - 数据变了（切范围 / 刷新）：各点从旧高度平滑过渡到新高度，不回零重来。
 * 交互（传了 [formatValue] 才有）：
 * - 左上角一条淡的峰值参考线 + 峰值金额；
 * - 按住 / 左右拖动：竖线 + 圆点 + 气泡显示当天的 [pointLabels] 和金额，松手渐隐。
 * 「动态效果」关掉时，全部直接到位。
 */
@Composable
fun AreaChart(
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    description: String? = null,
    pointLabels: List<String> = emptyList(),
    formatValue: ((Double) -> String)? = null,
) {
    val colors = LocalTokensColors.current
    val motion = LocalMotionEnabled.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val interactive = formatValue != null
    val topInset = if (interactive) 22.dp else 0.dp

    // 第一次出现的「画出来」。只跑一次：之后数据变了走下面的 morph，不会重画。
    val drawIn = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (!motion) {
            drawIn.snapTo(1f)
        } else if (drawIn.value < 1f) {
            drawIn.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }

    // 数据变化：from → to 之间用 morph 插值。动画中途又来新数据，就从当前画面接着走。
    val morph = remember { Animatable(1f) }
    var from by remember { mutableStateOf(values) }
    var to by remember { mutableStateOf(values) }
    LaunchedEffect(values, motion) {
        if (!motion) {
            from = values
            to = values
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        if (values == to) return@LaunchedEffect
        from = blendSeries(from, to, morph.value)
        to = values
        morph.snapTo(0f)
        morph.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    // 按住查看：scrubIndex = 当前手指对应的点（-1 = 没按），lastScrub 留给渐隐阶段继续画。
    var scrubIndex by remember { mutableIntStateOf(-1) }
    var lastScrub by remember { mutableIntStateOf(-1) }
    val scrubAlpha = animateFloatAsState(
        targetValue = if (scrubIndex >= 0) 1f else 0f,
        animationSpec = if (!motion) snap() else if (scrubIndex >= 0) tween(120) else tween(320),
        label = "scrub",
    )
    val pointCount = rememberUpdatedState(to.size)

    // 峰值标签只在数据变化时重新排版。
    val dim = colors.dim
    val peakValue = to.maxOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val peakLayout = remember(peakValue, formatValue, dim, textMeasurer) {
        if (peakValue == null || formatValue == null) {
            null
        } else {
            textMeasurer.measure(
                text = "峰值 ${formatValue(peakValue)}",
                style = TextStyle(color = dim, fontSize = TokensType.Micro, fontFamily = NumericFont),
                maxLines = 1,
            )
        }
    }
    val bubbleTextColor = colors.text
    val bubbleFill = colors.bg
    val bubbleBorder = colors.hairline
    val guideColor = colors.textSoft
    val accent = colors.accent
    val hairline = colors.hairline

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
                )
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val dotPx = 4.dp.toPx()
                                fun indexAt(x: Float): Int {
                                    val n = pointCount.value
                                    if (n <= 1) return 0
                                    val right = (size.width - dotPx).coerceAtLeast(1f)
                                    return ((x / right) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                }
                                fun moveTo(x: Float) {
                                    val index = indexAt(x)
                                    if (index != scrubIndex) {
                                        scrubIndex = index
                                        lastScrub = index
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                moveTo(down.position.x)
                                // 横向拖动才接管手势；竖向留给页面滚动 / 下拉刷新。
                                val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                    change.consume()
                                }
                                if (slop != null) {
                                    horizontalDrag(down.id) { change ->
                                        moveTo(change.position.x)
                                        change.consume()
                                    }
                                }
                                scrubIndex = -1
                            }
                        }
                    },
                ),
        ) {
            val shown = blendSeries(from, to, morph.value)
            val topInsetPx = topInset.toPx()
            drawUsageArea(
                values = shown,
                color = accent,
                baselineColor = hairline,
                drawIn = drawIn.value,
                topInset = topInsetPx,
            )
            if (!interactive || shown.isEmpty()) return@Canvas

            val scrub = scrubAlpha.value

            // 峰值参考线：正好穿过最高点；标签在线上方，按住查看时让位给气泡。
            val peakAlpha = ((drawIn.value - 0.6f) / 0.4f).coerceIn(0f, 1f) * (1f - scrub)
            if (peakLayout != null && peakAlpha > 0f) {
                val peakY = topInsetPx + 6.dp.toPx()
                drawLine(
                    color = hairline.copy(alpha = hairline.alpha * peakAlpha),
                    start = Offset(0f, peakY),
                    end = Offset(size.width, peakY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                )
                drawText(
                    textLayoutResult = peakLayout,
                    alpha = peakAlpha,
                    topLeft = Offset(0f, peakY - 5.dp.toPx() - peakLayout.size.height),
                )
            }

            // 按住查看：竖线 + 圆点 + 气泡。
            val index = lastScrub
            if (scrub > 0f && index in shown.indices && index < to.size) {
                val point = usagePoints(shown, topInset = topInsetPx)[index]
                val label = pointLabels.getOrNull(index).orEmpty()
                val money = formatValue!!(to[index])
                val bubbleText = textMeasurer.measure(
                    text = buildAnnotatedString {
                        if (label.isNotEmpty()) {
                            withStyle(SpanStyle(color = dim)) { append(label) }
                            append("  ")
                        }
                        withStyle(SpanStyle(color = bubbleTextColor, fontWeight = FontWeight.Medium)) { append(money) }
                    },
                    style = TextStyle(fontSize = TokensType.Caption, fontFamily = NumericFont),
                    maxLines = 1,
                )
                val padX = 8.dp.toPx()
                val padY = 3.dp.toPx()
                val bubbleW = bubbleText.size.width + padX * 2
                val bubbleH = bubbleText.size.height + padY * 2
                val bubbleLeft = (point.x - bubbleW / 2f).coerceIn(0f, (size.width - bubbleW).coerceAtLeast(0f))
                val radius = CornerRadius(bubbleH / 2f)

                drawLine(
                    color = guideColor.copy(alpha = 0.35f * scrub),
                    start = Offset(point.x, bubbleH),
                    end = Offset(point.x, size.height - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(accent.copy(alpha = 0.22f * scrub), 9.dp.toPx(), point)
                drawCircle(accent.copy(alpha = scrub), 4.5.dp.toPx(), point)
                drawRoundRect(
                    color = bubbleFill.copy(alpha = bubbleFill.alpha * scrub),
                    topLeft = Offset(bubbleLeft, 0f),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = bubbleBorder.copy(alpha = bubbleBorder.alpha * scrub),
                    topLeft = Offset(bubbleLeft, 0f),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    textLayoutResult = bubbleText,
                    alpha = scrub,
                    topLeft = Offset(bubbleLeft + padX, padY),
                )
            }
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

/**
 * 在两组数据之间按 [t]（0 = from，1 = to）逐点插值。
 * 两边点数不同（例如 7D → 30D）时，先把 from 沿横轴重采样到 to 的点数，再插值；
 * from 为空就当作一条贴着基线的平线。
 */
internal fun blendSeries(from: List<Double>, to: List<Double>, t: Float): List<Double> {
    if (t >= 1f || to.isEmpty()) return to
    val start = when {
        from.isEmpty() -> List(to.size) { 0.0 }
        from.size == to.size -> from
        from.size == 1 -> List(to.size) { from[0] }
        else -> List(to.size) { i ->
            val pos = if (to.size == 1) 0.0 else i.toDouble() / (to.size - 1) * (from.size - 1)
            val lo = pos.toInt().coerceIn(0, from.lastIndex)
            val hi = (lo + 1).coerceAtMost(from.lastIndex)
            from[lo] + (from[hi] - from[lo]) * (pos - lo)
        }
    }
    return List(to.size) { i ->
        val a = start[i].takeIf { it.isFinite() } ?: 0.0
        val b = to[i].takeIf { it.isFinite() } ?: 0.0
        a + (b - a) * t
    }
}

/** 每个数据点在画布上的位置。首页图表、峰值线、按住查看的圆点和小组件共用，保证对得上。 */
internal fun DrawScope.usagePoints(
    values: List<Double>,
    reveal: Float = 1f,
    topInset: Float = 0f,
): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val dotRadius = 4.dp.toPx()
    val baseline = size.height - 1.dp.toPx()
    val top = topInset + dotRadius + 2.dp.toPx()
    val max = values.maxOf { if (it.isFinite()) it else 0.0 }.takeIf { it > 0.0 } ?: 1.0
    val right = size.width - dotRadius
    val step = right / values.lastIndex.coerceAtLeast(1)
    return values.mapIndexed { index, value ->
        val ratio = if (value.isFinite()) (value / max).toFloat().coerceIn(0f, 1f) else 0f
        val x = if (values.size == 1) right else index * step
        Offset(x, baseline - ratio * (baseline - top) * reveal)
    }
}

/**
 * 首页和桌面 4×4 小组件共用的面积图。
 * [drawIn]：0→1 从左到右画出来（曲线 + 渐变被竖直边裁开，末端点最后弹出）；
 * [topInset]：顶部留出的高度（首页给峰值标签 / 气泡用），小组件不传 = 0，样子不变。
 */
internal fun DrawScope.drawUsageArea(
    values: List<Double>,
    color: Color,
    baselineColor: Color,
    reveal: Float = 1f,
    drawIn: Float = 1f,
    topInset: Float = 0f,
) {
    if (values.isEmpty()) return
    val dotRadius = 4.dp.toPx()
    val baseline = size.height - 1.dp.toPx()
    val top = topInset + dotRadius + 2.dp.toPx()
    val points = usagePoints(values, reveal, topInset)
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
    fun clipped(block: DrawScope.() -> Unit) {
        if (drawIn >= 1f) block() else clipRect(right = size.width * drawIn.coerceAtLeast(0f)) { block() }
    }
    clipped {
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.03f)),
                startY = top,
                endY = baseline,
            ),
        )
    }
    drawLine(
        color = baselineColor,
        start = Offset(0f, baseline),
        end = Offset(size.width, baseline),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
    )
    clipped {
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = (if (values.size > 60) 1.5.dp else 2.5.dp).toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
    // 末端点：线画到 85% 之后才从小变大弹出。
    val dotScale = ((drawIn - 0.85f) / 0.15f).coerceIn(0f, 1f)
    if (dotScale > 0f) drawCircle(color, dotRadius * dotScale, points.last())
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
