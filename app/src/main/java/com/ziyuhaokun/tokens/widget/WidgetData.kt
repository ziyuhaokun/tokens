package com.ziyuhaokun.tokens.widget

import android.content.Context
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.SettingsStore
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageAgg
import com.ziyuhaokun.tokens.data.UsageApiException

data class WidgetSnapshot(
    val configured: Boolean,
    val handle: String?,
    val todayCost: Double,
    val weekCost: Double,
    val weekTokens: Long,
    val trend: List<Double>,
    val updatedAt: Long?,
    val message: String?,
)

/** 小组件只读缓存，不自己发请求；刷新由 RefreshAction / Worker 负责。 */
object WidgetData {

    suspend fun read(store: SettingsStore): WidgetSnapshot {
        val settings = store.snapshot()
        val today = store.readCache(Range.TODAY)?.let { entry ->
            runCatching { Usage.parse(entry.json) }.getOrNull()?.let { it to entry.fetchedAt }
        }
        val week = store.readCache(Range.D7)?.let { entry ->
            runCatching { Usage.parse(entry.json) }.getOrNull()?.let { it to entry.fetchedAt }
        }
        val weekUsage = week?.first
        return WidgetSnapshot(
            configured = settings.configured,
            handle = today?.first?.accountHandle ?: weekUsage?.accountHandle,
            todayCost = today?.first?.let { usage -> UsageAgg.totals(usage).cost } ?: 0.0,
            weekCost = weekUsage?.let { usage -> UsageAgg.totals(usage).cost } ?: 0.0,
            weekTokens = weekUsage?.let { usage -> UsageAgg.totals(usage).tokens } ?: 0L,
            trend = weekUsage?.let { usage ->
                UsageAgg.trend(usage, Range.D7).map { it.cost }
            } ?: emptyList(),
            updatedAt = maxOf(today?.second ?: 0L, week?.second ?: 0L).takeIf { it > 0 },
            message = if (settings.configured) null else "先打开 App 填 API Key",
        )
    }

    /** 拉「今天」+「7 天」写缓存，供小组件渲染。返回是否有任一成功。 */
    suspend fun refresh(context: Context): Boolean {
        val container = AppContainer.get(context)
        val settings = container.settings.snapshot()
        val key = settings.apiKey ?: return false
        var anyOk = false
        for (range in listOf(Range.TODAY, Range.D7)) {
            try {
                val usage = container.api.fetch(settings.baseUrl, key, range.params())
                container.settings.writeCache(range, usage.rawJson, System.currentTimeMillis())
                anyOk = true
            } catch (_: UsageApiException) {
                // 一个区间失败不影响另一个
            }
        }
        return anyOk
    }
}
