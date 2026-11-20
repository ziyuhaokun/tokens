package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class UsageFetchTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `blank key does not hit the network`() = runBlocking {
        val calls = AtomicInteger()
        val coordinator = coordinator(fetch = { calls.incrementAndGet(); usage() })

        val snapshot = coordinator.load(settings(key = "  "), "7d", emptyMap(), zone)

        assertEquals("未配置 API Key", snapshot.error)
        assertEquals(0, calls.get())
    }

    @Test
    fun `success writes the raw json once and drops it from the snapshot`() = runBlocking {
        val raw = """{"buckets":[]}"""
        var written: String? = null
        val coordinator = coordinator(
            fetch = { usage(raw) },
            write = { _, _, json, _ -> written = json },
        )

        val snapshot = coordinator.load(settings(), "7d", mapOf("days" to "7"), zone)

        assertEquals(raw, written)
        assertEquals("", snapshot.usage?.rawJson)
        assertEquals("me", snapshot.usage?.accountHandle)
        assertFalse(snapshot.fromCache)
        assertNull(snapshot.error)
        assertFalse(snapshot.unauthorized)
    }

    @Test
    fun `same scope and cache id share one in flight request`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = coordinator(fetch = {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            usage()
        })

        val first = async { coordinator.load(settings(), "7d", emptyMap(), zone) }
        started.await()
        val second = async { coordinator.load(settings(), "7d", emptyMap(), zone) }
        // 先让第二次调用挂上共享结果，再放行。放早了，第一次已经写完并从 map 里摘掉。
        yield()
        assertEquals(1, calls.get())
        release.complete(Unit)

        val a = first.await()
        val b = second.await()
        assertEquals(1, calls.get())
        assertEquals(a.fetchedAt, b.fetchedAt)
        assertEquals("", a.usage?.rawJson)
    }

    @Test
    fun `different cache ids are not merged`() = runBlocking {
        val calls = AtomicInteger()
        val coordinator = coordinator(fetch = { calls.incrementAndGet(); usage() })

        coordinator.load(settings(), "today", emptyMap(), zone)
        coordinator.load(settings(), "7d", emptyMap(), zone)

        assertEquals(2, calls.get())
    }

    @Test
    fun `a finished request does not suppress the next one`() = runBlocking {
        val calls = AtomicInteger()
        val coordinator = coordinator(fetch = { calls.incrementAndGet(); usage() })

        coordinator.load(settings(), "7d", emptyMap(), zone)
        coordinator.load(settings(), "7d", emptyMap(), zone)

        assertEquals(2, calls.get())
    }

    @Test
    fun `network failure falls back to cache without marking unauthorized`() = runBlocking {
        val coordinator = coordinator(
            fetch = { throw UsageApiException.Network("down") },
            read = { _, _ -> CacheEntry("""{"hasAnyData":true,"account":{"handle":"me"}}""", 123L) },
        )

        val snapshot = coordinator.load(settings(), "7d", emptyMap(), zone)

        assertTrue(snapshot.fromCache)
        assertEquals(123L, snapshot.fetchedAt)
        assertEquals("me", snapshot.usage?.accountHandle)
        assertEquals("", snapshot.usage?.rawJson)
        assertTrue(snapshot.error?.contains("网络错误") == true)
        assertFalse(snapshot.unauthorized)
    }

    @Test
    fun `unauthorized without cache is flagged and not retried by the caller contract`() = runBlocking {
        val coordinator = coordinator(
            fetch = { throw UsageApiException.Unauthorized },
            read = { _, _ -> null },
        )

        val snapshot = coordinator.load(settings(), "7d", emptyMap(), zone)

        assertTrue(snapshot.unauthorized)
        assertNull(snapshot.usage)
        assertFalse(snapshot.fromCache)
        assertEquals("API Key 无效", snapshot.error)
    }

    @Test
    fun `http 429 is unauthorized even when a cache exists`() = runBlocking {
        val coordinator = coordinator(
            fetch = { throw UsageApiException.Http(429, "slow down") },
            read = { _, _ -> CacheEntry("{}", 9L) },
        )

        val snapshot = coordinator.load(settings(), "7d", emptyMap(), zone)

        assertTrue(snapshot.unauthorized)
        assertTrue(snapshot.fromCache)
        assertEquals(9L, snapshot.fetchedAt)
    }

    @Test
    fun `http 500 falls back but stays retryable`() = runBlocking {
        val coordinator = coordinator(
            fetch = { throw UsageApiException.Http(500, "nope") },
            read = { _, _ -> CacheEntry("{}", 4L) },
        )

        val snapshot = coordinator.load(settings(), "7d", emptyMap(), zone)

        assertFalse(snapshot.unauthorized)
        assertTrue(snapshot.fromCache)
        assertTrue(snapshot.error?.contains("500") == true)
    }

    @Test
    fun `broken cache is ignored and the error stands alone`() = runBlocking {
        val coordinator = coordinator(
            fetch = { throw UsageApiException.Network("down") },
            read = { _, _ -> CacheEntry("not-json", 1L) },
        )

        val snapshot = coordinator.load(settings(), "7d", emptyMap(), zone)

        assertNull(snapshot.usage)
        assertFalse(snapshot.fromCache)
        assertFalse(snapshot.unauthorized)
    }

    @Test
    fun `invalidate while the request is in flight skips the cache write`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        val coordinator = coordinator(
            fetch = {
                entered.complete(Unit)
                release.await()
                usage("RAW")
            },
            write = { _, _, _, _ -> writes.incrementAndGet() },
        )

        val job = async { coordinator.load(settings(), "7d", emptyMap(), zone) }
        entered.await()
        coordinator.invalidate()
        release.complete(Unit)

        val snapshot = job.await()
        assertEquals(0, writes.get())
        assertEquals("me", snapshot.usage?.accountHandle)
        assertEquals("", snapshot.usage?.rawJson)
        assertNull(snapshot.error)
    }

    @Test
    fun `cached read drops the raw json`() = runBlocking {
        val coordinator = coordinator(read = { _, _ -> CacheEntry("{}", 5L) })

        val snapshot = coordinator.cached(settings(), "7d", zone)

        assertEquals("", snapshot?.usage?.rawJson)
        assertTrue(snapshot?.fromCache == true)
        assertEquals(5L, snapshot?.fetchedAt)
    }

    private fun settings(key: String = "vbu_test") = StoredSettings(
        apiKey = key,
        baseUrl = "https://vibecafe.ai",
        range = Range.D7,
        uploadProject = null,
    )

    private fun usage(raw: String = """{"ok":true}""") = Usage(
        accountHandle = "me",
        buckets = emptyList(),
        sessions = emptyList(),
        hasAnyData = false,
        names = UsageNames(),
        rawJson = raw,
    )

    private fun coordinator(
        fetch: suspend () -> Usage = { usage() },
        read: suspend (String, String) -> CacheEntry? = { _, _ -> null },
        write: suspend (String, String, String, Long) -> Unit = { _, _, _, _ -> },
    ) = UsageFetchCoordinator(
        fetch = { _, _, _, _ -> fetch() },
        readCache = read,
        writeCache = write,
    )
}
