package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReceiptBuilderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2026-11-20 12:36 上海时间。 */
    private val now: Long = Instant.parse("2026-11-20T04:36:00Z").toEpochMilli()

    private fun bucket(
        source: String = "cli",
        cost: Double = 0.0,
        input: Long = 0,
        output: Long = 0,
        cached: Long = 0,
        reasoning: Long = 0,
        cache5m: Long = 0,
        cache1h: Long = 0,
        model: String = "claude-sonnet",
    ) = Bucket(
        source = source,
        model = model,
        project = "tokens",
        hostname = "LAPTOP-ABC",
        bucketStartMillis = now,
        inputTokens = input,
        outputTokens = output,
        cachedInputTokens = cached,
        reasoningOutputTokens = reasoning,
        cacheCreation5m = cache5m,
        cacheCreation1h = cache1h,
        estimatedCost = cost,
    )

    private fun usage(
        buckets: List<Bucket> = emptyList(),
        sessions: List<Session> = emptyList(),
        names: UsageNames = UsageNames(),
    ) = Usage(
        accountHandle = "me",
        buckets = buckets,
        sessions = sessions,
        hasAnyData = buckets.isNotEmpty(),
        names = names,
        rawJson = "",
    )

    private fun build(
        usage: Usage,
        range: Range = Range.D7,
        fetchedAt: Long? = 1_700_000_000_000L,
        stale: Boolean = false,
    ) = ReceiptBuilder.build(usage, range, fetchedAt, stale, now, zone)

    @Test
    fun `build sums the token breakdown over every bucket`() {
        val receipt = build(
            usage(
                listOf(
                    bucket(cost = 0.5, input = 100, output = 20, cached = 30, reasoning = 5, cache5m = 2, cache1h = 1),
                    bucket(source = "web", cost = 0.25, input = 10, output = 1, cached = 3, reasoning = 7, cache5m = 4, cache1h = 2),
                ),
            ),
        )

        assertEquals(110L, receipt.input)
        assertEquals(21L, receipt.output)
        assertEquals(33L, receipt.cacheRead)
        // 缓存写 = cacheCreation5m + cacheCreation1h
        assertEquals(9L, receipt.cacheWrite)
        assertEquals(12L, receipt.reasoning)
        assertEquals(185L, receipt.totalTokens)
        assertEquals(
            receipt.input + receipt.output + receipt.cacheRead + receipt.cacheWrite + receipt.reasoning,
            receipt.totalTokens,
        )
        assertEquals(0.75, receipt.costUsd, 1e-9)
    }

    @Test
    fun `build of empty usage is all zeros`() {
        val receipt = build(usage())

        assertEquals(0L, receipt.input)
        assertEquals(0L, receipt.output)
        assertEquals(0L, receipt.cacheRead)
        assertEquals(0L, receipt.cacheWrite)
        assertEquals(0L, receipt.reasoning)
        assertEquals(0L, receipt.totalTokens)
        assertEquals(0.0, receipt.costUsd, 0.0)
        assertTrue(receipt.tools.isEmpty())
        assertEquals(0L, receipt.activeSeconds)
        assertEquals(0, receipt.sessions)
        assertEquals(0L, receipt.messages)
    }

    @Test
    fun `build keeps the six most expensive tools in cost order`() {
        val buckets = (1..8).map { i ->
            // s1 最便宜，s8 最贵
            bucket(source = "s$i", cost = i.toDouble(), input = i * 10L)
        } + bucket(source = "s8", cost = 1.0, input = 1)
        val names = UsageNames(sources = (1..8).associate { "s$it" to "Source $it" })

        val tools = build(usage(buckets, names = names)).tools

        assertEquals(6, tools.size)
        assertEquals(listOf("Source 8", "Source 7", "Source 6", "Source 5", "Source 4", "Source 3"), tools.map { it.name })
        // s8 的两个 bucket 先合并成 9.0 再排行
        assertEquals(listOf(9.0, 7.0, 6.0, 5.0, 4.0, 3.0), tools.map { it.costUsd })
        assertEquals(81L, tools.first().tokens)
        // 第六名之后的行被截掉
        assertTrue(tools.none { it.name == "Source 2" })
    }

    @Test
    fun `build formats the receipt number from the print time in the given zone`() {
        val receipt = build(usage())

        assertEquals(now, receipt.printedAt)
        assertEquals("TK-261120-1236", receipt.number)
        // 同一个瞬间在 UTC 下是 04:36
        assertEquals("TK-261120-0436", ReceiptBuilder.build(usage(), Range.D7, null, false, now, ZoneId.of("UTC")).number)
    }

    @Test
    fun `build titles every range in english`() {
        val expected = mapOf(
            Range.TODAY to "TODAY",
            Range.H24 to "LAST 24 HOURS",
            Range.D7 to "LAST 7 DAYS",
            Range.D30 to "LAST 30 DAYS",
            Range.D90 to "LAST 90 DAYS",
            Range.D180 to "LAST 180 DAYS",
            Range.D365 to "LAST 365 DAYS",
        )

        for (range in Range.entries) {
            val receipt = build(usage(), range = range)
            assertEquals(range, receipt.range)
            assertEquals("range $range", expected.getValue(range), receipt.title)
        }
    }

    @Test
    fun `build passes through fetched time, staleness and session metrics`() {
        val sessions = listOf(
            Session("cli", "tokens", activeSeconds = 600, messageCount = 12),
            Session("web", "tokens", activeSeconds = 300, messageCount = 8),
            Session("cursor", "tokens", activeSeconds = 0),
        )

        val receipt = build(usage(sessions = sessions), fetchedAt = 1_600_000_000_000L, stale = true)

        assertEquals(1_600_000_000_000L, receipt.dataFetchedAt)
        assertTrue(receipt.stale)
        assertEquals(900L, receipt.activeSeconds)
        assertEquals(3, receipt.sessions)
        assertEquals(20L, receipt.messages)
    }

    @Test
    fun `build leaves the fetched time null when there is none`() {
        val receipt = build(usage(), fetchedAt = null)

        assertNull(receipt.dataFetchedAt)
        assertFalse(receipt.stale)
    }
}
