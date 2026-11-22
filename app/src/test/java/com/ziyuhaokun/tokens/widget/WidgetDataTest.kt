package com.ziyuhaokun.tokens.widget

import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.Bucket
import com.ziyuhaokun.tokens.data.CacheEntry
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.ThemeMode
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetDataTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, zone)
    private val at = now.toInstant().toEpochMilli()

    private fun settings(
        apiKey: String? = "vbu_test",
        themeMode: ThemeMode = ThemeMode.DARK,
        accent: AccentChoice = AccentChoice.MONO,
    ) = StoredSettings(
        apiKey = apiKey,
        baseUrl = "https://vibecafe.ai",
        range = Range.D7,
        uploadProject = null,
        themeMode = themeMode,
        accent = accent,
    )

    private fun usage(handle: String, vararg rows: Pair<String, Double>, at: Long = this.at): Usage = Usage(
        accountHandle = handle,
        buckets = rows.map { (source, cost) ->
            Bucket(
                source = source,
                model = "model",
                project = "project",
                hostname = "host",
                bucketStartMillis = at,
                inputTokens = 10L,
                outputTokens = 0L,
                cachedInputTokens = 0L,
                reasoningOutputTokens = 0L,
                cacheCreation5m = 0L,
                cacheCreation1h = 0L,
                estimatedCost = cost,
            )
        },
        sessions = emptyList(),
        hasAnyData = rows.isNotEmpty(),
        names = UsageNames(),
        rawJson = "{}",
    )

    @Test
    fun `not configured keeps appearance but hides every number`() {
        val snapshot = widgetNotConfigured(
            settings(apiKey = null, themeMode = ThemeMode.LIGHT, accent = AccentChoice.BLUE),
        )

        assertFalse(snapshot.configured)
        assertNull(snapshot.todayCost)
        assertNull(snapshot.weekCost)
        assertNull(snapshot.weekTokens)
        assertNull(snapshot.handle)
        assertTrue(snapshot.trend.isEmpty())
        assertEquals(ThemeMode.LIGHT, snapshot.themeMode)
        assertEquals(AccentChoice.BLUE, snapshot.accent)
    }

    @Test
    fun `missing cache is null not zero`() {
        val snapshot = mapWidgetSnapshot(settings(), today = null, week = null, zone, now)

        assertTrue(snapshot.configured)
        assertNull(snapshot.todayCost)
        assertNull(snapshot.weekCost)
        assertNull(snapshot.weekTokens)
        assertNull(snapshot.updatedAt)
        assertTrue(snapshot.trend.isEmpty())
        assertTrue(snapshot.topTools.isEmpty())
        assertNull(snapshot.message)
    }

    @Test
    fun `cache is mapped to today week trend updatedAt and top tools`() {
        val today = WidgetUsageEntry(usage("me", "cli" to 1.0), fetchedAt = 100L)
        val week = WidgetUsageEntry(
            usage("me", "cli" to 2.0, "api" to 1.5),
            fetchedAt = 200L,
        )

        val snapshot = mapWidgetSnapshot(settings(), today, week, zone, now)

        assertEquals("me", snapshot.handle)
        assertEquals(1.0, snapshot.todayCost!!, 1e-9)
        assertEquals(3.5, snapshot.weekCost!!, 1e-9)
        assertEquals(20L, snapshot.weekTokens)
        assertEquals(7, snapshot.trend.size)
        assertEquals(3.5, snapshot.trend.sum(), 1e-9)
        assertEquals(200L, snapshot.updatedAt)
        assertEquals(listOf("cli", "api"), snapshot.topTools.map { it.name })
    }

    @Test
    fun `today handle falls back to week when today cache is missing`() {
        val snapshot = mapWidgetSnapshot(
            settings(),
            today = null,
            week = WidgetUsageEntry(usage("week-only", "cli" to 1.0), fetchedAt = 5L),
            zone,
            now,
        )

        assertEquals("week-only", snapshot.handle)
        assertNull(snapshot.todayCost)
        assertEquals(5L, snapshot.updatedAt)
    }

    @Test
    fun `trend start label uses injected day`() {
        assertEquals("11-14", trendStartLabel(LocalDate.of(2026, 11, 20)))
        assertEquals("12-31", trendStartLabel(LocalDate.of(2027, 1, 6)))
    }

    @Test
    fun `bad cached json is treated as no cache`() {
        assertNull(parseWidgetEntry(null))
        assertNull(parseWidgetEntry(CacheEntry("not json", 1L)))
    }
}
