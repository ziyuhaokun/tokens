package com.ziyuhaokun.tokens.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StepDateTest {

    private val dates = listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3))

    @Test
    fun `steps forward and backward`() {
        assertEquals(dates[2], stepDate(dates, dates[1], 1))
        assertEquals(dates[0], stepDate(dates, dates[1], -1))
    }

    @Test
    fun `stops at both ends`() {
        assertEquals(dates[2], stepDate(dates, dates[2], 1))
        assertEquals(dates[0], stepDate(dates, dates[0], -1))
    }

    @Test
    fun `nothing selected starts from the end that makes sense`() {
        assertEquals(dates[2], stepDate(dates, null, -1))
        assertEquals(dates[0], stepDate(dates, null, 1))
        // 选中的日期不在列表里，当作没选
        assertEquals(dates[2], stepDate(dates, LocalDate.of(2020, 1, 1), -1))
    }

    @Test
    fun `empty list gives null`() {
        assertNull(stepDate(emptyList(), null, 1))
    }
}
