package com.ziyuhaokun.tokens.data

import java.time.ZoneId
import java.time.ZonedDateTime

/** 与桌面端一致的五个时间范围。 */
enum class Range(val id: String, val label: String) {
    TODAY("today", "今天"),
    H24("24H", "24H"),
    D7("7D", "7D"),
    D30("30D", "30D"),
    D90("90D", "90D");

    val isHourly: Boolean get() = this == TODAY || this == H24

    fun params(zone: ZoneId = ZoneId.systemDefault()): Map<String, String> = when (this) {
        TODAY -> mapOf("from" to ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant().toString())
        H24 -> mapOf("days" to "1")
        D7 -> mapOf("days" to "7")
        D30 -> mapOf("days" to "30")
        D90 -> mapOf("days" to "90")
    }

    companion object {
        fun fromId(id: String?): Range = entries.firstOrNull { it.id == id } ?: D7
    }
}
