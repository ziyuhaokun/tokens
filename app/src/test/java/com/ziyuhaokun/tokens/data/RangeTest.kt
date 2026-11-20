package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RangeTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `long ranges request an inclusive from-to window`() {
        val today = LocalDate.now(zone)
        assertEquals(
            mapOf("from" to today.minusDays(179).toString(), "to" to today.toString()),
            Range.D180.params(zone),
        )
        assertEquals(
            mapOf("from" to today.minusDays(364).toString(), "to" to today.toString()),
            Range.D365.params(zone),
        )
    }

    @Test
    fun `365D shares the heatmap year cache and window`() {
        assertEquals(UsageRepository.YEAR_CACHE_KEY, Range.D365.cacheId)
        assertEquals(Range.dateWindow(YearAgg.DAYS, zone), Range.D365.params(zone))
        assertEquals("180D", Range.D180.cacheId)
    }

    @Test
    fun `ids round-trip and unknown falls back to 7D`() {
        Range.entries.forEach { assertEquals(it, Range.fromId(it.id)) }
        assertEquals(Range.D7, Range.fromId("nope"))
        assertEquals(Range.D7, Range.fromId(null))
    }
}
