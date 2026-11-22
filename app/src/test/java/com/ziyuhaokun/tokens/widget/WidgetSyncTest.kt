package com.ziyuhaokun.tokens.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSyncTest {

    @Test
    fun `throttle uses injected clock and respects boundary`() {
        assertTrue(shouldThrottleRefresh(now = 60_000L, last = 1_000L, minIntervalMillis = 60_000L))
        assertTrue(shouldThrottleRefresh(now = 59_999L, last = 0L, minIntervalMillis = 60_000L))
        assertFalse(shouldThrottleRefresh(now = 61_000L, last = 1_000L, minIntervalMillis = 60_000L))
    }

    @Test
    fun `worker refresh with zero interval is never throttled`() {
        assertFalse(shouldThrottleRefresh(now = 1_000L, last = 1_000L, minIntervalMillis = 0L))
        assertFalse(shouldThrottleRefresh(now = 1_000L, last = 0L, minIntervalMillis = 0L))
    }
}
