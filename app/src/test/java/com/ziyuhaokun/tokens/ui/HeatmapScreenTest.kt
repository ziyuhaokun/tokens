package com.ziyuhaokun.tokens.ui

import com.ziyuhaokun.tokens.data.DayUsage
import com.ziyuhaokun.tokens.data.YearData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 只测热力图的月份分行。色阶阈值在 YearAggTest。 */
class HeatmapScreenTest {

    private val end: LocalDate = LocalDate.of(2026, 11, 20)

    /** 完整 365 天窗口，只有 costs / tokens 里点名的日子有用量。 */
    private fun yearData(
        costs: Map<LocalDate, Double> = emptyMap(),
        tokens: Map<LocalDate, Long> = emptyMap(),
    ): YearData {
        val start = end.minusDays(364)
        val days = (0 until 365).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayUsage(
                date = date,
                cost = costs[date] ?: 0.0,
                tokens = tokens[date] ?: 0L,
                activeSeconds = 0L,
            )
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

    @Test
    fun `monthsOf gives 13 rows for a 365 day window spanning two same numbered months`() {
        val rows = monthsOf(yearData())

        assertEquals(13, rows.size)
        // 首行是 2025-11，末行是 2026-11：同一个月份号，两个不同年份。
        assertEquals(2025, rows.first().second.filterNotNull().first().year)
        assertEquals(2026, rows.last().second.filterNotNull().last().year)
        assertEquals(listOf(11, 12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), rows.map { it.first })
        assertTrue(rows.all { it.second.size == 31 })
    }

    @Test
    fun `monthsOf puts each date in its day of month column`() {
        val rows = monthsOf(yearData())
        val firstNov = rows.first()
        val lastNov = rows.last()

        // 窗口第一天是 2025-11-21：前 20 列必须是空的，不是从第 1 列开始塞。
        assertNull(firstNov.second[19])
        assertEquals(LocalDate.of(2025, 11, 21), firstNov.second[20])
        assertEquals(LocalDate.of(2025, 11, 22), firstNov.second[21])

        // 两行都是「11 月」，但装的是不同年份的日期。
        assertEquals(LocalDate.of(2026, 11, 1), lastNov.second[0])
        assertEquals(LocalDate.of(2026, 11, 20), lastNov.second[19])
        assertNull(lastNov.second[20])
        assertNotEquals(firstNov.second[20], lastNov.second[20])
    }

    @Test
    fun `monthsOf keeps a full month aligned and February short`() {
        val rows = monthsOf(yearData())
        val march = rows.single { it.second.contains(LocalDate.of(2026, 3, 1)) }.second
        val february = rows.single { it.second.contains(LocalDate.of(2026, 2, 1)) }.second

        assertEquals(LocalDate.of(2026, 3, 31), march[30])
        assertNull(february[28])
        assertNull(february[30])
    }
}
