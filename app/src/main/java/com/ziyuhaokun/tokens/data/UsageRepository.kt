package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class Snapshot(
    val usage: Usage?,
    val fetchedAt: Long?,
    val error: String?,
    val fromCache: Boolean,
    /** 401：key 失效，后台任务不该再重试。 */
    val unauthorized: Boolean = false,
)

/** 小组件 / 后台刷新的结果，Worker 据此决定 success 还是 retry。 */
enum class RefreshOutcome { OK, NOT_CONFIGURED, UNAUTHORIZED, FAILED }

class UsageRepository(
    private val api: UsageApi,
    private val store: SettingsStore,
    private val cache: UsageCache,
) {
    val settings: Flow<StoredSettings> = store.data

    /** 缓存一有写入 / 清空就变，小组件用它触发重读。 */
    val cacheVersion: Flow<Long> = cache.version

    suspend fun snapshot(): StoredSettings = store.snapshot()

    /**
     * 先验证再保存：key 不通就不落盘，引导页 / 设置页能看到原因。
     * [apiKey] 为空时沿用已保存的 key（只改服务器地址的场景）。
     * 返回 null 表示成功，否则是给用户看的错误文案。
     */
    suspend fun verifyAndSaveCredentials(apiKey: String, baseUrl: String): String? {
        val url = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
        val key = apiKey.trim().ifBlank { store.snapshot().apiKey.orEmpty() }
        if (key.isBlank()) return "请输入 API Key"
        try {
            api.verify(url, key)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: UsageApiException) {
            return e.message ?: "验证失败"
        } catch (e: Exception) {
            return e.message ?: "验证失败"
        }
        return if (store.saveCredentials(key, url)) null else "Key 加密保存失败（Keystore 不可用）"
    }

    /** 退出：清 key + 清掉全部缓存（不留上一个账号的数字）。 */
    suspend fun signOut() {
        store.clearCredentials()
        cache.clearAll()
    }

    suspend fun saveRange(range: Range) = store.saveRange(range)

    suspend fun saveThemeMode(mode: ThemeMode) = store.saveThemeMode(mode)
    suspend fun saveAccent(accent: AccentChoice) = store.saveAccent(accent)
    suspend fun saveFontScale(choice: FontScaleChoice) = store.saveFontScale(choice)
    suspend fun saveMotion(enabled: Boolean) = store.saveMotion(enabled)
    suspend fun clearCache() = cache.clearAll()

    /** 先网络、失败回退缓存：网络失败也能看到上次的数据 + 时间戳。 */
    suspend fun load(range: Range, settings: StoredSettings): Snapshot =
        fetchWithFallback(settings, range.id, range.params())

    /** 只读缓存，不发请求。没配 key 或没有缓存时返回 null。 */
    suspend fun cached(range: Range, settings: StoredSettings): Snapshot? =
        cachedSnapshot(settings, range.id)

    /**
     * 整年热力图：一次拿完，不分段。服务端接受任意 from/to 区间
     * （实测 365 天 ~1.5MB / 600ms），所以不需要拉 4 次 90 天再拼。
     */
    suspend fun loadYear(settings: StoredSettings, zone: ZoneId = ZoneId.systemDefault()): Snapshot {
        val today = LocalDate.now(zone)
        val params = mapOf(
            "from" to today.minusDays((YearAgg.DAYS - 1).toLong()).toString(),
            "to" to today.toString(),
        )
        return fetchWithFallback(settings, YEAR_CACHE_KEY, params, zone)
    }

    suspend fun cachedYear(settings: StoredSettings, zone: ZoneId = ZoneId.systemDefault()): Snapshot? =
        cachedSnapshot(settings, YEAR_CACHE_KEY, zone)

    /** 小组件用的两份数据：今天 + 7 天。一个失败不影响另一个。 */
    suspend fun refreshForWidget(): RefreshOutcome {
        val settings = store.snapshot()
        if (!settings.configured) return RefreshOutcome.NOT_CONFIGURED
        val results = listOf(Range.TODAY, Range.D7).map { load(it, settings) }
        return when {
            results.any { it.error == null } -> RefreshOutcome.OK
            results.any { it.unauthorized } -> RefreshOutcome.UNAUTHORIZED
            else -> RefreshOutcome.FAILED
        }
    }

    /** 小组件只读缓存：没配 key 时一律当作没有数据，不显示上一个账号的数字。 */
    suspend fun readCacheRaw(range: Range, settings: StoredSettings): CacheEntry? {
        val scope = settings.cacheScope ?: return null
        return cache.read(scope, range.id)
    }

    private suspend fun fetchWithFallback(
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

        return try {
            val usage = api.fetch(settings.baseUrl, key, params, zone)
            val at = System.currentTimeMillis()
            cache.write(scope, cacheId, usage.rawJson, at)
            Snapshot(usage, at, null, false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 网络失败才去解析缓存：成功路径上不白白多解析一份几百 KB 的 JSON。
            val fallback = cachedSnapshot(settings, cacheId, zone)
            Snapshot(
                usage = fallback?.usage,
                fetchedAt = fallback?.fetchedAt,
                error = e.message ?: "未知错误",
                fromCache = fallback != null,
                unauthorized = e is UsageApiException.Unauthorized,
            )
        }
    }

    private suspend fun cachedSnapshot(
        settings: StoredSettings,
        cacheId: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Snapshot? {
        val scope = settings.cacheScope ?: return null
        val entry = cache.read(scope, cacheId) ?: return null
        val usage = withContext(Dispatchers.Default) {
            runCatching { Usage.parse(entry.json, zone) }.getOrNull()
        } ?: return null
        return Snapshot(usage, entry.fetchedAt, null, true)
    }

    companion object {
        const val YEAR_CACHE_KEY = "year"
    }
}
