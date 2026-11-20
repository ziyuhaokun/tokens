package com.ziyuhaokun.tokens.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class UsageApiException(message: String) : Exception(message) {
    data object Unauthorized : UsageApiException("API Key 无效")
    class Http(val code: Int, detail: String) : UsageApiException("HTTP $code" + if (detail.isBlank()) "" else " · ${detail.take(160)}")
    class Network(message: String) : UsageApiException("网络错误：$message")
    class Decode(message: String) : UsageApiException("响应解析失败：$message")
}

class UsageApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UsageApiException.Network(e.message ?: e::class.java.simpleName)
        }

        response.use { res ->
            val body = res.body?.string().orEmpty()
            when {
                res.code == 401 -> throw UsageApiException.Unauthorized
                !res.isSuccessful -> throw UsageApiException.Http(res.code, body)
                else -> runCatching { Usage.parse(body, zone) }
                    .getOrElse { throw UsageApiException.Decode(it.message ?: "未知错误") }
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
