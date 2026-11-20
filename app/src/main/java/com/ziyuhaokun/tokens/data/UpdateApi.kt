package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 更新流程里的失败。message 直接给用户看，所以不带原始报文。 */
class UpdateException(message: String) : Exception(message)

/**
 * 从 GitHub 查最新 Release、下载 APK。
 *
 * - 查询用 api.github.com，不带任何凭据，不跟重定向；
 * - 下载要跟重定向（GitHub 会跳到它的文件域名），但**每一跳**都要过域名白名单，
 *   跳到别处直接中断，不会把请求带到不认识的主机。
 */
class UpdateApi(
    private val repo: String = UPDATE_REPO,
    private val metaClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // 不设整体超时：慢网下 3MB 也可能要一会儿，卡住由读超时兜底。
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!UpdateLogic.isAllowedDownloadUrl(request.url)) {
                throw IOException("blocked host ${request.url.host}")
            }
            chain.proceed(request)
        }
        .build(),
) {
    /** 最新正式 Release。仓库没有 Release / 没有可用 APK 返回 null。 */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            metaClient.newCall(request).execute().use { res ->
                when {
                    res.code == 404 -> null
                    res.code == 403 || res.code == 429 -> throw UpdateException("GitHub 请求太频繁，稍后再试")
                    !res.isSuccessful -> throw UpdateException("检查更新失败（HTTP ${res.code}）")
                    else -> UpdateLogic.parseRelease(res.readTextLimited(1L * 1024 * 1024), repo)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            throw e
        } catch (e: UsageApiException) {
            throw UpdateException("检查更新失败：响应异常")
        } catch (e: IOException) {
            throw UpdateException("网络错误，无法连接 GitHub")
        }
    }

    /**
     * 把 [info] 的 APK 下载到 [dest]。先写 `.part` 临时文件，大小和 SHA-256 都对上才改成正式名。
     * [onProgress] 参数是（已下载字节，总字节；总数未知为 -1），约每 100ms 回调一次。
     */
    suspend fun download(
        info: ReleaseInfo,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!UpdateLogic.isTrustedAssetUrl(info.apkUrl, repo)) throw UpdateException("下载地址不可信")
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            downloadClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw UpdateException("下载失败（HTTP ${res.code}）")
                if (!UpdateLogic.isAllowedDownloadUrl(res.request.url)) throw UpdateException("下载地址不可信")
                val body = res.body ?: throw UpdateException("下载失败：响应为空")
                val declared = body.contentLength()
                val total = if (declared > 0) declared else info.apkSize
                if (total > UpdateLogic.MAX_APK_BYTES) throw UpdateException("安装包异常（体积过大）")
                // 先查剩余空间，免得下到一半磁盘满了，再被误报成「网络中断」。留 16MB 余量给系统安装器用。
                val needed = (if (total > 0) total else 0L) + 16L * 1024 * 1024
                if (total > 0 && (dest.parentFile?.usableSpace ?: Long.MAX_VALUE) < needed) {
                    throw UpdateException("存储空间不足，至少需要 ${needed / (1024 * 1024)}MB 可用空间")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var lastReport = 0L
                val buffer = ByteArray(32 * 1024)
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > UpdateLogic.MAX_APK_BYTES) throw UpdateException("安装包异常（体积过大）")
                            // 写盘失败（多半是空间不足）要和网络中断分开报，用户才知道该清理手机而不是换网。
                            try {
                                out.write(buffer, 0, n)
                            } catch (e: IOException) {
                                throw UpdateException("保存安装包失败，请检查存储空间")
                            }
                            digest.update(buffer, 0, n)
                            val now = System.nanoTime()
                            if (now - lastReport > 100_000_000L) {
                                lastReport = now
                                onProgress(read, total)
                            }
                        }
                    }
                }
                onProgress(read, total)
                if (info.apkSize > 0 && read != info.apkSize) throw UpdateException("下载不完整，请重试")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256 != null && actual != info.sha256) throw UpdateException("安装包校验失败（SHA-256 不一致）")
                dest.delete()
                if (!part.renameTo(dest)) throw UpdateException("保存安装包失败")
            }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: UpdateException) {
            part.delete()
            throw e
        } catch (e: IOException) {
            part.delete()
            throw UpdateException("下载中断，请检查网络后重试")
        }
    }

    private companion object {
        /** 不带版本、不带设备信息：GitHub 要求有 UA，仅此而已。 */
        const val USER_AGENT = "Tokens-Android"
    }
}
