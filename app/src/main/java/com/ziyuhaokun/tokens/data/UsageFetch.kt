package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * 同一账号、同一缓存档的在途请求合并成一次网络调用。
 *
 * 退出登录 / 换账号会 [invalidate]：正在飞的旧请求还可以把结果交还给当时的调用方，
 * 但写缓存必须和作废共用一把锁，避免「清完缓存又被旧请求写回来」。
 */
internal class UsageFetchCoordinator(
    private val fetch: suspend (baseUrl: String, apiKey: String, params: Map<String, String>, zone: ZoneId) -> Usage,
    private val readCache: suspend (scope: String, cacheId: String) -> CacheEntry?,
    private val writeCache: suspend (scope: String, cacheId: String, json: String, fetchedAt: Long) -> Unit,
) {
    private val gate = Mutex()
    private var generation = 0
    private val flights = ConcurrentHashMap<String, Deferred<Snapshot>>()

    /** 独立于调用方：首页取消时，小组件还在等的那一次请求不能被一起取消。 */
    private val flightScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun invalidate() {
        gate.withLock { generation++ }
    }

    suspend fun load(
        settings: StoredSettings,
        cacheId: String,
        params: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Snapshot {
        val key = settings.apiKey
        val scope = settings.cacheScope
        if (key.isNullOrBlank() || scope == null) {
            return Snapshot(null, null, "未配置 API Key", false)
        }
        return singleFlight("$scope|$cacheId") {
            fetchOne(settings.baseUrl, key, scope, cacheId, params, zone)
        }
    }

    /** 只读缓存，不进在途合并。交给界面的 [Usage] 不带原始 JSON。 */
    suspend fun cached(
        settings: StoredSettings,
        cacheId: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Snapshot? {
        val scope = settings.cacheScope ?: return null
        return readParsed(scope, cacheId, zone)
    }

    private suspend fun fetchOne(
        baseUrl: String,
        apiKey: String,
        scope: String,
        cacheId: String,
        params: Map<String, String>,
        zone: ZoneId,
    ): Snapshot {
        val seen = gate.withLock { generation }
        return try {
            val usage = fetch(baseUrl, apiKey, params, zone)
            val at = System.currentTimeMillis()
            // 和 invalidate 互斥：要么写在作废之前（随后 clearAll 会删掉），要么直接不写。
            gate.withLock {
                if (generation == seen) writeCache(scope, cacheId, usage.rawJson, at)
            }
            Snapshot(usage.withoutRawJson(), at, null, false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallback = readParsed(scope, cacheId, zone)
            Snapshot(
                usage = fallback?.usage,
                fetchedAt = fallback?.fetchedAt,
                error = e.message ?: "未知错误",
                fromCache = fallback != null,
                // 429 没有官方 Retry-After 解析需求：交给下一个 30 分钟周期即可，不要指数退避无限重试。
                unauthorized = e is UsageApiException.Unauthorized ||
                    (e is UsageApiException.Http && e.code == 429),
            )
        }
    }

    private suspend fun readParsed(scope: String, cacheId: String, zone: ZoneId): Snapshot? {
        val entry = readCache(scope, cacheId) ?: return null
        val usage = withContext(Dispatchers.Default) {
            runCatching { Usage.parse(entry.json, zone).withoutRawJson() }.getOrNull()
        } ?: return null
        return Snapshot(usage, entry.fetchedAt, null, true)
    }

    /**
     * 先占坑再发请求。坑被别人占了就取消自己这次、改等对方，
     * 避免 async 先跑起来、put 失败时网络已经发出去。
     */
    private suspend fun singleFlight(key: String, block: suspend () -> Snapshot): Snapshot {
        while (true) {
            flights[key]?.let { return it.await() }
            val created = CompletableDeferred<Snapshot>()
            if (flights.putIfAbsent(key, created) != null) continue
            flightScope.launch {
                try {
                    created.complete(block())
                } catch (t: Throwable) {
                    created.completeExceptionally(t)
                } finally {
                    flights.remove(key, created)
                }
            }
            return created.await()
        }
    }
}

/** 原始 JSON 只在写缓存时需要，界面状态里留着它等于把整年响应再放一份。 */
private fun Usage.withoutRawJson(): Usage = if (rawJson.isEmpty()) this else copy(rawJson = "")
