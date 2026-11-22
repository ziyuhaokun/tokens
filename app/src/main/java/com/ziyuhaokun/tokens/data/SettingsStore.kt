package com.ziyuhaokun.tokens.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val DEFAULT_BASE_URL = "https://vibecafe.ai"

/**
 * 归一化服务器地址：裸域名补 https，去掉 query/fragment 与尾部斜杠。
 * 只放行 https；本机调试地址（localhost / 127.0.0.1 / 10.0.2.2）例外放行 http。
 * 返回 null 表示这个地址不能用——API Key 会被发到那里，不能让它随便是谁。
 */
fun normalizeBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return DEFAULT_BASE_URL
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val parsed = runCatching { withScheme.toHttpUrlOrNull() }.getOrNull() ?: return null
    val host = parsed.host
    val loopback = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
    if (!parsed.isHttps && !loopback) return null
    return parsed.newBuilder()
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')
}

/**
 * 读取设置时把地址和 key 一起决定：地址无效就绝不带着旧 key 换到默认域名。
 * 旧版本可能存过 http:// 的自建地址，升级后归一化会失败；这时 key 必须同步失效。
 */
internal data class ResolvedCredentials(
    val apiKey: String?,
    val baseUrl: String,
    val serverAddressInvalid: Boolean,
)

internal fun resolveStoredCredentials(
    keyBlob: String?,
    rawBaseUrl: String?,
    decrypt: (String) -> String?,
): ResolvedCredentials {
    val normalized = normalizeBaseUrl(rawBaseUrl ?: "")
    if (normalized == null) {
        return ResolvedCredentials(null, DEFAULT_BASE_URL, serverAddressInvalid = true)
    }
    return ResolvedCredentials(keyBlob?.let(decrypt), normalized, serverAddressInvalid = false)
}

/** 汇率允许的范围：防止输错一位数把所有金额放大十倍。 */
val CNY_RATE_RANGE = 1.0..20.0

private val Context.tokensDataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

data class StoredSettings(
    val apiKey: String?,
    val baseUrl: String,
    /** 旧版存下的地址无法归一化；此时 apiKey 被丢弃，必须重新配置。 */
    val serverAddressInvalid: Boolean = false,
    val range: Range,
    val uploadProject: Boolean?,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentChoice = AccentChoice.MONO,
    val fontScale: FontScaleChoice = FontScaleChoice.STANDARD,
    /** false = 精简动态效果（关掉入场与切换动画）。 */
    val motion: Boolean = true,
    val currency: CurrencyChoice = CurrencyChoice.USD,
    /** 1 美元折多少人民币；只在 currency = CNY 时用到。 */
    val cnyPerUsd: Double = DEFAULT_CNY_PER_USD,
    val units: UnitStyle = UnitStyle.EN,
    val receiptStyle: ReceiptStyle = ReceiptStyle.CLASSIC,
    val receiptLanguage: ReceiptLanguage = ReceiptLanguage.MIXED,
    /** 上次成功查询 GitHub 新版本的时间（毫秒）；自动检查据此限频。 */
    val lastUpdateCheck: Long = 0L,
    /** 用户点了「跳过此版本」的 tag；之后不再自动弹窗，更高的版本不受影响。 */
    val skippedVersion: String? = null,
) {
    val configured: Boolean get() = !apiKey.isNullOrBlank()

    /** 按当前设置显示金额与 token 数。 */
    val numberFormats: NumberFormats get() = NumberFormats(currency, cnyPerUsd, units)

    /** 缓存作用域：没配 key 时为 null，此时不读不写任何缓存。 */
    val cacheScope: String? by lazy {
        apiKey?.takeIf { it.isNotBlank() }?.let { UsageCache.scopeOf(baseUrl, it) }
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val apiKeyBlob = stringPreferencesKey("api_key_blob")
        val baseUrl = stringPreferencesKey("base_url")
        val rangeId = stringPreferencesKey("range_id")
        val uploadProject = booleanPreferencesKey("upload_project")
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val fontScale = stringPreferencesKey("font_scale")
        val motion = booleanPreferencesKey("motion")
        val currency = stringPreferencesKey("currency")
        val cnyPerUsd = doublePreferencesKey("cny_per_usd")
        val units = stringPreferencesKey("units")
        val receiptStyle = stringPreferencesKey("receipt_style")
        val receiptLanguage = stringPreferencesKey("receipt_language")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedVersion = stringPreferencesKey("skipped_version")
    }

    /**
     * 主题的同步镜像。DataStore 是异步的，冷启动第一帧还拿不到设置，会先按系统主题画一帧，
     * 强制深色 / 浅色的用户就会看到一下闪。这里用一个很小的 SharedPreferences 存上次的值，第一帧就能同步读到。
     * 设置流每次发值都会同步一次，所以老用户升级后第一次启动就会自动补上。
     */
    private val bootPrefs by lazy { context.getSharedPreferences("boot", Context.MODE_PRIVATE) }

    fun bootThemeMode(): ThemeMode = ThemeMode.fromId(bootPrefs.getString("theme_mode", null))

    private fun mirrorTheme(mode: ThemeMode) {
        if (bootPrefs.getString("theme_mode", null) != mode.id) {
            bootPrefs.edit().putString("theme_mode", mode.id).apply()
        }
    }

    // 改主题 / 字号也会让设置流重发；密文没变就别再走一遍 Keystore 解密。
    // 单个 volatile 保存 (blob, key) 对：两个独立字段在换账号时会出现「新 blob + 旧 key」。
    @Volatile private var cachedDecrypt: Pair<String, String?>? = null

    private fun decryptCached(blob: String?): String? {
        if (blob == null) return null
        cachedDecrypt?.let { if (it.first == blob) return it.second }
        val key = SecretBox.decrypt(blob)
        // 只记住解密成功的结果。Keystore 偶发失败时如果把 null 也记下来，同一份密文会一直被当成「未配置」，
        // 直到重启 App 才恢复；不记的话下一次设置流发值时会自动再试。
        if (key != null) cachedDecrypt = blob to key
        return key
    }

    val data: Flow<StoredSettings> = context.tokensDataStore.data
        // prefs 文件损坏 / IO 失败时退回空设置（引导页），不要抛未捕获异常崩掉应用。
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        // 旧版本写进来的 http:// 地址在读取时也要拦住：地址无效就连 key 一起失效，
        // 不能因为「存的时候没问题」就带着旧 key 换到默认域名。
        val credentials = resolveStoredCredentials(
            keyBlob = prefs[Keys.apiKeyBlob],
            rawBaseUrl = prefs[Keys.baseUrl],
            decrypt = ::decryptCached,
        )
        StoredSettings(
            apiKey = credentials.apiKey,
            baseUrl = credentials.baseUrl,
            serverAddressInvalid = credentials.serverAddressInvalid,
            range = Range.fromId(prefs[Keys.rangeId]),
            uploadProject = prefs[Keys.uploadProject],
            themeMode = ThemeMode.fromId(prefs[Keys.themeMode]),
            accent = AccentChoice.fromId(prefs[Keys.accent]),
            fontScale = FontScaleChoice.fromId(prefs[Keys.fontScale]),
            motion = prefs[Keys.motion] ?: true,
            currency = CurrencyChoice.fromId(prefs[Keys.currency]),
            cnyPerUsd = prefs[Keys.cnyPerUsd]?.takeIf { it in CNY_RATE_RANGE } ?: DEFAULT_CNY_PER_USD,
            units = UnitStyle.fromId(prefs[Keys.units]),
            receiptStyle = ReceiptStyle.fromId(prefs[Keys.receiptStyle]),
            receiptLanguage = ReceiptLanguage.fromId(prefs[Keys.receiptLanguage]),
            lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: 0L,
            skippedVersion = prefs[Keys.skippedVersion],
        ).also { mirrorTheme(it.themeMode) }
    }.flowOn(Dispatchers.Default)

    suspend fun snapshot(): StoredSettings = data.first()

    /**
     * 返回 false 表示没有落盘（Keystore 加密失败，或服务器地址不合法）。
     * 地址必须先过 [normalizeBaseUrl]：key 是跟着这个地址发出去的。
     */
    suspend fun saveCredentials(apiKey: String, baseUrl: String): Boolean {
        val url = normalizeBaseUrl(baseUrl) ?: return false
        // Keystore 加密（首次还要现场生成密钥）可能要几百毫秒，不能在调用方（主线程）上做。
        val blob = withContext(Dispatchers.Default) { SecretBox.encrypt(apiKey.trim()) } ?: return false
        context.tokensDataStore.edit { prefs ->
            prefs[Keys.apiKeyBlob] = blob
            prefs[Keys.baseUrl] = url
        }
        return true
    }

    suspend fun clearCredentials() {
        context.tokensDataStore.edit { prefs ->
            prefs.remove(Keys.apiKeyBlob)
            prefs.remove(Keys.uploadProject)
        }
    }

    suspend fun saveRange(range: Range) {
        context.tokensDataStore.edit { it[Keys.rangeId] = range.id }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.tokensDataStore.edit { it[Keys.themeMode] = mode.id }
    }

    suspend fun saveAccent(accent: AccentChoice) {
        context.tokensDataStore.edit { it[Keys.accent] = accent.id }
    }

    suspend fun saveFontScale(choice: FontScaleChoice) {
        context.tokensDataStore.edit { it[Keys.fontScale] = choice.id }
    }

    suspend fun saveMotion(enabled: Boolean) {
        context.tokensDataStore.edit { it[Keys.motion] = enabled }
    }

    suspend fun saveCurrency(choice: CurrencyChoice) {
        context.tokensDataStore.edit { it[Keys.currency] = choice.id }
    }

    /** 汇率超出合理范围（例如手滑输成 71）就不存，返回 false。 */
    suspend fun saveCnyPerUsd(rate: Double): Boolean {
        if (rate !in CNY_RATE_RANGE) return false
        context.tokensDataStore.edit { it[Keys.cnyPerUsd] = rate }
        return true
    }

    suspend fun saveUnits(style: UnitStyle) {
        context.tokensDataStore.edit { it[Keys.units] = style.id }
    }

    suspend fun saveReceiptStyle(style: ReceiptStyle) {
        context.tokensDataStore.edit { it[Keys.receiptStyle] = style.id }
    }

    suspend fun saveReceiptLanguage(language: ReceiptLanguage) {
        context.tokensDataStore.edit { it[Keys.receiptLanguage] = language.id }
    }

    suspend fun saveLastUpdateCheck(millis: Long) {
        context.tokensDataStore.edit { it[Keys.lastUpdateCheck] = millis }
    }

    suspend fun saveSkippedVersion(tag: String) {
        context.tokensDataStore.edit { it[Keys.skippedVersion] = tag }
    }
}
