package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class YearAggTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val end: LocalDate = LocalDate.of(2026, 11, 20)

    private fun bucketAt(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
        cost: Double = 0.0,
        input: Long = 0,
        cached: Long = 0,
        source: String = "cli",
    ) = Bucket(
        source = source,
        model = "claude-sonnet",
        project = "tokens",
        hostname = "host-a",
        bucketStartMillis = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli(),
        inputTokens = input,
        outputTokens = 0,
        cachedInputTokens = cached,
        reasoningOutputTokens = 0,
        cacheCreation5m = 0,
        cacheCreation1h = 0,
        estimatedCost = cost,
    )

    private fun bucketAtInstant(instant: Instant, cost: Double = 0.0) = Bucket(
        source = "cli",
        model = "claude-sonnet",
        project = "tokens",
        hostname = "host-a",
        bucketStartMillis = instant.toEpochMilli(),
        inputTokens = 0,
        outputTokens = 0,
        cachedInputTokens = 0,
        reasoningOutputTokens = 0,
        cacheCreation5m = 0,
        cacheCreation1h = 0,
        estimatedCost = cost,
    )

    private fun sessionAt(date: LocalDate, hour: Int, seconds: Long) = Session(
        source = "cli",
        project = "tokens",
        activeSeconds = seconds,
        startMillis = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private fun usage(
        buckets: List<Bucket> = emptyList(),
        sessions: List<Session> = emptyList(),
    ) = Usage("me", buckets, sessions, buckets.isNotEmpty(), UsageNames(), "")

    @Test
    fun `build spans 365 days ending on end date`() {
        val data = YearAgg.build(usage(), end, zone)

        assertEquals(365, data.days.size)
        assertEquals(LocalDate.of(2025, 11, 21), data.start)
        assertEquals(end, data.end)
        assertEquals(data.start, data.days.first().date)
        assertEquals(end, data.days.last().date)
        assertEquals(0, data.activeDays)
        assertEquals(0, data.longestStreak)
        assertNull(data.bestDay)
    }

    @Test
    fun `build counts active days and longest streak`() {
        val buckets = listOf(
            bucketAt(LocalDate.of(2026, 11, 10), 9, cost = 0.4),
            bucketAt(LocalDate.of(2026, 11, 11), 9, cost = 0.2),
            bucketAt(LocalDate.of(2026, 11, 12), 9, cost = 0.3),
            bucketAt(LocalDate.of(2026, 11, 15), 9, cost = 0.1),
        )

        val data = YearAgg.build(usage(buckets), end, zone)

        assertEquals(4, data.activeDays)
        assertEquals(3, data.longestStreak)
        assertEquals(1.0, data.totalCost, 1e-9)
    }

    @Test
    fun `build picks the most expensive day as best day`() {
        val buckets = listOf(
            bucketAt(LocalDate.of(2026, 11, 1), 9, cost = 0.4),
            bucketAt(LocalDate.of(2026, 11, 2), 9, cost = 2.5),
            bucketAt(LocalDate.of(2026, 11, 3), 9, cost = 0.9),
        )

        val data = YearAgg.build(usage(buckets), end, zone)

        assertEquals(LocalDate.of(2026, 11, 2), data.bestDay?.date)
        assertEquals(2.5, data.bestDay?.cost ?: 0.0, 1e-9)
        assertEquals(1.2666, data.averageCostPerActiveDay, 1e-4)
    }

    @Test
    fun `build ignores buckets outside the window`() {
        val buckets = listOf(
            bucketAt(end.minusDays(365), 12, cost = 9.0),
            bucketAt(end.plusDays(1), 12, cost = 8.0),
            bucketAt(end, 12, cost = 0.5),
        )

        val data = YearAgg.build(usage(buckets), end, zone)

        assertEquals(0.5, data.totalCost, 1e-9)
        assertEquals(1, data.activeDays)
        assertEquals(0.5, data.bestDay?.cost ?: 0.0, 1e-9)
    }

    @Test
    fun `build buckets by local day not by UTC day`() {
        // 2026-11-20T15:30Z = 2026-11-20 23:30 +08 → 窗口内最后一天
        // 2026-11-20T16:30Z = 2026-11-21 00:30 +08 → 已经出窗口
        val buckets = listOf(
            bucketAtInstant(Instant.parse("2026-11-20T15:30:00Z"), cost = 0.7),
            bucketAtInstant(Instant.parse("2026-11-20T16:30:00Z"), cost = 4.0),
        )

        val data = YearAgg.build(usage(buckets), end, zone)

        assertEquals(0.7, data.totalCost, 1e-9)
        assertEquals(0.7, data.days.last().cost, 1e-9)
        assertEquals(1, data.activeDays)
    }

    @Test
    fun `build sums tokens including cached input and counts token only days as active`() {
        val buckets = listOf(
            bucketAt(LocalDate.of(2026, 11, 18), 9, input = 100, cached = 400),
            // 只有 tokens、没有费用的一天也算活跃
            bucketAt(LocalDate.of(2026, 11, 19), 9, cost = 0.0, input = 1),
        )

        val data = YearAgg.build(usage(buckets), end, zone)

        assertEquals(501L, data.totalTokens)
        assertEquals(2, data.activeDays)
        assertEquals(2, data.longestStreak)
        assertEquals(250L, data.averageTokensPerActiveDay)
    }

    @Test
    fun `build folds session seconds onto their start day inside the window`() {
        val sessions = listOf(
            sessionAt(LocalDate.of(2026, 11, 19), 10, 600),
            sessionAt(LocalDate.of(2026, 11, 19), 18, 300),
            sessionAt(end.plusDays(2), 10, 9_999),
        )

        val data = YearAgg.build(usage(sessions = sessions), end, zone)

        assertEquals(900L, data.days.single { it.date == LocalDate.of(2026, 11, 19) }.activeSeconds)
        assertEquals(0L, data.days.last().activeSeconds)
        // 会话不算「有用量」，活跃天数只看 bucket
        assertEquals(0, data.activeDays)
    }
}
