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

    /**
     * 只留 [scope] 这个账号的缓存，其他账号的文件全删；再按总量上限从最旧的开始淘汰。
     * 换账号 / 换 key 后调用，否则旧账号几 MB 的文件会永远留在磁盘上。
     */
    suspend fun retainOnly(scope: String, maxBytes: Long = MAX_CACHE_BYTES) {
        val removed = withContext(Dispatchers.IO) {
            lock.withLock { pruneCacheDir(dir, scope, maxBytes) }
        }
        if (removed > 0) _version.value += 1
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
        /** 缓存目录总量上限。一个账号的全部范围加起来也就几 MB，这个值只是异常时的保险。 */
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024

        /** 账号作用域：同一个服务器 + 同一个 key 才算同一份缓存。 */
        fun scopeOf(baseUrl: String, apiKey: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${baseUrl.trimEnd('/')}\n$apiKey".toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * 清理缓存目录，返回删掉的文件个数：
 * 1. 文件名不以 `<scope>-` 开头的（别的账号留下的缓存、临时文件）全部删除；
 * 2. 剩下的总大小超过 [maxBytes]，就从最久没改过的开始删，直到不超。
 */
internal fun pruneCacheDir(dir: File, scope: String, maxBytes: Long): Int {
    var removed = 0
    val all = dir.listFiles().orEmpty().filter { it.isFile }
    for (file in all) {
        if (!file.name.startsWith("$scope-") && file.delete()) removed++
    }
    val keep = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }
    var total = keep.sumOf { it.length() }
    for (file in keep) {
        if (total <= maxBytes) break
        val size = file.length()
        if (file.delete()) {
            total -= size
            removed++
        }
    }
    return removed
}
