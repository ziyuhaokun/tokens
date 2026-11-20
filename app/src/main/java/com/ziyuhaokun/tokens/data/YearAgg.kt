package com.ziyuhaokun.tokens.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 一天的量，热力图一格。 */
data class DayUsage(
    val date: LocalDate,
    val cost: Double,
    val tokens: Long,
    val activeSeconds: Long,
) {
    val hasUsage: Boolean get() = tokens > 0 || cost > 0.0
}

/** 过去一年（365 天，含今天）的按日视图。 */
data class YearData(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<DayUsage>,
    val totalCost: Double,
    val totalTokens: Long,
    val activeDays: Int,
    val longestStreak: Int,
    val bestDay: DayUsage?,
) {
    val averageCostPerActiveDay: Double
        get() = if (activeDays <= 0) 0.0 else totalCost / activeDays

    val averageTokensPerActiveDay: Long
        get() = if (activeDays <= 0) 0L else totalTokens / activeDays
}

/**
 * 把半小时 bucket 折成「本地日」。
 *
 * 口径和仪表盘一致：一天的费用是当天所有 bucket 的 estimatedCost 之和，
 * token 用 Bucket.tokens（缓存读、缓存写都算进去）。会话按 firstMessageAt
 * 落到开始那天——和 CLI 把活跃时间记在起始半小时桶的做法一致。
 */
object YearAgg {

    const val DAYS = 365

    fun build(usage: Usage, endDate: LocalDate, zone: ZoneId = ZoneId.systemDefault()): YearData {
        val start = endDate.minusDays((DAYS - 1).toLong())

        val cost = HashMap<LocalDate, Double>()
        val tokens = HashMap<LocalDate, Long>()
        for (bucket in usage.buckets) {
            if (bucket.bucketStartMillis <= 0L) continue
            val day = Instant.ofEpochMilli(bucket.bucketStartMillis).atZone(zone).toLocalDate()
            if (day < start || day > endDate) continue
            cost[day] = (cost[day] ?: 0.0) + bucket.estimatedCost
            tokens[day] = (tokens[day] ?: 0L) + bucket.tokens
        }

        val active = HashMap<LocalDate, Long>()
        for (session in usage.sessions) {
            if (session.startMillis <= 0L) continue
            val day = Instant.ofEpochMilli(session.startMillis).atZone(zone).toLocalDate()
            if (day < start || day > endDate) continue
            active[day] = (active[day] ?: 0L) + session.activeSeconds
        }

        val days = (0 until DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayUsage(
                date = date,
                cost = cost[date] ?: 0.0,
                tokens = tokens[date] ?: 0L,
                activeSeconds = active[date] ?: 0L,
            )
        }

        var streak = 0
        var bestStreak = 0
        var best: DayUsage? = null
        for (day in days) {
            if (day.hasUsage) {
                streak++
                if (streak > bestStreak) bestStreak = streak
            } else {
                streak = 0
            }
            if (day.hasUsage && (best == null || day.cost > best.cost)) best = day
        }

        return YearData(
            start = start,
            end = endDate,
            days = days,
            totalCost = days.sumOf { it.cost },
            totalTokens = days.sumOf { it.tokens },
            activeDays = days.count { it.hasUsage },
            longestStreak = bestStreak,
            bestDay = best,
        )
    }
}
