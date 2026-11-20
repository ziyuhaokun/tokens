package com.ziyuhaokun.tokens.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 云端用量契约（GET /api/usage）。字段全部防御式读取：
 * 服务端没有版本号，多一个少一个字段都不能让整页崩掉。
 */
data class Bucket(
    val source: String,
    val model: String,
    val project: String,
    val hostname: String,
    val bucketStartMillis: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long,
    val reasoningOutputTokens: Long,
    val cacheCreation5m: Long,
    val cacheCreation1h: Long,
    val estimatedCost: Double,
) {
    /** 与桌面端一致的口径：缓存读和缓存写都算进总量。 */
    val tokens: Long
        get() = inputTokens + outputTokens + cachedInputTokens +
            reasoningOutputTokens + cacheCreation5m + cacheCreation1h
}

data class Session(
    val source: String,
    val project: String,
    val activeSeconds: Long,
    /** 会话开始时间（毫秒）。服务端没给就为 0：热力图就没法按天摆放它。 */
    val startMillis: Long = 0L,
    /** 这段会话里的消息条数；服务端没给就为 0。 */
    val messageCount: Long = 0L,
)

data class UsageNames(
    val sources: Map<String, String> = emptyMap(),
    val models: Map<String, String> = emptyMap(),
    val modelFamilies: Map<String, String> = emptyMap(),
)

data class Usage(
    val accountHandle: String?,
    val buckets: List<Bucket>,
    val sessions: List<Session>,
    val hasAnyData: Boolean,
    val names: UsageNames,
    val rawJson: String,
) {
    fun sourceName(id: String): String = names.sources[id] ?: id
    fun modelName(id: String): String = names.models[id] ?: id

    companion object {
        val EMPTY = Usage(null, emptyList(), emptyList(), false, UsageNames(), "")

        fun parse(json: String, zone: ZoneId = ZoneId.systemDefault()): Usage {
            val root = JSONObject(json)
            return Usage(
                accountHandle = root.optJSONObject("account")
                    ?.let { it.optString("handle").ifBlank { it.optString("name") } }
                    ?.ifBlank { null },
                buckets = root.optJSONArray("buckets").objects { it.toBucket(zone) },
                sessions = root.optJSONArray("sessions").objects {
                    Session(
                        source = it.optString("source"),
                        project = it.optString("project", "unknown").ifBlank { "unknown" },
                        activeSeconds = it.optLong("activeSeconds", 0L),
                        startMillis = it.optString("firstMessageAt").toEpochMillis(),
                        messageCount = it.optLong("messageCount", 0L),
                    )
                },
                hasAnyData = root.optBoolean("hasAnyData", false),
                names = root.optJSONObject("names")?.let {
                    UsageNames(
                        sources = it.optJSONObject("sources").stringMap(),
                        models = it.optJSONObject("models").stringMap(),
                        modelFamilies = it.optJSONObject("modelFamilies").stringMap(),
                    )
                } ?: UsageNames(),
                rawJson = json,
            )
        }

        private fun <T> JSONArray?.objects(map: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) {
                    optJSONObject(i)?.let { o -> runCatching { add(map(o)) } }
                }
            }
        }

        /** "2026-11-20T09:55:00.000Z" 或带偏移的 ISO 都能吃，解析不了就当没有。 */
        private fun String.toEpochMillis(): Long {
            if (isBlank()) return 0L
            return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
                .recoverCatching { java.time.Instant.parse(this).toEpochMilli() }
                .getOrDefault(0L)
        }

        private fun JSONObject?.stringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (key in keys()) {
                val value = optString(key)
                if (value.isNotBlank()) out[key] = value
            }
            return out
        }

        private fun JSONObject.toBucket(zone: ZoneId): Bucket {
            val start = optString("bucketStart")
            val millis = runCatching {
                OffsetDateTime.parse(start).toInstant().toEpochMilli()
            }.recoverCatching {
                java.time.Instant.parse(start).toEpochMilli()
            }.getOrDefault(0L)
            return Bucket(
                source = optString("source", "unknown"),
                model = optString("model", "unknown"),
                project = optString("project", "unknown").ifBlank { "unknown" },
                hostname = optString("hostname"),
                bucketStartMillis = millis,
                inputTokens = optLong("inputTokens", 0L),
                outputTokens = optLong("outputTokens", 0L),
                cachedInputTokens = optLong("cachedInputTokens", 0L),
                reasoningOutputTokens = optLong("reasoningOutputTokens", 0L),
                cacheCreation5m = optLong("cacheCreation5mTokens", 0L),
                cacheCreation1h = optLong("cacheCreation1hTokens", 0L),
                estimatedCost = optDouble("estimatedCost", 0.0),
            )
        }
    }
}
