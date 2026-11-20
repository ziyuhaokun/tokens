package com.ziyuhaokun.tokens.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 小票上的一行排行（按工具）。金额是美元，显示时由 NumberFormats 换算。 */
data class ReceiptLine(val name: String, val tokens: Long, val costUsd: Double)

/**
 * 一张用量小票的全部内容，只放原始数字，不放格式化好的字符串：
 * 货币 / 单位由界面按设置决定。
 *
 * 隐私：分享出去的东西，不放设备名、项目名、账号。
 */
data class ReceiptData(
    val range: Range,
    /** 英文副标题，例如 "LAST 7 DAYS"、"TODAY"。 */
    val title: String,
    /** 打印时间（毫秒）。 */
    val printedAt: Long,
    /** 单号，例如 "TK-261120-1236"，由打印时间生成。 */
    val number: String,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    /** 缓存写 = cacheCreation5m + cacheCreation1h。 */
    val cacheWrite: Long,
    val reasoning: Long,
    /** = input + output + cacheRead + cacheWrite + reasoning，与首页口径一致。 */
    val totalTokens: Long,
    val costUsd: Double,
    /** 按工具，费用降序，最多 6 行。 */
    val tools: List<ReceiptLine>,
    val activeSeconds: Long,
    val sessions: Int,
    val messages: Long,
    /** 数据本身的获取时间；来自缓存时 stale = true，小票上要注明。 */
    val dataFetchedAt: Long?,
    val stale: Boolean,
)

object ReceiptBuilder {
    /** 单号用的时间戳：yyMMdd-HHmm，按传入时区取。 */
    private val NUMBER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd-HHmm")

    /** 小票上最多列几个工具。 */
    private const val MAX_TOOL_LINES = 6

    fun build(
        usage: Usage,
        range: Range,
        fetchedAt: Long?,
        stale: Boolean,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReceiptData {
        val input = usage.buckets.sumOf { it.inputTokens }
        val output = usage.buckets.sumOf { it.outputTokens }
        val cacheRead = usage.buckets.sumOf { it.cachedInputTokens }
        val cacheWrite = usage.buckets.sumOf { it.cacheCreation5m + it.cacheCreation1h }
        val reasoning = usage.buckets.sumOf { it.reasoningOutputTokens }

        return ReceiptData(
            range = range,
            title = titleOf(range),
            printedAt = now,
            number = "TK-" + Instant.ofEpochMilli(now).atZone(zone).format(NUMBER_FORMAT),
            input = input,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoning = reasoning,
            totalTokens = usage.buckets.sumOf { it.tokens },
            costUsd = usage.buckets.sumOf { it.estimatedCost },
            tools = UsageAgg.bySource(usage)
                .take(MAX_TOOL_LINES)
                .map { ReceiptLine(name = it.name, tokens = it.tokens, costUsd = it.cost) },
            activeSeconds = usage.sessions.sumOf { it.activeSeconds },
            sessions = usage.sessions.size,
            messages = usage.sessions.sumOf { it.messageCount },
            dataFetchedAt = fetchedAt,
            stale = stale,
        )
    }

    /** 新增 Range 时这里会编译失败，必须补一条英文副标题。 */
    private fun titleOf(range: Range): String = when (range) {
        Range.TODAY -> "TODAY"
        Range.H24 -> "LAST 24 HOURS"
        Range.D7 -> "LAST 7 DAYS"
        Range.D30 -> "LAST 30 DAYS"
        Range.D90 -> "LAST 90 DAYS"
        Range.D180 -> "LAST 180 DAYS"
        Range.D365 -> "LAST 365 DAYS"
    }
}
