package com.ziyuhaokun.tokens.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.ziyuhaokun.tokens.data.NumberFormats
import java.util.Locale

/**
 * 当前的数字显示方式（货币 / token 单位），由 MainActivity 按设置提供。
 * 金额、token 一律走它：`val nf = LocalNumberFormats.current` 然后 `nf.money(...)`。
 */
val LocalNumberFormats = staticCompositionLocalOf { NumberFormats.DEFAULT }

/** 与货币 / 单位无关的格式化：时长、时刻、多久之前。 */
object Fmt {
    /** 页头小字：有用户名就加 @（已经带 @ 的不重复加），没有就回退到站点名。 */
    fun handle(handle: String?): String = when {
        handle.isNullOrBlank() -> "vibefriends.com"
        handle.startsWith("@") -> handle
        else -> "@$handle"
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
