package com.ziyuhaokun.tokens.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val DEFAULT_BASE_URL = "https://vibecafe.ai"

private val Context.tokensDataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

// 缓存单独一个 DataStore：设置流只关心身份/范围变化，缓存写入不应该触发它。
private val Context.tokensCacheStore: DataStore<Preferences> by preferencesDataStore(name = "tokens-cache")

data class StoredSettings(
    val apiKey: String?,
    val baseUrl: String,
    val range: Range,
    val uploadProject: Boolean?,
) {
    val configured: Boolean get() = !apiKey.isNullOrBlank()
}

data class CacheEntry(val json: String, val fetchedAt: Long)

class SettingsStore(private val context: Context) {

    private object Keys {
        val apiKeyBlob = stringPreferencesKey("api_key_blob")
        val baseUrl = stringPreferencesKey("base_url")
        val rangeId = stringPreferencesKey("range_id")
        val uploadProject = booleanPreferencesKey("upload_project")
        fun cacheJson(id: String) = stringPreferencesKey("cache_json_$id")
        fun cacheAt(id: String) = longPreferencesKey("cache_at_$id")
    }

    val data: Flow<StoredSettings> = context.tokensDataStore.data.map { prefs ->
        StoredSettings(
            apiKey = prefs[Keys.apiKeyBlob]?.let { SecretBox.decrypt(it) },
            baseUrl = prefs[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            range = Range.fromId(prefs[Keys.rangeId]),
            uploadProject = prefs[Keys.uploadProject],
        )
    }

    suspend fun snapshot(): StoredSettings = data.first()

    suspend fun saveCredentials(apiKey: String, baseUrl: String) {
        val blob = SecretBox.encrypt(apiKey.trim())
        context.tokensDataStore.edit { prefs ->
            if (blob != null) prefs[Keys.apiKeyBlob] = blob else prefs.remove(Keys.apiKeyBlob)
            prefs[Keys.baseUrl] = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
        }
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

    suspend fun saveUploadProject(value: Boolean?) {
        context.tokensDataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.uploadProject) else prefs[Keys.uploadProject] = value
        }
    }

    suspend fun writeCache(range: Range, json: String, fetchedAt: Long) = writeCache(range.id, json, fetchedAt)

    /** 按任意 key 缓存：热力图的整年数据不是五个 Range 之一。 */
    suspend fun writeCache(id: String, json: String, fetchedAt: Long) {
        context.tokensCacheStore.edit { prefs ->
            prefs[Keys.cacheJson(id)] = json
            prefs[Keys.cacheAt(id)] = fetchedAt
        }
    }

    suspend fun readCache(range: Range): CacheEntry? = readCache(range.id)

    suspend fun readCache(id: String): CacheEntry? {
        val prefs = context.tokensCacheStore.data.first()
        val json = prefs[Keys.cacheJson(id)] ?: return null
        return CacheEntry(json, prefs[Keys.cacheAt(id)] ?: 0L)
    }
}
