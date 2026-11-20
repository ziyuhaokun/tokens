package com.ziyuhaokun.tokens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.ReceiptData
import com.ziyuhaokun.tokens.data.ReceiptLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/*
 * 小票永远是「一张纸」：纸色、墨色写死在这里，不跟 App 的深色/浅色主题走。
 * 分享出去的图片里也只有这张纸，所以不能引用主题色。
 */
private val ReceiptPaper = Color(0xFFF7F5EF)
private val ReceiptInk = Color(0xFF1A1A1A)
private val ReceiptInkSoft = Color(0xFF6B6B6B)
private val ReceiptInkFaint = Color(0x471A1A1A)

private val ReceiptWidth = 300.dp
private val ReceiptTooth = 6.dp
private val ReceiptToothPitch = 12.dp

/** 条码的横向单位总数：条纹按 1~3 个单位宽铺满整行。 */
private const val BarcodeUnits = 96

/**
 * 用量小票：热敏纸样式的一张固定宽度卡片。
 *
 * 只负责画，不负责保存/分享/动画 —— 截图由 [ReceiptSheet] 用 graphicsLayer 抓，
 * 所以这里的每个像素都必须来自 [data] 和 [formats]，不能引用主题色或随机数
 * （同一次打印必须印出同一张图）。
 */
@Composable
fun ReceiptCard(data: ReceiptData, formats: NumberFormats, modifier: Modifier = Modifier) {
    val shape = remember { TornPaperShape(ReceiptTooth, ReceiptToothPitch) }
    val printed = remember(data.printedAt) { printedStamp(data.printedAt) }
    val bars = remember(data.number) { barcodeBars(data.number) }

    Column(
        modifier = modifier
            .width(ReceiptWidth)
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .background(ReceiptPaper, shape)
            .padding(horizontal = 18.dp, vertical = ReceiptTooth + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TOKENS",
            color = ReceiptInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ReceiptFont,
            letterSpacing = 5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "AI 用量小票",
            color = ReceiptInkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "— ${data.title} —",
            color = ReceiptInk,
            fontSize = 12.sp,
            fontFamily = ReceiptFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        DashedRule()
        Spacer(Modifier.height(8.dp))
        ReceiptRow("DATE", printed)
        ReceiptRow("NO.", data.number)
        Spacer(Modifier.height(8.dp))

        DashedRule()
        Spacer(Modifier.height(8.dp))
        ReceiptRow("输入", formats.tokens(data.input))
        ReceiptRow("输出", formats.tokens(data.output))
        ReceiptRow("缓存读取", formats.tokens(data.cacheRead))
        ReceiptRow("缓存写入", formats.tokens(data.cacheWrite))
        ReceiptRow("推理", formats.tokens(data.reasoning))
        Spacer(Modifier.height(8.dp))

        DashedRule()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "TOTAL TOKEN",
            color = ReceiptInkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formats.tokens(data.totalTokens),
            color = ReceiptInk,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ReceiptFont,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ReceiptRow("COST", formats.money(data.costUsd), bold = true, valueSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        // 没有工具行时不留空标题 + 空分隔线。
        if (data.tools.isNotEmpty()) {
            DashedRule()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "TOOLS",
                color = ReceiptInkSoft,
                fontSize = 11.sp,
                fontFamily = ReceiptFont,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            data.tools.forEach { tool -> ReceiptRow(tool.name, formats.tokens(tool.tokens)) }
            Spacer(Modifier.height(8.dp))
            DashedRule()
            Spacer(Modifier.height(8.dp))
        }

        ReceiptRow("活跃时长", Fmt.duration(data.activeSeconds))
        ReceiptRow("会话数", data.sessions.toString())
        ReceiptRow("消息数", data.messages.toString())

        if (data.stale) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "数据来自缓存 · 更新于 ${Fmt.clock(data.dataFetchedAt)}",
                color = ReceiptInkSoft,
                fontSize = 11.sp,
                fontFamily = ReceiptFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Barcode(bars, Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = data.number,
            color = ReceiptInkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "THANK YOU",
            color = ReceiptInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ReceiptFont,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "vibecafe.ai",
            color = ReceiptInkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 左标签、右数值，中间一串点。点只画在中间那段空白里，不会压到文字上。 */
@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueSize: TextUnit = 12.sp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = ReceiptInkSoft,
            fontSize = 12.sp,
            fontFamily = ReceiptFont,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 7.dp)
                .height(6.dp)
                .drawBehind {
                    val radius = 0.6.dp.toPx()
                    val step = 3.4.dp.toPx()
                    val y = size.height / 2f
                    var x = radius
                    while (x + radius <= size.width) {
                        drawCircle(color = ReceiptInkFaint, radius = radius, center = Offset(x, y))
                        x += step
                    }
                },
        )
        Text(
            text = value,
            color = ReceiptInk,
            fontSize = valueSize,
            fontFamily = ReceiptFont,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 热敏纸上的虚线分隔。 */
@Composable
private fun DashedRule(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        drawLine(
            color = ReceiptInkFaint,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
        )
    }
}

/** 装饰用条码：只由单号决定，同样的单号永远印出同样的条纹。 */
@Composable
private fun Barcode(bars: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = bars.sumOf { abs(it) }
        if (total <= 0) return@Canvas
        val unitWidth = size.width / total
        var x = 0f
        bars.forEach { bar ->
            val barWidth = abs(bar) * unitWidth
            if (bar > 0) {
                drawRect(color = ReceiptInk, topLeft = Offset(x, 0f), size = Size(barWidth, size.height))
            }
            x += barWidth
        }
    }
}

/**
 * 从单号生成条纹序列：正数是条纹宽度（1~3 个单位），负数是 1~2 个单位的间隙。
 * 用固定种子的线性同余，保证跨进程、跨设备都是同一张图。
 */
private fun barcodeBars(seed: String): List<Int> {
    var state = seed.fold(0x2F6E2B1) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    if (state == 0) state = 1
    fun next(): Int {
        state = (state * 1664525 + 1013904223) and 0x7FFFFFFF
        return state ushr 8
    }

    val bars = ArrayList<Int>()
    var units = 0
    while (units < BarcodeUnits) {
        val bar = 1 + next() % 3
        bars += bar
        units += bar
        val gap = 1 + next() % 2
        bars += -gap
        units += gap
    }
    return bars
}

private val StampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

private fun printedStamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(StampFormat)

/**
 * 纸的轮廓：上下各一排三角齿（6dp），左右两侧直边。
 * 齿宽按卡片宽度均分，最后一个齿正好落在右边缘上。
 */
private class TornPaperShape(private val tooth: Dp, private val pitch: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tooth.toPx() }.coerceAtMost(size.height / 4f)
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
        return Outline.Generic(path)
    }
}

/** 预览样例（7 天口径，各项数字自洽）。 */
private val SampleReceipt = ReceiptData(
    range = Range.D7,
    title = "LAST 7 DAYS",
    printedAt = 1_795_000_000_000,
    number = "TK-261120-1236",
    input = 12_400_000,
    output = 3_900_000,
    cacheRead = 640_000_000,
    cacheWrite = 41_200_000,
    reasoning = 880_000,
    totalTokens = 698_380_000,
    costUsd = 2460.18,
    tools = listOf(
        ReceiptLine(name = "claude-code", tokens = 312_000_000, costUsd = 1180.44),
        ReceiptLine(name = "codex", tokens = 201_000_000, costUsd = 812.30),
        ReceiptLine(name = "cursor", tokens = 96_400_000, costUsd = 320.55),
        ReceiptLine(name = "kimi", tokens = 41_000_000, costUsd = 96.20),
    ),
    activeSeconds = 11_520,
    sessions = 42,
    messages = 1834,
    dataFetchedAt = 1_795_000_000_000,
    stale = true,
)

@Preview(showBackground = true, backgroundColor = 0xFF101214, widthDp = 340, heightDp = 1000)
@Composable
private fun ReceiptCardPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(data = SampleReceipt, formats = NumberFormats.DEFAULT)
    }
}
