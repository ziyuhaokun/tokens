package com.ziyuhaokun.tokens.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

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
