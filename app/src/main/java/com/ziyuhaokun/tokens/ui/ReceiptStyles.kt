package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.ReceiptLanguage
import com.ziyuhaokun.tokens.data.ReceiptStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/*
 * 小票的「纸」和「字」都定义在这里：
 * 纸色 / 墨色是文件里的常量，不跟 App 的深色 / 浅色主题走 —— 同一张票在两种模式下、
 * 以及分享出去的 PNG 里必须长得一模一样。
 */

/** 小票的固定宽度。 */
internal val ReceiptWidth = 300.dp

/** 锯齿 / 半圆咬边的上下占用高度。 */
internal val ReceiptEdgeBand = 6.dp

/** 锯齿 / 半圆咬边的横向节距。 */
internal val ReceiptEdgePitch = 12.dp

/** 纸的边：锯齿撕边 / 直边圆角 / 半圆咬边。 */
internal enum class ReceiptEdge { ZIGZAG, STRAIGHT, SCALLOP }

/**
 * 一种纸张样式需要的全部「材质」参数。文本排版不动，只换这里的纸 / 墨 / 边 / 线。
 *
 * [shadow] 关掉时纸是平贴的；[dashedRules] 决定分隔线是虚线还是实线；
 * [showBarcodeText] 关掉后条码下面不再印单号（极简样式嫌它吵）。
 */
internal data class ReceiptTheme(
    val paper: Color,
    val ink: Color,
    val inkSoft: Color,
    val rule: Color,
    val edge: ReceiptEdge,
    val shadow: Boolean,
    val cornerRadius: Dp,
    val brandLetterSpacing: TextUnit,
    /** 顶部印的标识，终端风会带上提示符和下划线。 */
    val brand: String,
    val dashedRules: Boolean,
    val showBarcodeText: Boolean,
    /** 极淡扫描线的透明度，0 表示不画。 */
    val scanlineAlpha: Float,
) {
    /** 行内引导点用的极淡墨色。 */
    val inkFaint: Color get() = ink.copy(alpha = 0.28f)
}

private val ClassicTheme = ReceiptTheme(
    paper = Color(0xFFF7F5EF),
    ink = Color(0xFF1A1A1A),
    inkSoft = Color(0xFF6B6B6B),
    rule = Color(0x471A1A1A),
    edge = ReceiptEdge.ZIGZAG,
    shadow = true,
    cornerRadius = 0.dp,
    brandLetterSpacing = 5.sp,
    brand = "TOKENS",
    dashedRules = true,
    showBarcodeText = true,
    scanlineAlpha = 0f,
)

private val MinimalTheme = ReceiptTheme(
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF111111),
    inkSoft = Color(0xFF8A8A8A),
    rule = Color(0xFFE0E0E0),
    edge = ReceiptEdge.STRAIGHT,
    shadow = true,
    cornerRadius = 16.dp,
    brandLetterSpacing = 4.sp,
    brand = "TOKENS",
    dashedRules = false,
    showBarcodeText = false,
    scanlineAlpha = 0f,
)

private val NightTheme = ReceiptTheme(
    paper = Color(0xFF121316),
    ink = Color(0xFFF2F3F5),
    inkSoft = Color(0xFF8A9099),
    rule = Color(0xFF2A2D33),
    edge = ReceiptEdge.ZIGZAG,
    shadow = true,
    cornerRadius = 0.dp,
    brandLetterSpacing = 5.sp,
    brand = "TOKENS",
    dashedRules = true,
    showBarcodeText = true,
    scanlineAlpha = 0f,
)

private val KraftTheme = ReceiptTheme(
    paper = Color(0xFFD9C3A0),
    ink = Color(0xFF3B2A1A),
    inkSoft = Color(0xFF6E5A44),
    rule = Color(0x593B2A1A),
    edge = ReceiptEdge.SCALLOP,
    shadow = true,
    cornerRadius = 0.dp,
    brandLetterSpacing = 5.sp,
    brand = "TOKENS",
    dashedRules = true,
    showBarcodeText = true,
    scanlineAlpha = 0f,
)

private val TerminalTheme = ReceiptTheme(
    paper = Color(0xFF0B0F0B),
    ink = Color(0xFF7CF29A),
    inkSoft = Color(0xFF4C8F5E),
    rule = Color(0xFF4C8F5E),
    edge = ReceiptEdge.STRAIGHT,
    shadow = true,
    cornerRadius = 4.dp,
    brandLetterSpacing = 2.sp,
    // 终端的那种提示符光标感。
    brand = "> TOKENS_",
    dashedRules = true,
    showBarcodeText = true,
    scanlineAlpha = 0.04f,
)

/** 样式 → 纸张参数。新增样式时这里会编译失败，必须补一套。 */
internal fun receiptTheme(style: ReceiptStyle): ReceiptTheme = when (style) {
    ReceiptStyle.CLASSIC -> ClassicTheme
    ReceiptStyle.MINIMAL -> MinimalTheme
    ReceiptStyle.NIGHT -> NightTheme
    ReceiptStyle.KRAFT -> KraftTheme
    ReceiptStyle.TERMINAL -> TerminalTheme
}

/**
 * 纸的轮廓。三种边共用一套形状：整卡（300dp 宽）和设置页的小样都走它，
 * 保证小样和真票是同一张纸。
 */
internal class ReceiptPaperShape(
    private val edge: ReceiptEdge,
    private val cornerRadius: Dp,
    private val band: Dp = ReceiptEdgeBand,
    private val pitch: Dp = ReceiptEdgePitch,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        when (edge) {
            ReceiptEdge.STRAIGHT -> {
                // 小样上按比例收一收，否则 16dp 圆角会把 40dp 宽的小票做成胶囊。
                val radius = with(density) { cornerRadius.toPx() }
                    .coerceAtMost(min(size.width, size.height) / 4f)
                Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }

            ReceiptEdge.ZIGZAG -> Outline.Generic(zigzagPath(size, density))
            ReceiptEdge.SCALLOP -> Outline.Generic(scallopPath(size, density))
        }

    /** 上下各一排三角齿，左右直边，最后一个齿正好落在右边缘上。 */
    private fun zigzagPath(size: Size, density: Density): Path {
        val t = with(density) { band.toPx() }.coerceAtMost(size.height / 4f)
        val pitchPx = with(density) { pitch.toPx() }.coerceAtLeast(1f)
        val count = ceil(size.width / pitchPx).toInt().coerceAtLeast(1)
        val step = size.width / count

        val path = Path()
        path.moveTo(0f, t)
        for (i in 1..count) {
            path.lineTo(i * step, if (i % 2 == 0) t else 0f)
        }
        path.lineTo(size.width, t)
        path.lineTo(size.width, size.height - t)
        for (i in 1..count) {
            path.lineTo(size.width - i * step, if (i % 2 == 0) size.height - t else size.height)
        }
        path.lineTo(0f, size.height - t)
        path.close()
        return path
    }

    /** 上下边各一排向纸内凹的半圆，看起来像被咬掉一口的牛皮纸。 */
    private fun scallopPath(size: Size, density: Density): Path {
        val depth = with(density) { band.toPx() }.coerceAtMost(size.height / 4f)
        val pitchPx = with(density) { pitch.toPx() }.coerceAtLeast(2f)
        val count = ceil(size.width / pitchPx).toInt().coerceAtLeast(1)
        val step = size.width / count
        val r = (step / 2f).coerceAtMost(depth)

        val path = Path()
        path.moveTo(0f, 0f)
        // 上边：从左往右，每段是圆心落在纸外、向纸内凸的半圆。
        for (i in 0 until count) {
            val cx = i * step + step / 2f
            path.arcTo(Rect(cx - r, -r, cx + r, r), 180f, -180f, false)
        }
        path.lineTo(size.width, size.height)
        // 下边：从右往左，同样的半圆朝纸内凸。
        for (i in count - 1 downTo 0) {
            val cx = i * step + step / 2f
            path.arcTo(Rect(cx - r, size.height - r, cx + r, size.height + r), 0f, -180f, false)
        }
        path.lineTo(0f, 0f)
        path.close()
        return path
    }
}

// ---------------------------------------------------------------------------
// 文字
// ---------------------------------------------------------------------------

/** 时长写法：英文 "3h 12m" / 中文「3小时12分」。 */
internal enum class ReceiptDurationStyle { ENGLISH, CHINESE }

/**
 * 一张小票上的全部文字。语言只换这里的值，卡片布局和顺序永远不变。
 * 注意：小票内容里不放任何会随系统 Locale 变的东西。
 */
internal data class ReceiptStrings(
    val subtitle: String,
    val titles: Map<Range, String>,
    val dateLabel: String,
    val numberLabel: String,
    /** 时间戳格式：中文「2026年11月20日 12:36」，英文 / 混合「2026-11-20 12:36」。 */
    val datePattern: String,
    val input: String,
    val output: String,
    val cacheRead: String,
    val cacheWrite: String,
    val reasoning: String,
    val total: String,
    val cost: String,
    val tools: String,
    val active: String,
    val sessions: String,
    val messages: String,
    /** 缓存提示，%s 替换成更新时刻。 */
    val staleFormat: String,
    val thankYou: String,
    val durationStyle: ReceiptDurationStyle,
) {
    fun title(range: Range): String = titles.getValue(range)

    fun stale(clock: String): String = staleFormat.replace("%s", clock)

    fun duration(seconds: Long): String = when (durationStyle) {
        ReceiptDurationStyle.ENGLISH -> Fmt.duration(seconds)
        ReceiptDurationStyle.CHINESE -> chineseDuration(seconds)
    }
}

/** 中文时长：「29小时15分」「29小时」「45分」「0分」。 */
private fun chineseDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours <= 0 -> "${minutes}分"
        minutes == 0L -> "${hours}小时"
        else -> "${hours}小时${minutes}分"
    }
}

/** 小票标题唯一来源在 Range，这里只映射成 ReceiptStrings 需要的表。 */
private val ChineseTitles: Map<Range, String> = Range.entries.associateWith { it.receiptTitleZh }
private val EnglishTitles: Map<Range, String> = Range.entries.associateWith { it.receiptTitleEn }

private const val ChineseDatePattern = "yyyy年M月d日 HH:mm"
private const val LatinDatePattern = "yyyy-MM-dd HH:mm"

private val ChineseReceipt = ReceiptStrings(
    subtitle = "AI 用量小票",
    titles = ChineseTitles,
    dateLabel = "日期",
    numberLabel = "单号",
    datePattern = ChineseDatePattern,
    input = "输入",
    output = "输出",
    cacheRead = "缓存读取",
    cacheWrite = "缓存写入",
    reasoning = "推理",
    total = "用量合计",
    cost = "费用",
    tools = "按工具",
    active = "活跃时长",
    sessions = "会话数",
    messages = "消息数",
    staleFormat = "数据来自缓存 · 更新于 %s",
    thankYou = "谢谢惠顾",
    durationStyle = ReceiptDurationStyle.CHINESE,
)

private val EnglishReceipt = ReceiptStrings(
    subtitle = "AI USAGE RECEIPT",
    titles = EnglishTitles,
    dateLabel = "DATE",
    numberLabel = "NO.",
    datePattern = LatinDatePattern,
    input = "INPUT",
    output = "OUTPUT",
    cacheRead = "CACHE READ",
    cacheWrite = "CACHE WRITE",
    reasoning = "REASONING",
    total = "TOTAL TOKENS",
    cost = "COST",
    tools = "TOOLS",
    active = "ACTIVE",
    sessions = "SESSIONS",
    messages = "MESSAGES",
    staleFormat = "CACHED DATA · UPDATED %s",
    thankYou = "THANK YOU",
    durationStyle = ReceiptDurationStyle.ENGLISH,
)

/** 混合：栏目标题英文（热敏票的观感），明细行中文（一眼看懂）。 */
private val MixedReceipt = ReceiptStrings(
    subtitle = "AI 用量小票",
    titles = EnglishTitles,
    dateLabel = "DATE",
    numberLabel = "NO.",
    datePattern = LatinDatePattern,
    input = "输入",
    output = "输出",
    cacheRead = "缓存读取",
    cacheWrite = "缓存写入",
    reasoning = "推理",
    total = "TOTAL TOKEN",
    cost = "COST",
    tools = "TOOLS",
    active = "活跃时长",
    sessions = "会话数",
    messages = "消息数",
    staleFormat = "数据来自缓存 · 更新于 %s",
    thankYou = "THANK YOU",
    durationStyle = ReceiptDurationStyle.ENGLISH,
)

/** 语言 → 文字。新增语言时这里会编译失败，必须补一份。 */
internal fun receiptStrings(language: ReceiptLanguage): ReceiptStrings = when (language) {
    ReceiptLanguage.ZH -> ChineseReceipt
    ReceiptLanguage.EN -> EnglishReceipt
    ReceiptLanguage.MIXED -> MixedReceipt
}

/** 小票上的时间戳。「2026年11月20日 12:36」/「2026-11-20 12:36」。 */
internal fun receiptStamp(millis: Long, pattern: String): String = Instant
    .ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(pattern, Locale.US))

// ---------------------------------------------------------------------------
// 设置页用的小样
// ---------------------------------------------------------------------------

// 外框比纸片大一圈：选中描边和纸之间留 ~4dp 空隙，深色纸（夜间黑 / 终端绿）上也看得出选中。
private val SwatchWidth = 52.dp
private val SwatchHeight = 64.dp
private val SwatchPaperWidth = 38.dp
private val SwatchPaperHeight = 50.dp
private val SwatchRingRadius = 10.dp

/**
 * 设置页里的一格纸张小样：44x56 的可点缩略图，纸色 / 边型 / 四根墨线都按样式画。
 *
 * 选中时套一个 2dp 描边。颜色用 [Palette.Text]（深色主题近白、浅色主题近黑），
 * 所以在两种 App 主题下都看得清 —— 这是界面元素，不是纸上的墨。
 */
@Composable
fun ReceiptStyleSwatch(
    style: ReceiptStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = remember(style) { receiptTheme(style) }
    val shape = remember(theme) { ReceiptPaperShape(theme.edge, theme.cornerRadius) }
    val ring = Palette.Text

    Box(
        modifier = modifier
            .width(SwatchWidth)
            .height(SwatchHeight)
            .clip(RoundedCornerShape(SwatchRingRadius))
            // selectable 自带 Role.RadioButton 和选中语义，屏幕阅读器不用额外处理。
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = style.label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.width(SwatchPaperWidth).height(SwatchPaperHeight)) {
            drawReceiptThumb(theme, shape)
        }
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .border(2.dp, ring, RoundedCornerShape(SwatchRingRadius)),
            )
        }
    }
}

/** 缩略图：纸的轮廓 + 条码似的几根墨线，够分辨样式就行。 */
private fun DrawScope.drawReceiptThumb(theme: ReceiptTheme, shape: Shape) {
    drawOutline(shape.createOutline(size, LayoutDirection.Ltr, this), theme.paper)

    val inset = 7.dp.toPx()
    val stroke = 1.4.dp.toPx()
    val left = inset
    val right = size.width - inset
    val width = right - left
    // 第一根当品牌（实墨），后三根当明细（淡墨）。
    val lines = listOf(
        0.28f to 0.55f,
        0.44f to 0.92f,
        0.58f to 0.74f,
        0.72f to 0.92f,
    )
    lines.forEachIndexed { index, (yFraction, widthFraction) ->
        val y = size.height * yFraction
        drawLine(
            color = if (index == 0) theme.ink else theme.inkSoft,
            start = Offset(left, y),
            end = Offset(left + width * widthFraction, y),
            strokeWidth = if (index == 0) stroke * 1.4f else stroke,
            cap = StrokeCap.Round,
        )
    }
}
