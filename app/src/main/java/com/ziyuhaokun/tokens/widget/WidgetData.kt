package com.ziyuhaokun.tokens.widget

import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.ThemeMode
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageAgg
import com.ziyuhaokun.tokens.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/** 大尺寸里「按工具 · 7 天」的一行。costUsd 是美元原值，显示时走 NumberFormats 换算。 */
data class WidgetToolRow(val name: String, val costUsd: Double)

data class WidgetSnapshot(
    val configured: Boolean,
    val handle: String?,
    /** null = 本地还没有这份缓存（例如刚升级、还没拉过 7 天），显示「—」而不是 $0.00。 */
    val todayCost: Double?,
    val weekCost: Double?,
    val weekTokens: Long?,
    val trend: List<Double>,
    val updatedAt: Long?,
    val message: String?,
    /** 7 天里花钱最多的几个工具（最多 3 个），给 4x4 用；没数据就是空列表。 */
    val topTools: List<WidgetToolRow> = emptyList(),
    /** 外观跟着 App 的设置走，小组件不再写死深色。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentChoice = AccentChoice.MONO,
    /** 货币 / token 单位，跟 App 设置一致。 */
    val formats: NumberFormats = NumberFormats.DEFAULT,
    val refreshing: Boolean = false,
) {
    companion object {
        val NOT_CONFIGURED = WidgetSnapshot(
            configured = false,
            handle = null,
            todayCost = null,
            weekCost = null,
            weekTokens = null,
            trend = emptyList(),
            updatedAt = null,
            message = "打开 App 填写 API Key",
        )

        /**
         * Android 15 桌面生成预览用的固定样例：不能带真实账号 / 真实金额。
         * updatedAt = null，右上角就显示「刷新」而不是某个时刻。
         */
        val PREVIEW = WidgetSnapshot(
            configured = true,
            handle = "you",
            todayCost = 12.34,
            weekCost = 86.20,
            weekTokens = 45_600_000L,
            trend = listOf(8.4, 10.2, 7.9, 15.4, 12.76, 19.2, 12.34),
            updatedAt = null,
            message = null,
            topTools = listOf(
                WidgetToolRow("Claude Code", 52.40),
                WidgetToolRow("Codex", 22.10),
                WidgetToolRow("Gemini CLI", 11.70),
            ),
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
        // 没配 key（包括刚退出登录）：不显示任何旧数字，但外观仍按用户设置来。
        if (!settings.configured) {
            return WidgetSnapshot.NOT_CONFIGURED.copy(
                themeMode = settings.themeMode,
                accent = settings.accent,
                formats = settings.numberFormats,
            )
        }

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
            todayCost = today?.first?.let { usage -> UsageAgg.totals(usage).cost },
            weekCost = weekTotals?.cost,
            weekTokens = weekTotals?.tokens,
            trend = weekUsage?.let { usage ->
                UsageAgg.trend(usage, Range.D7).map { it.cost }
            } ?: emptyList(),
            updatedAt = maxOf(today?.second ?: 0L, week?.second ?: 0L).takeIf { it > 0 },
            message = null,
            topTools = weekUsage?.let { usage ->
                UsageAgg.bySource(usage).take(3).map { WidgetToolRow(name = it.name, costUsd = it.cost) }
            } ?: emptyList(),
            themeMode = settings.themeMode,
            accent = settings.accent,
            formats = settings.numberFormats,
        )
    }
}
