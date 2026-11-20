package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `thresholds keep cents instead of truncating to whole dollars`() {
        // 全是小额：取整会把这一串压成 1 块钱的几档，颜色全糊在一起。
        val costs = listOf(0.05, 0.3, 0.45, 0.9, 1.9, 2.4, 3.1, 4.6, 6.0, 9.5)
        val data = yearData(costs = costs.mapIndexed { i, cost -> end.minusDays(i * 3L) to cost }.toMap())

        val cuts = thresholds(data, YearMetric.COST)

        assertEquals(3, cuts.size)
        assertArrayEquals(doubleArrayOf(0.9, 3.1, 6.0), cuts, 0.0)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 3, 3, 4), costs.map { levelOf(day(cost = it), YearMetric.COST, cuts) })
    }

    @Test
    fun `small and mid dollar days land in different levels`() {
        val data = yearData(
            costs = mapOf(
                end.minusDays(6) to 0.05,
                end.minusDays(5) to 0.3,
                end.minusDays(4) to 0.45,
                end.minusDays(3) to 0.9,
                end.minusDays(2) to 1.9,
                end.minusDays(1) to 6.0,
                end to 9.5,
            ),
        )

        val cuts = thresholds(data, YearMetric.COST)

        // 这就是修复前的 bug：$0.3 和 $1.9 都 .toInt() 成 0/1，挤在同一档。
        assertNotEquals(
            levelOf(day(cost = 0.3), YearMetric.COST, cuts),
            levelOf(day(cost = 1.9), YearMetric.COST, cuts),
        )
        assertEquals(1, levelOf(day(cost = 0.3), YearMetric.COST, cuts))
        assertEquals(4, levelOf(day(cost = 9.5), YearMetric.COST, cuts))
    }

    @Test
    fun `thresholds are non decreasing`() {
        val data = yearData(costs = mapOf(end to 0.42, end.minusDays(1) to 3.7, end.minusDays(2) to 12.4))
        val cuts = thresholds(data, YearMetric.COST)

        assertTrue(cuts[0] <= cuts[1])
        assertTrue(cuts[1] <= cuts[2])
    }

    @Test
    fun `thresholds with no usage are all zero and every day stays empty`() {
        val data = yearData()
        val cuts = thresholds(data, YearMetric.COST)

        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), cuts, 0.0)
        assertEquals(0, levelOf(null, YearMetric.COST, cuts))
        // hasUsage 为假的日子画空格，不进色阶。
        assertEquals(0, levelOf(day(), YearMetric.COST, cuts))
    }

    @Test
    fun `single non zero day gives flat thresholds`() {
        val data = yearData(costs = mapOf(end to 0.3))
        val cuts = thresholds(data, YearMetric.COST)

        assertArrayEquals(doubleArrayOf(0.3, 0.3, 0.3), cuts, 0.0)
        assertEquals(1, levelOf(day(cost = 0.3), YearMetric.COST, cuts))
        assertEquals(4, levelOf(day(cost = 0.31), YearMetric.COST, cuts))
    }

    @Test
    fun `tokens metric thresholds use token counts`() {
        val tokens = listOf(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L)
        val data = yearData(
            // cost 全为 0，证明阈值真的取的是 tokens 而不是费用。
            tokens = tokens.mapIndexed { i, value -> end.minusDays(i.toLong()) to value }.toMap(),
        )

        val cuts = thresholds(data, YearMetric.TOKENS)

        assertArrayEquals(doubleArrayOf(400.0, 700.0, 900.0), cuts, 0.0)
        assertEquals(1, levelOf(day(tokens = 400L), YearMetric.TOKENS, cuts))
        assertEquals(3, levelOf(day(tokens = 900L), YearMetric.TOKENS, cuts))
        assertEquals(4, levelOf(day(tokens = 1000L), YearMetric.TOKENS, cuts))
    }

    private fun yearData(
        costs: Map<LocalDate, Double> = emptyMap(),
        tokens: Map<LocalDate, Long> = emptyMap(),
    ): YearData {
        val start = end.minusDays(364)
        val days = (0 until 365).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayUsage(date, costs[date] ?: 0.0, tokens[date] ?: 0L, 0L)
        }
        return YearData(
            start = start,
            end = end,
            days = days,
            totalCost = days.sumOf { it.cost },
            totalTokens = days.sumOf { it.tokens },
            activeDays = days.count { it.hasUsage },
            longestStreak = 0,
            bestDay = null,
        )
    }

    private fun day(cost: Double = 0.0, tokens: Long = 0L): DayUsage = DayUsage(end, cost, tokens, 0L)
}
