package com.ziyuhaokun.tokens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class UsageModelsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `parse tolerates a completely empty payload`() {
        val usage = Usage.parse("{}", zone)

        assertNull(usage.accountHandle)
        assertTrue(usage.buckets.isEmpty())
        assertTrue(usage.sessions.isEmpty())
        assertFalse(usage.hasAnyData)
        assertEquals("{}", usage.rawJson)
    }

    @Test
    fun `parse fills bucket defaults when fields are missing`() {
        val usage = Usage.parse("""{"buckets":[{}]}""", zone)
        val bucket = usage.buckets.single()

        assertEquals(1, usage.buckets.size)
        assertEquals("unknown", bucket.source)
        assertEquals("unknown", bucket.model)
        assertEquals("unknown", bucket.project)
        assertEquals("", bucket.hostname)
        assertEquals(0L, bucket.bucketStartMillis)
        assertEquals(0L, bucket.tokens)
        assertEquals(0.0, bucket.estimatedCost, 0.0)
    }

    @Test
    fun `parse fills session defaults when fields are missing`() {
        val usage = Usage.parse("""{"sessions":[{}]}""", zone)
        val session = usage.sessions.single()

        assertEquals("", session.source)
        assertEquals("unknown", session.project)
        assertEquals(0L, session.activeSeconds)
        assertEquals(0L, session.startMillis)
        assertEquals(0L, session.messageCount)
    }

    @Test
    fun `parse reads session message count`() {
        val json = """{"sessions":[{"messageCount":42},{"messageCount":"7"},{}]}"""

        val counts = Usage.parse(json, zone).sessions.map { it.messageCount }

        assertEquals(listOf(42L, 7L, 0L), counts)
    }

    @Test
    fun `bucket tokens adds every token field`() {
        val json = """
            {"buckets":[{
              "bucketStart":"2026-11-20T09:55:00.000Z",
              "inputTokens":1000,
              "outputTokens":200,
              "cachedInputTokens":300,
              "reasoningOutputTokens":40,
              "cacheCreation5mTokens":5,
              "cacheCreation1hTokens":6,
              "estimatedCost":1.25
            }]}
        """.trimIndent()

        val bucket = Usage.parse(json, zone).buckets.single()

        assertEquals(1551L, bucket.tokens)
        assertEquals(1000L, bucket.inputTokens)
        assertEquals(1.25, bucket.estimatedCost, 0.0)
        assertEquals(Instant.parse("2026-11-20T09:55:00Z").toEpochMilli(), bucket.bucketStartMillis)
    }

    @Test
    fun `parse reads account handle, names and session start`() {
        val json = """
            {
              "account":{"name":"ziyuhaokun"},
              "hasAnyData":true,
              "names":{"sources":{"cli":"Claude Code"},"models":{"claude-sonnet":"Sonnet"}},
              "sessions":[{"source":"cli","project":"tokens","activeSeconds":600,
                           "firstMessageAt":"2026-11-20T01:00:00.000Z"}]
            }
        """.trimIndent()

        val usage = Usage.parse(json, zone)

        assertEquals("ziyuhaokun", usage.accountHandle)
        assertTrue(usage.hasAnyData)
        assertEquals("Claude Code", usage.sourceName("cli"))
        assertEquals("Sonnet", usage.modelName("claude-sonnet"))
        assertEquals("unknown-id", usage.sourceName("unknown-id"))
        assertEquals(600L, usage.sessions.single().activeSeconds)
        assertEquals(Instant.parse("2026-11-20T01:00:00Z").toEpochMilli(), usage.sessions.single().startMillis)
    }

    @Test
    fun `parse accepts an offset timestamp as the same instant`() {
        val utc = Usage.parse("""{"buckets":[{"bucketStart":"2026-11-20T09:55:00.000Z"}]}""", zone)
        val offset = Usage.parse("""{"buckets":[{"bucketStart":"2026-11-20T17:55:00+08:00"}]}""", zone)

        assertEquals(utc.buckets.single().bucketStartMillis, offset.buckets.single().bucketStartMillis)
    }

    @Test
    fun `parse keeps buckets even when a timestamp is unparsable`() {
        val usage = Usage.parse("""{"buckets":[{"bucketStart":"nope","inputTokens":5},{"inputTokens":7}]}""", zone)

        assertEquals(2, usage.buckets.size)
        assertEquals(0L, usage.buckets.first().bucketStartMillis)
        assertEquals(5L, usage.buckets.first().tokens)
        assertEquals(7L, usage.buckets.last().tokens)
    }
}
