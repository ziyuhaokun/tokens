package com.ziyuhaokun.tokens.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class ThemeRevealTest {

    @Test
    fun `radius from the centre reaches the corners`() {
        val size = IntSize(1000, 2000)
        val expected = sqrt(500f * 500f + 1000f * 1000f)
        assertEquals(expected, farthestCornerDistance(Offset(500f, 1000f), size), 0.01f)
    }

    @Test
    fun `radius from a corner is the full diagonal`() {
        val size = IntSize(1200, 1600)
        // 从左上角开始，圆要盖满右下角。
        assertEquals(2000f, farthestCornerDistance(Offset.Zero, size), 0.01f)
    }

    @Test
    fun `radius outside the box still covers it`() {
        val size = IntSize(100, 100)
        // 触点在屏幕外（例如分段控件被裁切时算出的坐标）也要能盖满。
        assertEquals(sqrt(200f * 200f + 200f * 200f), farthestCornerDistance(Offset(-100f, -100f), size), 0.01f)
    }

    @Test
    fun `zero size yields zero radius`() {
        assertEquals(0f, farthestCornerDistance(Offset(10f, 10f), IntSize.Zero), 0f)
    }
}
