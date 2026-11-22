package com.ziyuhaokun.tokens.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeTextTest {

    @Test
    fun `system font scale alone triggers large text`() {
        // 系统 130% + App「标准」：最终生效字号 1.3，必须进大字号布局。
        assertTrue(largeText(1.3f))
        assertTrue(extraLargeText(1.3f))
    }

    @Test
    fun `app font scale alone triggers large text`() {
        // 系统 100% + App「大」：旧逻辑同样进大字号布局，不能回归。
        assertTrue(largeText(1.15f))
        assertFalse(extraLargeText(1.15f))
    }

    @Test
    fun `default scale stays compact`() {
        assertFalse(largeText(1.0f))
        assertFalse(extraLargeText(1.0f))
    }

    @Test
    fun `threshold boundary`() {
        assertFalse(largeText(1.05f))
        assertTrue(largeText(1.0501f))
        assertTrue(extraLargeText(1.3f))
        assertFalse(extraLargeText(1.2999f))
    }
}
