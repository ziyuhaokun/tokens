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
