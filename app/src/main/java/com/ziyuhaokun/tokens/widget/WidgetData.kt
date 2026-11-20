package com.ziyuhaokun.tokens.widget

import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageAgg
import com.ziyuhaokun.tokens.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class WidgetSnapshot(
    val configured: Boolean,
    val handle: String?,
    val todayCost: Double,
    val weekCost: Double,
    val weekTokens: Long,
    val trend: List<Double>,
    val updatedAt: Long?,
    val message: String?,
) {
    companion object {
        val NOT_CONFIGURED = WidgetSnapshot(
            configured = false,
            handle = null,
            todayCost = 0.0,
            weekCost = 0.0,
            weekTokens = 0L,
            trend = emptyList(),
            updatedAt = null,
            message = "先打开 App 填 API Key",
        )
    }
}

/** 小组件只读缓存，不自己发请求；刷新由 RefreshAction / Worker 负责。 */
object WidgetData {

    suspend fun read(repository: UsageRepository): WidgetSnapshot = read(repository, repository.snapshot())

    /**
     * 设置或缓存一变就重读。小组件在 provideContent 里 collect 这个流，
     * 会话还活着时 updateAll() 只会重组、不会重跑 provideGlance，
     * 所以数据必须从流里来，不能在 provideContent 之前读一次就完事。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(repository: UsageRepository): Flow<WidgetSnapshot> =
        combine(repository.settings, repository.cacheVersion) { settings, _ -> settings }
            .mapLatest { read(repository, it) }
            .flowOn(Dispatchers.Default)

    private suspend fun read(repository: UsageRepository, settings: StoredSettings): WidgetSnapshot {
        // 没配 key（包括刚退出登录）：不显示任何旧数字。
        if (!settings.configured) return WidgetSnapshot.NOT_CONFIGURED

        val today = repository.readCacheRaw(Range.TODAY, settings)?.let { entry ->
            runCatching { Usage.parse(entry.json) }.getOrNull()?.let { it to entry.fetchedAt }
        }
        val week = repository.readCacheRaw(Range.D7, settings)?.let { entry ->
            runCatching { Usage.parse(entry.json) }.getOrNull()?.let { it to entry.fetchedAt }
        }
        val weekUsage = week?.first
        val weekTotals = weekUsage?.let { UsageAgg.totals(it) }
        return WidgetSnapshot(
            configured = true,
            handle = today?.first?.accountHandle ?: weekUsage?.accountHandle,
            todayCost = today?.first?.let { usage -> UsageAgg.totals(usage).cost } ?: 0.0,
            weekCost = weekTotals?.cost ?: 0.0,
            weekTokens = weekTotals?.tokens ?: 0L,
            trend = weekUsage?.let { usage ->
                UsageAgg.trend(usage, Range.D7).map { it.cost }
            } ?: emptyList(),
            updatedAt = maxOf(today?.second ?: 0L, week?.second ?: 0L).takeIf { it > 0 },
            message = null,
        )
    }
}
