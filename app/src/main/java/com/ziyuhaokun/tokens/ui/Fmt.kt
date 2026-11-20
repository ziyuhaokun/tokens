package com.ziyuhaokun.tokens.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun money(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

    fun tokens(value: Long): String = when {
        value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1e9)
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
        else -> value.toString()
    }

    fun hours(seconds: Long): String = String.format(Locale.US, "%.1fh", seconds / 3600.0)

    fun clock(millis: Long?): String =
        if (millis == null || millis <= 0) "—" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
