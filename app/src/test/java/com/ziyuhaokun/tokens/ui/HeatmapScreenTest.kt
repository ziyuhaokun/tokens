package com.ziyuhaokun.tokens.ui

import com.ziyuhaokun.tokens.data.DayUsage
import com.ziyuhaokun.tokens.data.YearData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 只测 HeatmapScreen.kt 底部的纯函数（阈值、色阶、月份分行），不碰 Compose。 */
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

    private fun day(cost: Double = 0.0, tokens: Long = 0L): DayUsage = DayUsage(end, cost, tokens, 0L)

    @Test
    fun `thresholds keep cents instead of truncating to whole dollars`() {
        // 全是小额：取整会把这一串压成 1 块钱的几档，颜色全糊在一起。
        val costs = listOf(0.05, 0.3, 0.45, 0.9, 1.9, 2.4, 3.1, 4.6, 6.0, 9.5)
        val data = yearData(costs = costs.mapIndexed { i, cost -> end.minusDays(i * 3L) to cost }.toMap())

        val cuts = thresholds(data, YearMetric.COST)

        assertEquals(3, cuts.size)
        assertArrayEquals(doubleArrayOf(0.9, 3.1, 6.0), cuts, 0.0)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 3, 3, 4), costs.map { levelOf(day(cost = it), YearMetric.COST, cuts) })
    }

    @Test
    fun `small and mid dollar days land in different levels`() {
        val data = yearData(
            costs = mapOf(
                end.minusDays(6) to 0.05,
                end.minusDays(5) to 0.3,
                end.minusDays(4) to 0.45,
                end.minusDays(3) to 0.9,
                end.minusDays(2) to 1.9,
                end.minusDays(1) to 6.0,
                end to 9.5,
            ),
        )

        val cuts = thresholds(data, YearMetric.COST)

        // 这就是修复前的 bug：$0.3 和 $1.9 都 .toInt() 成 0/1，挤在同一档。
        assertNotEquals(
            levelOf(day(cost = 0.3), YearMetric.COST, cuts),
            levelOf(day(cost = 1.9), YearMetric.COST, cuts),
        )
        assertEquals(1, levelOf(day(cost = 0.3), YearMetric.COST, cuts))
        assertEquals(4, levelOf(day(cost = 9.5), YearMetric.COST, cuts))
    }

    @Test
    fun `thresholds are non decreasing`() {
        val data = yearData(costs = mapOf(end to 0.42, end.minusDays(1) to 3.7, end.minusDays(2) to 12.4))
        val cuts = thresholds(data, YearMetric.COST)

        assertTrue(cuts[0] <= cuts[1])
        assertTrue(cuts[1] <= cuts[2])
    }

    @Test
    fun `thresholds with no usage are all zero and every day stays empty`() {
        val data = yearData()
        val cuts = thresholds(data, YearMetric.COST)

        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), cuts, 0.0)
        assertEquals(0, levelOf(null, YearMetric.COST, cuts))
        // hasUsage 为假的日子画空格，不进色阶。
        assertEquals(0, levelOf(day(), YearMetric.COST, cuts))
    }

    @Test
    fun `single non zero day gives flat thresholds`() {
        val data = yearData(costs = mapOf(end to 0.3))
        val cuts = thresholds(data, YearMetric.COST)

        assertArrayEquals(doubleArrayOf(0.3, 0.3, 0.3), cuts, 0.0)
        assertEquals(1, levelOf(day(cost = 0.3), YearMetric.COST, cuts))
        assertEquals(4, levelOf(day(cost = 0.31), YearMetric.COST, cuts))
    }

    @Test
    fun `tokens metric thresholds use token counts`() {
        val tokens = listOf(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L)
        val data = yearData(
            // cost 全为 0，证明阈值真的取的是 tokens 而不是费用。
            tokens = tokens.mapIndexed { i, value -> end.minusDays(i.toLong()) to value }.toMap(),
        )

        val cuts = thresholds(data, YearMetric.TOKENS)

        assertArrayEquals(doubleArrayOf(400.0, 700.0, 900.0), cuts, 0.0)
        assertEquals(1, levelOf(day(tokens = 400L), YearMetric.TOKENS, cuts))
        assertEquals(3, levelOf(day(tokens = 900L), YearMetric.TOKENS, cuts))
        assertEquals(4, levelOf(day(tokens = 1000L), YearMetric.TOKENS, cuts))
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
