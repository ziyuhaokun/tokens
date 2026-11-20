package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

data class Snapshot(
    val usage: Usage?,
    val fetchedAt: Long?,
    val error: String?,
    val fromCache: Boolean,
)

class UsageRepository(
    private val api: UsageApi,
    private val store: SettingsStore,
) {
    val settings: Flow<StoredSettings> = store.data

    suspend fun snapshot(): StoredSettings = store.snapshot()

    suspend fun saveCredentials(apiKey: String, baseUrl: String) = store.saveCredentials(apiKey, baseUrl)

    suspend fun clearCredentials() = store.clearCredentials()

    suspend fun saveRange(range: Range) = store.saveRange(range)
    suspend fun saveBaseUrl(url: String) = store.saveBaseUrl(url)

    suspend fun saveUploadProject(value: Boolean?) = store.saveUploadProject(value)

    /** 先缓存后网络：有缓存立刻可用，网络失败也能看到上次的数据 + 时间戳。 */
    suspend fun load(range: Range, settings: StoredSettings): Snapshot {
        val cache = store.readCache(range)
        val cachedUsage = cache?.let { entry -> runCatching { Usage.parse(entry.json) }.getOrNull() }

        val key = settings.apiKey
        if (key.isNullOrBlank()) {
            return Snapshot(cachedUsage, cache?.fetchedAt, "未配置 API Key", cachedUsage != null)
        }

        return try {
            val usage = api.fetch(settings.baseUrl, key, range.params())
            val at = System.currentTimeMillis()
            store.writeCache(range, usage.rawJson, at)
            settings.uploadProject?.let { /* 服务端设置项只用于展示，不在这里改 */ }
            Snapshot(usage, at, null, false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: UsageApiException) {
            Snapshot(cachedUsage, cache?.fetchedAt, e.message, cachedUsage != null)
        } catch (e: Exception) {
            Snapshot(cachedUsage, cache?.fetchedAt, e.message ?: "未知错误", cachedUsage != null)
        }
    }

    suspend fun cached(range: Range): Snapshot? {
        val cache = store.readCache(range) ?: return null
        val usage = runCatching { Usage.parse(cache.json) }.getOrNull() ?: return null
        return Snapshot(usage, cache.fetchedAt, null, true)
    }

    /**
     * 整年热力图：一次拿完，不分段。服务端接受任意 from/to 区间
     * （实测 365 天 ~1.5MB / 600ms），所以不需要拉 4 次 90 天再拼。
     */
    suspend fun loadYear(settings: StoredSettings, zone: ZoneId = ZoneId.systemDefault()): Snapshot {
        val today = LocalDate.now(zone)
        val cache = store.readCache(YEAR_CACHE_KEY)
        val cachedUsage = cache?.let { runCatching { Usage.parse(it.json, zone) }.getOrNull() }

        val key = settings.apiKey
        if (key.isNullOrBlank()) {
            return Snapshot(cachedUsage, cache?.fetchedAt, "未配置 API Key", cachedUsage != null)
        }

        val params = mapOf(
            "from" to today.minusDays((YearAgg.DAYS - 1).toLong()).toString(),
            "to" to today.toString(),
        )
        return try {
            val usage = api.fetch(settings.baseUrl, key, params, zone)
            val at = System.currentTimeMillis()
            store.writeCache(YEAR_CACHE_KEY, usage.rawJson, at)
            Snapshot(usage, at, null, false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: UsageApiException) {
            Snapshot(cachedUsage, cache?.fetchedAt, e.message, cachedUsage != null)
        } catch (e: Exception) {
            Snapshot(cachedUsage, cache?.fetchedAt, e.message ?: "未知错误", cachedUsage != null)
        }
    }

    companion object {
        const val YEAR_CACHE_KEY = "year"
    }
}
