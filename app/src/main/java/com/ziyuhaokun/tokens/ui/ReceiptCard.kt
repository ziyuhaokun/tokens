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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.ReceiptData
import com.ziyuhaokun.tokens.data.ReceiptLanguage
import com.ziyuhaokun.tokens.data.ReceiptLine
import com.ziyuhaokun.tokens.data.ReceiptStyle
import kotlin.math.abs

/** 条码的横向单位总数：条纹按 1~3 个单位宽铺满整行。 */
private const val BarcodeUnits = 96

/**
 * 用量小票：固定宽度的一张纸，纸张样式和文字语言都由调用方给定。
 *
 * 只负责画，不负责保存 / 分享 / 动画 —— 截图由 [ReceiptSheet] 用 graphicsLayer 抓，
 * 所以这里的每个像素都必须来自 [data] / [formats] / [style] / [language]，
 * 不能引用主题色或随机数（同一次打印必须印出同一张图）。
 */
@Composable
fun ReceiptCard(
    data: ReceiptData,
    formats: NumberFormats,
    style: ReceiptStyle,
    language: ReceiptLanguage,
    modifier: Modifier = Modifier,
) {
    val theme = remember(style) { receiptTheme(style) }
    val text = remember(language) { receiptStrings(language) }
    val shape = remember(theme) { ReceiptPaperShape(theme.edge, theme.cornerRadius) }
    val printed = remember(data.printedAt, text.datePattern) {
        receiptStamp(data.printedAt, text.datePattern)
    }
    val bars = remember(data.number) { barcodeBars(data.number) }
    // 锯齿 / 咬边给纸边留出高度，直边就不用。
    val edge = if (theme.edge == ReceiptEdge.STRAIGHT) 0.dp else ReceiptEdgeBand

    Column(
        modifier = modifier
            .width(ReceiptWidth)
            .then(if (theme.shadow) Modifier.shadow(elevation = 10.dp, shape = shape) else Modifier)
            .background(theme.paper, shape)
            .drawBehind { drawScanlines(theme) }
            .padding(horizontal = 18.dp, vertical = edge + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = theme.brand,
            color = theme.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ReceiptFont,
            letterSpacing = theme.brandLetterSpacing,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text.subtitle,
            color = theme.inkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "— ${text.title(data.range)} —",
            color = theme.ink,
            fontSize = 12.sp,
            fontFamily = ReceiptFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        ReceiptRule(theme)
        Spacer(Modifier.height(8.dp))
        ReceiptRow(text.dateLabel, printed, theme)
        ReceiptRow(text.numberLabel, data.number, theme)
        Spacer(Modifier.height(8.dp))

        ReceiptRule(theme)
        Spacer(Modifier.height(8.dp))
        ReceiptRow(text.input, formats.tokens(data.input), theme)
        ReceiptRow(text.output, formats.tokens(data.output), theme)
        ReceiptRow(text.cacheRead, formats.tokens(data.cacheRead), theme)
        ReceiptRow(text.cacheWrite, formats.tokens(data.cacheWrite), theme)
        ReceiptRow(text.reasoning, formats.tokens(data.reasoning), theme)
        Spacer(Modifier.height(8.dp))

        ReceiptRule(theme)
        Spacer(Modifier.height(12.dp))
        Text(
            text = text.total,
            color = theme.inkSoft,
            fontSize = 11.sp,
            fontFamily = ReceiptFont,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formats.tokens(data.totalTokens),
            color = theme.ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ReceiptFont,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ReceiptRow(text.cost, formats.money(data.costUsd), theme, bold = true, valueSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        // 没有工具行时不留空标题 + 空分隔线。
        if (data.tools.isNotEmpty()) {
            ReceiptRule(theme)
            Spacer(Modifier.height(8.dp))
            Text(
                text = text.tools,
                color = theme.inkSoft,
                fontSize = 11.sp,
                fontFamily = ReceiptFont,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            data.tools.forEach { tool -> ReceiptRow(tool.name, formats.tokens(tool.tokens), theme) }
            Spacer(Modifier.height(8.dp))
            ReceiptRule(theme)
            Spacer(Modifier.height(8.dp))
        }

        ReceiptRow(text.active, text.duration(data.activeSeconds), theme)
        ReceiptRow(text.sessions, data.sessions.toString(), theme)
        ReceiptRow(text.messages, data.messages.toString(), theme)

        if (data.stale) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = text.stale(Fmt.clock(data.dataFetchedAt)),
                color = theme.inkSoft,
                fontSize = 11.sp,
                fontFamily = ReceiptFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Barcode(bars, theme, Modifier.fillMaxWidth().height(44.dp))
        if (theme.showBarcodeText) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = data.number,
                color = theme.inkSoft,
                fontSize = 11.sp,
                fontFamily = ReceiptFont,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = text.thankYou,
            color = theme.ink,
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
            color = theme.inkSoft,
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
    theme: ReceiptTheme,
    bold: Boolean = false,
    valueSize: TextUnit = 12.sp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = theme.inkSoft,
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
                        drawCircle(color = theme.inkFaint, radius = radius, center = Offset(x, y))
                        x += step
                    }
                },
        )
        Text(
            text = value,
            color = theme.ink,
            fontSize = valueSize,
            fontFamily = ReceiptFont,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 分隔线：极简样式是实线，其余样式是虚线。 */
@Composable
private fun ReceiptRule(theme: ReceiptTheme, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        drawLine(
            color = theme.rule,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (theme.dashedRules) {
                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
            } else {
                null
            },
        )
    }
}

/** 终端风的极淡扫描线：只在这一种纸上有，透明度 0.04，不能抢正文。 */
private fun DrawScope.drawScanlines(theme: ReceiptTheme) {
    if (theme.scanlineAlpha <= 0f) return
    val radius = theme.cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 4f)
    val paper = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
    clipPath(paper) {
        val step = 3.dp.toPx()
        val line = 1.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = theme.ink.copy(alpha = theme.scanlineAlpha),
                topLeft = Offset(0f, y),
                size = Size(size.width, line),
            )
            y += step
        }
    }
}

/** 装饰用条码：只由单号决定，同样的单号永远印出同样的条纹。 */
@Composable
private fun Barcode(bars: List<Int>, theme: ReceiptTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = bars.sumOf { abs(it) }
        if (total <= 0) return@Canvas
        val unitWidth = size.width / total
        var x = 0f
        bars.forEach { bar ->
            val barWidth = abs(bar) * unitWidth
            if (bar > 0) {
                drawRect(color = theme.ink, topLeft = Offset(x, 0f), size = Size(barWidth, size.height))
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
private fun ReceiptCardClassicPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(
            data = SampleReceipt,
            formats = NumberFormats.DEFAULT,
            style = ReceiptStyle.CLASSIC,
            language = ReceiptLanguage.MIXED,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7, widthDp = 340, heightDp = 1000)
@Composable
private fun ReceiptCardMinimalPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(
            data = SampleReceipt,
            formats = NumberFormats.DEFAULT,
            style = ReceiptStyle.MINIMAL,
            language = ReceiptLanguage.EN,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101214, widthDp = 340, heightDp = 1000)
@Composable
private fun ReceiptCardNightPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(
            data = SampleReceipt,
            formats = NumberFormats.DEFAULT,
            style = ReceiptStyle.NIGHT,
            language = ReceiptLanguage.ZH,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F6F7, widthDp = 340, heightDp = 1000)
@Composable
private fun ReceiptCardKraftPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(
            data = SampleReceipt,
            formats = NumberFormats.DEFAULT,
            style = ReceiptStyle.KRAFT,
            language = ReceiptLanguage.ZH,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101214, widthDp = 340, heightDp = 1000)
@Composable
private fun ReceiptCardTerminalPreview() {
    Box(Modifier.padding(16.dp)) {
        ReceiptCard(
            data = SampleReceipt,
            formats = NumberFormats.DEFAULT,
            style = ReceiptStyle.TERMINAL,
            language = ReceiptLanguage.EN,
        )
    }
}
