package com.ziyuhaokun.tokens.ui

import com.ziyuhaokun.tokens.data.NumberFormats
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendDescriptionTest {

    private val nf = NumberFormats.DEFAULT

    @Test
    fun `description names total, peak day and latest value`() {
        val text = trendDescription("7D", nf, 12.0, listOf(1.0, 5.5, 2.0), listOf("11-14", "11-15", "11-16"))
        assertEquals("7D 费用趋势，合计 ${nf.money(12.0)}，最高 ${nf.money(5.5)}（11-15），最新 ${nf.money(2.0)}", text)
    }

    @Test
    fun `all zero data has no peak part`() {
        val text = trendDescription("今天", nf, 0.0, listOf(0.0, 0.0), listOf("00:00", "01:00"))
        assertEquals("今天 费用趋势，合计 ${nf.money(0.0)}，最新 ${nf.money(0.0)}", text)
    }

    @Test
    fun `missing labels and empty data do not crash`() {
        assertEquals("30D 费用趋势，合计 ${nf.money(0.0)}", trendDescription("30D", nf, 0.0, emptyList(), emptyList()))
        assertEquals(
            "30D 费用趋势，合计 ${nf.money(3.0)}，最高 ${nf.money(3.0)}，最新 ${nf.money(3.0)}",
            trendDescription("30D", nf, 3.0, listOf(3.0), emptyList()),
        )
    }
}
