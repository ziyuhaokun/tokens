package com.ziyuhaokun.tokens.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BlendSeriesTest {

    @Test
    fun `t=1 returns the target, t=0 returns the start`() {
        val to = listOf(4.0, 8.0)
        assertSame(to, blendSeries(listOf(0.0, 0.0), to, 1f))
        assertEquals(listOf(2.0, 6.0), blendSeries(listOf(2.0, 6.0), to, 0f))
    }

    @Test
    fun `same size blends point by point`() {
        assertEquals(listOf(3.0, 6.0), blendSeries(listOf(2.0, 4.0), listOf(4.0, 8.0), 0.5f))
    }

    @Test
    fun `different sizes resample the old series along x`() {
        // 2 点 (0 → 10) 重采样到 5 点：0, 2.5, 5, 7.5, 10；t=0 时应当就是这条线
        val start = blendSeries(listOf(0.0, 10.0), List(5) { 0.0 }, 0f)
        assertEquals(listOf(0.0, 2.5, 5.0, 7.5, 10.0), start)
    }

    @Test
    fun `empty start grows from a flat line, empty target stays empty`() {
        assertEquals(listOf(5.0, 10.0), blendSeries(emptyList(), listOf(10.0, 20.0), 0.5f))
        assertEquals(emptyList<Double>(), blendSeries(listOf(1.0), emptyList(), 0.5f))
    }

    @Test
    fun `non-finite values are treated as zero`() {
        assertEquals(listOf(5.0), blendSeries(listOf(Double.NaN), listOf(10.0), 0.5f))
    }
}
