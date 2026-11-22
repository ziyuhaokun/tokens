package com.ziyuhaokun.tokens.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 首页的时间范围。[days] 是按天画图时的天数（小时粒度的两档为 1）。
 *
 * 180D / 365D 用 `from/to` 日期区间请求：服务端的 `days=` 只实测过 1/7/30/90，
 * 而任意 from/to 区间热力图已经在用（365 天 ~1.5MB / 600ms）。
 */
enum class Range(val id: String, val label: String, val days: Int) {
    TODAY("today", "今天", 1),
    H24("24H", "24H", 1),
    D7("7D", "7D", 7),
    D30("30D", "30D", 30),
    D90("90D", "90D", 90),
    D180("180D", "180D", 180),
    D365("365D", "365D", 365);

    val isHourly: Boolean get() = this == TODAY || this == H24

    /**
     * 本地缓存文件名。365D 和热力图请求的是同一段 from/to，
     * 共用 "year" 这份缓存，两边谁先拉到另一边都能直接用。
     */
    val cacheId: String get() = if (this == D365) UsageRepository.YEAR_CACHE_KEY else id

    fun params(
        zone: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(zone),
    ): Map<String, String> = when (this) {
        TODAY -> mapOf("from" to now.toLocalDate().atStartOfDay(zone).toInstant().toString())
        H24 -> mapOf("days" to "1")
        D7 -> mapOf("days" to "7")
        D30 -> mapOf("days" to "30")
        D90 -> mapOf("days" to "90")
        D180, D365 -> dateWindow(days, zone, now.toLocalDate())
    }

    companion object {
        fun fromId(id: String?): Range = entries.firstOrNull { it.id == id } ?: D7

        /** 含今天在内往前 [days] 天：from = 今天 - (days-1)，to = 今天。 */
        fun dateWindow(
            days: Int,
            zone: ZoneId = ZoneId.systemDefault(),
            today: LocalDate = LocalDate.now(zone),
        ): Map<String, String> {
            return mapOf(
                "from" to today.minusDays((days - 1).toLong()).toString(),
                "to" to today.toString(),
            )
        }
    }
}
