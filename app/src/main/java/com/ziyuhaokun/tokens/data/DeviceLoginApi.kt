package com.ziyuhaokun.tokens.data

import android.os.Build
import com.ziyuhaokun.tokens.data.UsageApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 浏览器登录（设备码流程），和 vibe-usage CLI 用同一套接口：
 *
 *   POST /api/usage/device/code  { clientName, hostname } -> { deviceCode, userCode, verificationUriComplete, interval, expiresIn }
 *   POST /api/usage/device/poll  { deviceCode }           -> { apiKey } | { error: authorization_pending | access_denied | expired_token }
 *
 * 用户在浏览器里确认后，poll 会返回 apiKey；整个过程不经过任何中间服务。
 */
data class DeviceLoginRequest(
    val deviceCode: String,
    val userCode: String,
    /** 带验证码的完整链接，直接打开浏览器即可。 */
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

sealed interface DevicePollResult {
    data class Approved(val apiKey: String) : DevicePollResult
    data object Pending : DevicePollResult
    data object Denied : DevicePollResult
    data object Expired : DevicePollResult
    data class Failed(val message: String) : DevicePollResult
}

/** 只做设备码这两步，和 UsageApi 共用同一个 OkHttpClient 与错误语义。 */
class DeviceLoginApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        // 不跟重定向：deviceCode 就是换 apiKey 的凭据，不能被 307 转到别的主机去。
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun request(baseUrl: String, clientName: String, hostname: String): DeviceLoginRequest =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("clientName", clientName)
                .put("hostname", hostname)
                .toString()
            val json = post(baseUrl, "/api/usage/device/code", body)
            val userCode = json.optString("userCode")
            DeviceLoginRequest(
                deviceCode = json.optString("deviceCode"),
                userCode = userCode,
                // 兜底拼链接时验证码必须编码，否则服务端回一个带 & 的码就能注入额外参数。
                verificationUri = resolveVerificationUri(
                    rawComplete = json.optString("verificationUriComplete"),
                    rawUri = json.optString("verificationUri"),
                    userCode = userCode,
                    baseUrl = baseUrl,
                ),
                intervalSeconds = json.optInt("interval", 5).coerceIn(2, 60),
                expiresInSeconds = json.optInt("expiresIn", 900).coerceIn(60, 3600),
            ).also {
                if (it.deviceCode.isBlank() || it.userCode.isBlank()) {
                    throw UsageApiException.Decode("登录响应缺少验证码")
                }
            }
        }

    suspend fun poll(baseUrl: String, deviceCode: String): DevicePollResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("deviceCode", deviceCode).toString()
        val json = try {
            post(baseUrl, "/api/usage/device/poll", body)
        } catch (e: UsageApiException.Unauthorized) {
            return@withContext DevicePollResult.Failed("登录状态失效，请重新登录")
        }
        val key = json.optString("apiKey")
        if (key.isNotBlank()) {
            DevicePollResult.Approved(key)
        } else {
            when (json.optString("error")) {
                "authorization_pending" -> DevicePollResult.Pending
                "access_denied" -> DevicePollResult.Denied
                "expired_token", "invalid_grant" -> DevicePollResult.Expired
                "" -> DevicePollResult.Pending
                else -> DevicePollResult.Failed(json.optString("error"))
            }
        }
    }

    private fun post(baseUrl: String, path: String, body: String): JSONObject {
        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()
        } catch (e: IllegalArgumentException) {
            throw UsageApiException.Decode("服务器地址无效，检查「API 地址」")
        }
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UsageApiException.Network((e.message ?: e::class.java.simpleName).take(120))
        }
        response.use { res ->
            val text = res.readTextLimited()
            if (res.code == 401) throw UsageApiException.Unauthorized
            if (!res.isSuccessful) throw UsageApiException.Http(res.code, text)
            return runCatching { JSONObject(text) }
                .getOrElse { throw UsageApiException.Decode("响应不是 JSON") }
        }
    }

    companion object {
        const val CLIENT_NAME = "Tokens Android"

        private val INTERNAL_BIND_HOSTS = setOf("0.0.0.0", "127.0.0.1", "localhost")

        /**
         * 服务端在容器 / 反代后有时会把本机监听地址（如 https://0.0.0.0:3000/usage/device）
         * 拼进 verificationUri。此时把 scheme/host/port 还原成用户请求的 baseUrl，
         * 第三方未知域名原样保留交由 UI 层的信任校验拦截。
         */
        internal fun resolveVerificationUri(
            rawComplete: String,
            rawUri: String,
            userCode: String,
            baseUrl: String,
        ): String {
            val candidate = rawComplete.ifBlank {
                if (rawUri.isBlank()) ""
                else rawUri + "?code=" + URLEncoder.encode(userCode, "UTF-8")
            }
            if (candidate.isBlank()) return ""
            if (candidate.any { it.isWhitespace() || it.isISOControl() || it == '\\' || it == '@' }) {
                return candidate
            }
            val parsed = runCatching { candidate.toHttpUrlOrNull() }.getOrNull() ?: return candidate
            val base = normalizeBaseUrl(baseUrl)
                ?.let { runCatching { it.toHttpUrlOrNull() }.getOrNull() }
                ?: return candidate
            val host = parsed.host.lowercase()
            val baseHost = base.host.lowercase()
            val baseIsLoopback = baseHost == "localhost" || baseHost == "127.0.0.1"
            val shouldRewrite = host == "0.0.0.0" || (!baseIsLoopback && host in INTERNAL_BIND_HOSTS)
            if (!shouldRewrite) return candidate
            return parsed.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
                .toString()
        }

        /** 设备名只用于服务端的登录记录，不带账号信息。 */
        fun hostnameOf(): String = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
    }
}
