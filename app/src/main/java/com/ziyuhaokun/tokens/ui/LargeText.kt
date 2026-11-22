package com.ziyuhaokun.tokens.ui

/** 生效字号超过这个倍率就把多列改成单列。 */
internal const val LARGE_TEXT_THRESHOLD = 1.05f

/** 生效字号超过这个倍率就把大金额再缩一档，避免换行。 */
internal const val EXTRA_LARGE_TEXT_THRESHOLD = 1.3f

/**
 * 按最终生效字号判断，而不是只看 App 内的倍率。
 * 调用方传的是 Density.fontScale = 系统 fontScale × App 字号倍率。
 */
internal fun largeText(effectiveFontScale: Float): Boolean =
    effectiveFontScale > LARGE_TEXT_THRESHOLD

internal fun extraLargeText(effectiveFontScale: Float): Boolean =
    effectiveFontScale >= EXTRA_LARGE_TEXT_THRESHOLD
