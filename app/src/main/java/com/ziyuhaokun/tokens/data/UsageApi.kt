package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.URLEncoder
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class UsageApiException(message: String) : Exception(message) {
    data object Unauthorized : UsageApiException("API Key 无效")
    class Http(val code: Int, detail: String) : UsageApiException(describe(code, detail))
    class Network(message: String) : UsageApiException("网络错误：$message")
    class Decode(message: String) : UsageApiException("响应解析失败：$message")

    private companion object {
        /**
         * 给用户看的 HTTP 错误。网关报错常常回一整页 HTML，原样显示出来是一堆标签，
         * 所以只保留纯文本 / JSON 里的一句话。
         */
        fun describe(code: Int, detail: String): String {
            val head = when (code) {
                in 500..599 -> "服务端出错（HTTP $code）"
                429 -> "请求太频繁（HTTP 429）"
                403 -> "没有权限（HTTP 403）"
                404 -> "接口不存在（HTTP 404），检查服务器地址"
                else -> "HTTP $code"
            }
            val text = detail.trim()
            if (text.isEmpty() || text.startsWith("<")) return head
            val message = runCatching { org.json.JSONObject(text).optString("error").ifBlank { null } }.getOrNull()
                ?: text.lineSequence().first()
            return "$head · ${message.take(120)}"
        }
    }
}

/** 单次响应体上限：最大的请求（365 天）约 1.5MB，留 8MB 余量。 */
internal const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024

/**
 * 限量读取响应体。不信任服务端声明的 contentLength，边读边计数：
 * 超大响应（网关报错页、被污染的接口、无限流式响应）直接判失败，
 * 不然一次请求就能把内存和缓存文件打爆。
 */
internal fun Response.readTextLimited(limit: Long = MAX_RESPONSE_BYTES): String {
    val body = body ?: return ""
    val declared = body.contentLength()
    if (declared > limit) throw UsageApiException.Decode("响应过大（$declared 字节）")
    val source = body.source()
    val buffer = Buffer()
    var total = 0L
    try {
        while (true) {
            val read = source.read(buffer, 64L * 1024)
            if (read == -1L) break
            total += read
            if (total > limit) throw UsageApiException.Decode("响应过大")
        }
        return buffer.readUtf8()
    } catch (e: IOException) {
        // 读取过程中的网络/解码异常不要让原始 message（可能是整段响应）裸奔到界面。
        throw UsageApiException.Decode("读取响应失败")
    }
}

class UsageApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        // 不跟重定向：地址由用户填写，跳走就换了个主机，带 key 的请求不该跟着走。
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    /** GET {baseUrl}/api/usage，自动带 tz。失败一律以 UsageApiException 抛出。 */
    suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        params: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Usage = withContext(Dispatchers.IO) {
        val query = buildList {
            params.forEach { (k, v) -> add("$k=${URLEncoder.encode(v, "UTF-8")}") }
            add("tz=${URLEncoder.encode(zone.id, "UTF-8")}")
        }.joinToString("&")
        val url = baseUrl.trimEnd('/') + "/api/usage?" + query
        val request = try {
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            // 地址拼不出合法 URL（没 scheme、带空格等）：别再让底层异常裸奔到界面上。
            throw UsageApiException.Decode("服务器地址无效，检查「API 地址」")
        }

        val response = try {
            client.newCall(request).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UsageApiException.Network((e.message ?: e::class.java.simpleName).take(120))
        }

        response.use { res ->
            val body = res.readTextLimited()
            when {
                res.code == 401 -> throw UsageApiException.Unauthorized
                !res.isSuccessful -> throw UsageApiException.Http(res.code, body)
                else -> runCatching { Usage.parse(body, zone) }
                    // 不把 org.json 的原文（可能含整段响应内容）带给用户。
                    .getOrElse { throw UsageApiException.Decode("数据格式不正确") }
            }
        }
    }

    /** 只验证 key 是否可用（最省的请求：1 天、只要聚合）。失败抛 UsageApiException。 */
    suspend fun verify(baseUrl: String, apiKey: String) {
        fetch(baseUrl, apiKey, mapOf("days" to "1", "bucketsOnly" to "true"))
    }
}

/**
 * 异步发请求并支持协程取消：协程被 cancel 时顺手 cancel 掉 OkHttp 的 Call。
 * 之前用阻塞的 execute()，协程取消拦不住它，请求会照样发完。
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
