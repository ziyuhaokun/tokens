package com.ziyuhaokun.tokens.data

/** 外观：深色 / 浅色 / 跟随系统。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    DARK("dark", "深色"),
    LIGHT("light", "浅色");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 强调色。MONO 等于不上色，和现有的单色设计完全一致。 */
enum class AccentChoice(val id: String, val label: String) {
    MONO("mono", "单色"),
    BLUE("blue", "蓝"),
    PURPLE("purple", "紫"),
    TEAL("teal", "青"),
    ORANGE("orange", "橙");

    companion object {
        fun fromId(id: String?): AccentChoice = entries.firstOrNull { it.id == id } ?: MONO
    }
}

/** 文字大小，乘以系统的 fontScale。 */
enum class FontScaleChoice(val id: String, val label: String, val scale: Float) {
    SMALL("small", "小", 0.85f),
    STANDARD("standard", "标准", 1.0f),
    LARGE("large", "大", 1.15f),
    XLARGE("xlarge", "特大", 1.3f);

    companion object {
        fun fromId(id: String?): FontScaleChoice = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}


/** 金额显示的货币。服务端给的是美元估算，人民币按设置里的汇率换算。 */
enum class CurrencyChoice(val id: String, val label: String, val symbol: String) {
    USD("usd", "美元", "$"),
    CNY("cny", "人民币", "¥");

    companion object {
        fun fromId(id: String?): CurrencyChoice = entries.firstOrNull { it.id == id } ?: USD
    }
}

/** Token 数字的单位：英文 K / M / B，或中文 万 / 亿。 */
enum class UnitStyle(val id: String, val label: String) {
    EN("en", "K / M / B"),
    CN("cn", "万 / 亿");

    companion object {
        fun fromId(id: String?): UnitStyle = entries.firstOrNull { it.id == id } ?: EN
    }
}

/** 默认汇率：1 美元 = 7.10 人民币。不联网拉实时汇率，用户可在设置里改。 */
const val DEFAULT_CNY_PER_USD = 7.10
