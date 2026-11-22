package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RangeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 11, 20, 23, 59, 0, 0, zone)

    @Test
    fun `long ranges request an inclusive from-to window`() {
        val today = now.toLocalDate()
        assertEquals(
            mapOf("from" to today.minusDays(179).toString(), "to" to today.toString()),
            Range.D180.params(zone, now),
        )
        assertEquals(
            mapOf("from" to today.minusDays(364).toString(), "to" to today.toString()),
            Range.D365.params(zone, now),
        )
    }

    @Test
    fun `today from is local midnight of the injected day`() {
        val midnight = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toString()

        assertEquals(mapOf("from" to midnight), Range.TODAY.params(zone, now))
    }

    @Test
    fun `365D shares the heatmap year cache and window`() {
        assertEquals(UsageRepository.YEAR_CACHE_KEY, Range.D365.cacheId)
        assertEquals(
            Range.dateWindow(YearAgg.DAYS, zone, now.toLocalDate()),
            Range.D365.params(zone, now),
        )
        assertEquals("180D", Range.D180.cacheId)
    }

    @Test
    fun `ids round-trip and unknown falls back to 7D`() {
        Range.entries.forEach { assertEquals(it, Range.fromId(it.id)) }
        assertEquals(Range.D7, Range.fromId("nope"))
        assertEquals(Range.D7, Range.fromId(null))
    }
}
