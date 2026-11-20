package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

data class Snapshot(
    val usage: Usage?,
    val fetchedAt: Long?,
    val error: String?,
    val fromCache: Boolean,
    /** 401 / 429：key 失效或被限流，后台任务不该再重试。 */
    val unauthorized: Boolean = false,
)

/** 小组件 / 后台刷新的结果，Worker 据此决定 success 还是 retry。 */
enum class RefreshOutcome { OK, NOT_CONFIGURED, UNAUTHORIZED, FAILED }

class UsageRepository(
    private val api: UsageApi,
    private val store: SettingsStore,
    private val cache: UsageCache,
    private val deviceLogin: DeviceLoginApi = DeviceLoginApi(),
) {
    private val fetches = UsageFetchCoordinator(
        fetch = api::fetch,
        readCache = cache::read,
        writeCache = cache::write,
    )

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
        // 校验下沉到 repository：任何调用方传进来的地址都要过这一关，不能只靠 UI 自觉。
        val url = normalizeBaseUrl(baseUrl)
            ?: return "服务器地址无效：请输入 https 地址（本机调试可用 http://localhost）"
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
        // 换账号/改地址后，之前带着旧凭据在飞的请求一律不许再落缓存。
        fetches.invalidate()
        if (!store.saveCredentials(key, url)) return "Key 加密保存失败（Keystore 不可用）"
        // 换了账号 / key：把旧账号留在磁盘上的缓存清掉。
        cache.retainOnly(UsageCache.scopeOf(url, key))
        return null
    }

    /** 浏览器登录第一步：拿验证码和确认链接。 */
    suspend fun requestDeviceLogin(baseUrl: String): DeviceLoginRequest =
        deviceLogin.request(baseUrl, DeviceLoginApi.CLIENT_NAME, DeviceLoginApi.hostnameOf())

    suspend fun pollDeviceLogin(baseUrl: String, deviceCode: String): DevicePollResult =
        deviceLogin.poll(baseUrl, deviceCode)

    /** 退出：清 key + 清掉全部缓存（不留上一个账号的数字）。 */
    suspend fun signOut() {
        fetches.invalidate()
        store.clearCredentials()
        cache.clearAll()
    }

    /** 当前时间范围会影响下次拉哪一档，留在用量仓库里。外观设置直接走 [SettingsStore]。 */
    suspend fun saveRange(range: Range) = store.saveRange(range)

    suspend fun clearCache() = cache.clearAll()

    /** 先网络、失败回退缓存：网络失败也能看到上次的数据 + 时间戳。 */
    suspend fun load(range: Range, settings: StoredSettings): Snapshot =
        fetches.load(settings, range.cacheId, range.params())

    /** 只读缓存，不发请求。没配 key 或没有缓存时返回 null。 */
    suspend fun cached(range: Range, settings: StoredSettings): Snapshot? =
        fetches.cached(settings, range.cacheId)

    /**
     * 整年热力图：一次拿完，不分段。服务端接受任意 from/to 区间
     * （实测 365 天 ~1.5MB / 600ms），所以不需要拉 4 次 90 天再拼。
     */
    suspend fun loadYear(settings: StoredSettings, zone: ZoneId = ZoneId.systemDefault()): Snapshot {
        // 和首页 365D 用同一段区间、同一份缓存（Range.D365.cacheId == YEAR_CACHE_KEY）。
        val params = Range.dateWindow(YearAgg.DAYS, zone)
        return fetches.load(settings, YEAR_CACHE_KEY, params, zone)
    }

    suspend fun cachedYear(settings: StoredSettings, zone: ZoneId = ZoneId.systemDefault()): Snapshot? =
        fetches.cached(settings, YEAR_CACHE_KEY, zone)

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
        return cache.read(scope, range.cacheId)
    }

    companion object {
        const val YEAR_CACHE_KEY = "year"
    }
}
