package com.ziyuhaokun.tokens.ui

import java.util.Locale

object Fmt {
    fun money(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

    /** 拆成 "$578" + ".22"：大号整数 + 小号小数，桌面端那套排版。 */
    fun moneyParts(value: Double): Pair<String, String> {
        val text = String.format(Locale.US, "%.2f", value)
        val dot = text.indexOf('.')
        return if (dot < 0) "$text" to ".00" else "$" + text.substring(0, dot) to text.substring(dot)
    }

    fun tokens(value: Long): String = when {
        value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1e9)
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
        else -> value.toString()
    }

    /** "3h 12m" 这种，没有整点就只有分钟。 */
    fun duration(seconds: Long): String {
        val totalMinutes = (seconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 && minutes <= 0 -> "0m"
            hours <= 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    fun clock(millis: Long?): String =
        if (millis == null || millis <= 0) "—"
        else java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(millis))

    fun ago(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null || millis <= 0) return "未更新"
        val minutes = (now - millis) / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            else -> "${minutes / (60 * 24)} 天前"
        }
    }
}
