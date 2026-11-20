package com.ziyuhaokun.tokens.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @Test
    fun `money formats with two decimals`() {
        assertEquals("$0.00", Fmt.money(0.0))
        assertEquals("$0.30", Fmt.money(0.3))
        assertEquals("$1.90", Fmt.money(1.9))
        assertEquals("$1234.50", Fmt.money(1234.5))
    }

    @Test
    fun `moneyParts splits integer and decimals`() {
        assertEquals("$578" to ".22", Fmt.moneyParts(578.22))
        assertEquals("$0" to ".30", Fmt.moneyParts(0.3))
        assertEquals("$0" to ".00", Fmt.moneyParts(0.0))
    }

    @Test
    fun `tokens picks a unit by magnitude`() {
        assertEquals("999", Fmt.tokens(999))
        assertEquals("1.0K", Fmt.tokens(1_000))
        assertEquals("1.5K", Fmt.tokens(1_500))
        assertEquals("2.5M", Fmt.tokens(2_500_000))
        assertEquals("3.46B", Fmt.tokens(3_456_789_012))
    }

    @Test
    fun `tokensParts splits integer from decimals and unit`() {
        assertEquals("999" to "", Fmt.tokensParts(999))
        assertEquals("1" to ".5K", Fmt.tokensParts(1_500))
        assertEquals("2" to ".5M", Fmt.tokensParts(2_500_000))
        assertEquals("3" to ".46B", Fmt.tokensParts(3_456_789_012))
    }

    @Test
    fun `duration shows hours and minutes`() {
        assertEquals("0m", Fmt.duration(0))
        assertEquals("0m", Fmt.duration(59))
        assertEquals("1m", Fmt.duration(60))
        assertEquals("2m", Fmt.duration(125))
        assertEquals("1h", Fmt.duration(3_600))
        assertEquals("3h 12m", Fmt.duration(3 * 3_600L + 12 * 60L))
    }
}
