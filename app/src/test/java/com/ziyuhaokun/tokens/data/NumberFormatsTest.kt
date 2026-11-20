package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatsTest {

    private val usdEn = NumberFormats.DEFAULT
    private val cny = NumberFormats(currency = CurrencyChoice.CNY, cnyPerUsd = 7.10)
    private val cn = NumberFormats(units = UnitStyle.CN)

    @Test
    fun `usd money has symbol, two decimals and grouping`() {
        assertEquals("$0.00", usdEn.money(0.0))
        assertEquals("$0.30", usdEn.money(0.3))
        assertEquals("$1.90", usdEn.money(1.9))
        assertEquals("$1,234.50", usdEn.money(1234.5))
        assertEquals("$2,460.18", usdEn.money(2460.18))
    }

    @Test
    fun `usd moneyParts splits integer and decimals`() {
        assertEquals("$578" to ".22", usdEn.moneyParts(578.22))
        assertEquals("$0" to ".30", usdEn.moneyParts(0.3))
        assertEquals("$0" to ".00", usdEn.moneyParts(0.0))
    }

    @Test
    fun `cny converts from usd with the configured rate`() {
        assertEquals("¥710.00", cny.money(100.0))
        assertEquals("¥17,467" to ".28", cny.moneyParts(2460.18))
        assertEquals("¥0.00", cny.money(0.0))
        // 汇率可改
        assertEquals("¥720.00", cny.copy(cnyPerUsd = 7.2).money(100.0))
    }

    @Test
    fun `english units pick K M B by magnitude`() {
        assertEquals("999", usdEn.tokens(999))
        assertEquals("1.0K", usdEn.tokens(1_000))
        assertEquals("1.5K", usdEn.tokens(1_500))
        assertEquals("2.5M", usdEn.tokens(2_500_000))
        assertEquals("3.46B", usdEn.tokens(3_456_789_012))
    }

    @Test
    fun `chinese units use wan and yi with about four significant digits`() {
        assertEquals("9999", cn.tokens(9_999))
        assertEquals("1.00万", cn.tokens(10_000))
        assertEquals("461.6万", cn.tokens(4_615_800))
        assertEquals("4021万", cn.tokens(40_210_000))
        assertEquals("8.75亿", cn.tokens(874_600_000))
        assertEquals("19.16亿", cn.tokens(1_916_000_000))
        assertEquals("1916亿", cn.tokens(191_600_000_000))
    }

    @Test
    fun `tokensParts splits integer from decimals and unit in both styles`() {
        assertEquals("999" to "", usdEn.tokensParts(999))
        assertEquals("1" to ".5K", usdEn.tokensParts(1_500))
        assertEquals("3" to ".46B", usdEn.tokensParts(3_456_789_012))
        assertEquals("19" to ".16亿", cn.tokensParts(1_916_000_000))
        assertEquals("4021" to "万", cn.tokensParts(40_210_000))
        assertEquals("9999" to "", cn.tokensParts(9_999))
    }
}
