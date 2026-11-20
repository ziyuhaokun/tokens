package com.ziyuhaokun.tokens.data

import java.util.Locale
import kotlin.math.abs

/**
 * 数字怎么显示：货币（美元 / 人民币）和 token 单位（K/M/B / 万/亿）。
 *
 * 纯 Kotlin，不依赖 Android：App 里通过 LocalNumberFormats 拿，
 * 小组件和小票按设置自己构造一份。所有金额入参都是**美元**（服务端口径），
 * 在这里统一换算。
 */
data class NumberFormats(
    val currency: CurrencyChoice = CurrencyChoice.USD,
    val cnyPerUsd: Double = DEFAULT_CNY_PER_USD,
    val units: UnitStyle = UnitStyle.EN,
) {
    val symbol: String get() = currency.symbol

    /** 美元 → 当前货币的数值。 */
    fun convert(usd: Double): Double = when (currency) {
        CurrencyChoice.USD -> usd
        CurrencyChoice.CNY -> usd * cnyPerUsd
    }

    /** 带千分位：$2,460.18 / ¥17,467.28。 */
    fun money(usd: Double): String = symbol + String.format(Locale.US, "%,.2f", convert(usd))

    /** 拆成 "$578" + ".22"：大号整数 + 小号小数。整数段以货币符号开头。 */
    fun moneyParts(usd: Double): Pair<String, String> {
        val text = String.format(Locale.US, "%,.2f", convert(usd))
        val dot = text.indexOf('.')
        return if (dot < 0) symbol + text to ".00" else symbol + text.substring(0, dot) to text.substring(dot)
    }

    fun tokens(value: Long): String = when (units) {
        UnitStyle.EN -> english(value)
        UnitStyle.CN -> chinese(value)
    }

    /**
     * Token 量拆成「整数」+「小数与单位」两段，给大号数字排版用。
     * "123.36B" → ("123", ".36B")；"4021万" → ("4021", "万")；没有单位时后半段为空。
     */
    fun tokensParts(value: Long): Pair<String, String> {
        val text = tokens(value)
        val dot = text.indexOf('.')
        if (dot >= 0) return text.substring(0, dot) to text.substring(dot)
        val unit = text.lastOrNull()?.takeIf { it.isLetter() }
        return if (unit != null) text.dropLast(1) to unit.toString() else text to ""
    }

    private fun english(value: Long): String = when {
        abs(value) >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1e9)
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1e6)
        abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1e3)
        else -> value.toString()
    }

    /** 1 万以下原样；万 / 亿保留约 4 位有效数字：4021万、461.6万、19.16亿。 */
    private fun chinese(value: Long): String {
        fun scaled(x: Double, unit: String): String {
            val digits = when {
                abs(x) >= 1000 -> 0
                abs(x) >= 100 -> 1
                else -> 2
            }
            return String.format(Locale.US, "%.${digits}f", x) + unit
        }
        return when {
            abs(value) >= 100_000_000 -> scaled(value / 1e8, "亿")
            abs(value) >= 10_000 -> scaled(value / 1e4, "万")
            else -> value.toString()
        }
    }

    companion object {
        val DEFAULT = NumberFormats()
    }
}
