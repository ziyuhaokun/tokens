package com.ziyuhaokun.tokens.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 客户端的聚合口径，与 Windows 桌面端保持一致。 */
object UsageAgg {

    data class Totals(
        val cost: Double,
        val tokens: Long,
        val cachedInput: Long,
        val activeSeconds: Long,
        val sessions: Int,
        val hosts: Int,
        val sources: Int,
    )

    data class Row(
        val key: String,
        val name: String,
        val cost: Double,
        val tokens: Long,
        val sessions: Int = 0,
        val activeSeconds: Long = 0,
    )

    data class TrendPoint(val label: String, val cost: Double, val tokens: Long)

    fun totals(usage: Usage): Totals = Totals(
        cost = usage.buckets.sumOf { it.estimatedCost },
        tokens = usage.buckets.sumOf { it.tokens },
        cachedInput = usage.buckets.sumOf { it.cachedInputTokens },
        activeSeconds = usage.sessions.sumOf { it.activeSeconds },
        sessions = usage.sessions.size,
        hosts = usage.buckets.map { it.hostname }.filter { it.isNotBlank() }.toSet().size,
        sources = usage.buckets.map { it.source }.toSet().size,
    )

    fun bySource(usage: Usage): List<Row> = rows(usage, keyOf = { it.source }, nameOf = { usage.sourceName(it) })

    fun byModel(usage: Usage): List<Row> = rows(usage, keyOf = { it.model }, nameOf = { usage.modelName(it) })

    fun byProject(usage: Usage): List<Row> {
        val sessionsByProject = usage.sessions.groupBy { it.project }
        return rows(
            usage,
            keyOf = { it.project },
            nameOf = { it },
            decorate = { row ->
                val list = sessionsByProject[row.key].orEmpty()
                row.copy(sessions = list.size, activeSeconds = list.sumOf { it.activeSeconds })
            },
        )
    }

    private fun rows(
        usage: Usage,
        keyOf: (Bucket) -> String,
        nameOf: (String) -> String,
        decorate: (Row) -> Row = { it },
    ): List<Row> {
        val map = LinkedHashMap<String, Row>()
        for (bucket in usage.buckets) {
            val key = keyOf(bucket)
            val current = map[key] ?: Row(key = key, name = nameOf(key), cost = 0.0, tokens = 0)
            map[key] = current.copy(
                cost = current.cost + bucket.estimatedCost,
                tokens = current.tokens + bucket.tokens,
            )
        }
        return map.values.map(decorate).sortedWith(compareByDescending<Row> { it.cost }.thenByDescending { it.tokens })
    }

    /** 今天/24H 走小时粒度，其余走天粒度；返回与范围严格对齐的定长序列。 */
    fun trend(
        usage: Usage,
        range: Range,
        zone: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zone),
    ): List<TrendPoint> {
        val cost = HashMap<Any, Double>()
        val tokens = HashMap<Any, Long>()

        if (range.isHourly) {
            val startHour = if (range == Range.TODAY) {
                now.toLocalDate().atStartOfDay()
            } else {
                now.truncatedTo(ChronoUnit.HOURS).toLocalDateTime().minusHours(23)
            }
            for (bucket in usage.buckets) {
                if (bucket.bucketStartMillis <= 0) continue
                val key = Instant.ofEpochMilli(bucket.bucketStartMillis).atZone(zone)
                    .toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
                cost[key] = (cost[key] ?: 0.0) + bucket.estimatedCost
                tokens[key] = (tokens[key] ?: 0L) + bucket.tokens
            }
            return (0 until 24).map { offset ->
                val slot: LocalDateTime = startHour.plusHours(offset.toLong())
                val label = "%02d".format(slot.hour)
                TrendPoint(label, cost[slot] ?: 0.0, tokens[slot] ?: 0L)
            }
        }

        val days = range.days
        for (bucket in usage.buckets) {
            if (bucket.bucketStartMillis <= 0) continue
            val key = Instant.ofEpochMilli(bucket.bucketStartMillis).atZone(zone).toLocalDate()
            cost[key] = (cost[key] ?: 0.0) + bucket.estimatedCost
            tokens[key] = (tokens[key] ?: 0L) + bucket.tokens
        }
        val today = now.toLocalDate()
        return (0 until days).map { offset ->
            val day: LocalDate = today.minusDays((days - 1 - offset).toLong())
            TrendPoint("${day.monthValue}-${day.dayOfMonth}", cost[day] ?: 0.0, tokens[day] ?: 0L)
        }
    }
}
