package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageAggTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 20)

    private fun bucket(
        source: String,
        date: LocalDate = today,
        hour: Int = 12,
        cost: Double = 0.0,
        input: Long = 0,
        output: Long = 0,
        cached: Long = 0,
        reasoning: Long = 0,
        cache5m: Long = 0,
        cache1h: Long = 0,
        hostname: String = "host-a",
        model: String = "claude-sonnet",
        project: String = "tokens",
    ) = Bucket(
        source = source,
        model = model,
        project = project,
        hostname = hostname,
        bucketStartMillis = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
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

    @Test
    fun `totals sum buckets and sessions`() {
        val buckets = listOf(
            bucket("cli", cost = 0.5, input = 100, output = 20, cached = 30, reasoning = 5, cache5m = 2, cache1h = 1),
            bucket("web", cost = 0.25, input = 10, output = 1, hostname = "host-b"),
            bucket("cli", cost = 1.25, hostname = ""),
        )
        val sessions = listOf(
            Session("cli", "tokens", activeSeconds = 600),
            Session("web", "tokens", activeSeconds = 300),
        )

        val totals = UsageAgg.totals(usage(buckets, sessions))

        assertEquals(2.0, totals.cost, 1e-9)
        // 158 = 100+20+30+5+2+1，缓存读、缓存写都算进总量
        assertEquals(169L, totals.tokens)
        assertEquals(30L, totals.cachedInput)
        assertEquals(900L, totals.activeSeconds)
        assertEquals(2, totals.sessions)
        // 空 hostname 不算一台机器
        assertEquals(2, totals.hosts)
        assertEquals(2, totals.sources)
    }

    @Test
    fun `totals of empty usage are zero`() {
        val totals = UsageAgg.totals(usage())

        assertEquals(0.0, totals.cost, 0.0)
        assertEquals(0L, totals.tokens)
        assertEquals(0, totals.sessions)
        assertEquals(0, totals.hosts)
        assertEquals(0, totals.sources)
    }

    @Test
    fun `bySource sorts by cost desc and resolves display names`() {
        val buckets = listOf(
            bucket("web", cost = 0.1, input = 10),
            bucket("cli", cost = 2.0, input = 20),
            bucket("cursor", cost = 0.5, input = 30),
            bucket("cli", cost = 1.0, input = 40),
        )
        val names = UsageNames(sources = mapOf("cli" to "Claude Code", "cursor" to "Cursor"))

        val rows = UsageAgg.bySource(usage(buckets, names = names))

        assertEquals(listOf("cli", "cursor", "web"), rows.map { it.key })
        assertEquals(listOf("Claude Code", "Cursor", "web"), rows.map { it.name })
        assertEquals(listOf(3.0, 0.5, 0.1), rows.map { it.cost })
        assertEquals(60L, rows.first().tokens)
    }

    @Test
    fun `trend length matches each range`() {
        val empty = usage()

        assertEquals(24, UsageAgg.trend(empty, Range.TODAY, zone).size)
        assertEquals(24, UsageAgg.trend(empty, Range.H24, zone).size)
        assertEquals(7, UsageAgg.trend(empty, Range.D7, zone).size)
        assertEquals(30, UsageAgg.trend(empty, Range.D30, zone).size)
        assertEquals(90, UsageAgg.trend(empty, Range.D90, zone).size)
    }

    @Test
    fun `today trend labels are the 24 hours of the day`() {
        val labels = UsageAgg.trend(usage(), Range.TODAY, zone).map { it.label }

        assertEquals((0..23).map { "%02d".format(it) }, labels)
    }

    @Test
    fun `today trend puts a bucket in the label matching its hour`() {
        val now = ZonedDateTime.now(zone)
        val point = UsageAgg.trend(
            usage(listOf(bucket("cli", date = now.toLocalDate(), hour = now.hour, cost = 0.5, input = 7))),
            Range.TODAY,
            zone,
        ).single { it.label == "%02d".format(now.hour) }

        assertEquals(0.5, point.cost, 1e-9)
        assertEquals(7L, point.tokens)
    }

    @Test
    fun `daily trend labels are month day pairs and every bucket today is counted`() {
        val points = UsageAgg.trend(
            usage(listOf(bucket("cli", date = LocalDate.now(zone), cost = 0.5, input = 7))),
            Range.D7,
            zone,
        )

        assertEquals(7, points.size)
        assertEquals(7, points.map { it.label }.toSet().size)
        assertTrue(points.all { Regex("\\d{1,2}-\\d{1,2}").matches(it.label) })
        assertEquals(0.5, points.sumOf { it.cost }, 1e-9)
        assertEquals(7L, points.sumOf { it.tokens })
    }
}
