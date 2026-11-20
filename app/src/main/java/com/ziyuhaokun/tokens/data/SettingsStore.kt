package com.ziyuhaokun.tokens.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val DEFAULT_BASE_URL = "https://vibecafe.ai"

private val Context.tokensDataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

data class StoredSettings(
    val apiKey: String?,
    val baseUrl: String,
    val range: Range,
    val uploadProject: Boolean?,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentChoice = AccentChoice.MONO,
    val fontScale: FontScaleChoice = FontScaleChoice.STANDARD,
    /** false = 精简动态效果（关掉入场与切换动画）。 */
    val motion: Boolean = true,
) {
    val configured: Boolean get() = !apiKey.isNullOrBlank()

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
    }

    // 改主题 / 字号也会让设置流重发；密文没变就别再走一遍 Keystore 解密。
    @Volatile private var lastBlob: String? = null
    @Volatile private var lastKey: String? = null

    private fun decryptCached(blob: String?): String? {
        if (blob == null) return null
        if (blob == lastBlob) return lastKey
        val key = SecretBox.decrypt(blob)
        lastKey = key
        lastBlob = blob
        return key
    }

    val data: Flow<StoredSettings> = context.tokensDataStore.data.map { prefs ->
        StoredSettings(
            apiKey = decryptCached(prefs[Keys.apiKeyBlob]),
            baseUrl = prefs[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            range = Range.fromId(prefs[Keys.rangeId]),
            uploadProject = prefs[Keys.uploadProject],
            themeMode = ThemeMode.fromId(prefs[Keys.themeMode]),
            accent = AccentChoice.fromId(prefs[Keys.accent]),
            fontScale = FontScaleChoice.fromId(prefs[Keys.fontScale]),
            motion = prefs[Keys.motion] ?: true,
        )
    }.flowOn(Dispatchers.Default)

    suspend fun snapshot(): StoredSettings = data.first()

    /** 返回 false 表示 Keystore 加密失败，key 没有落盘。 */
    suspend fun saveCredentials(apiKey: String, baseUrl: String): Boolean {
        val blob = SecretBox.encrypt(apiKey.trim()) ?: return false
        context.tokensDataStore.edit { prefs ->
            prefs[Keys.apiKeyBlob] = blob
            prefs[Keys.baseUrl] = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
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

    suspend fun saveBaseUrl(url: String) {
        context.tokensDataStore.edit { it[Keys.baseUrl] = url.trim().ifBlank { DEFAULT_BASE_URL } }
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

    suspend fun saveUploadProject(value: Boolean?) {
        context.tokensDataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.uploadProject) else prefs[Keys.uploadProject] = value
        }
    }
}
