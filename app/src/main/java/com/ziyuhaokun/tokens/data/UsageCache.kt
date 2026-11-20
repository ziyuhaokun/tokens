package com.ziyuhaokun.tokens.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class CacheEntry(val json: String, val fetchedAt: Long)

/**
 * 原样缓存 /api/usage 的响应，一个范围一个文件。
 *
 * 为什么不放 DataStore：整年数据 ~1.5MB，Preferences DataStore 每写一次都要整份重写、
 * 读的时候整份进内存。文件名带账号作用域（baseUrl + key 的哈希），换账号不会串到旧数据。
 *
 * 文件格式：第一行 fetchedAt（毫秒），之后是原始 JSON。
 */
class UsageCache(context: Context) {

    private val dir = File(context.noBackupFilesDir, "usage-cache")
    private val legacyStore = File(context.filesDir, "datastore/tokens-cache.preferences_pb")
    private val lock = Mutex()
    private val _version = MutableStateFlow(0L)

    /** 每次写入 / 清空都 +1，小组件靠它知道该重读了。 */
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun read(scope: String, id: String): CacheEntry? = withContext(Dispatchers.IO) {
        val file = fileOf(scope, id)
        if (!file.isFile) return@withContext null
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            val newline = text.indexOf('\n')
            if (newline <= 0) return@runCatching null
            val at = text.substring(0, newline).trim().toLongOrNull() ?: return@runCatching null
            CacheEntry(text.substring(newline + 1), at)
        }.getOrNull()
    }

    suspend fun write(scope: String, id: String, json: String, fetchedAt: Long) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                dir.mkdirs()
                val target = fileOf(scope, id)
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeText("$fetchedAt\n$json", Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }
        }
        _version.value += 1
    }

    /** 清掉所有账号、所有范围的缓存。 */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                dir.listFiles()?.forEach { it.delete() }
                legacyStore.delete()
            }
        }
        _version.value += 1
    }

    /** 旧版本把缓存放在 DataStore 里，迁到文件后那份已经没用，启动时删掉。 */
    suspend fun dropLegacy() {
        withContext(Dispatchers.IO) { if (legacyStore.exists()) legacyStore.delete() }
    }

    private fun fileOf(scope: String, id: String): File {
        val safeId = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$scope-$safeId.json")
    }

    companion object {
        /** 账号作用域：同一个服务器 + 同一个 key 才算同一份缓存。 */
        fun scopeOf(baseUrl: String, apiKey: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${baseUrl.trimEnd('/')}\n$apiKey".toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
