package com.ziyuhaokun.tokens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageRepository
import com.ziyuhaokun.tokens.data.YearAgg
import com.ziyuhaokun.tokens.data.YearData
import com.ziyuhaokun.tokens.data.YearMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class HeatmapState(
    val accountHandle: String? = null,
    val data: YearData? = null,
    val metric: YearMetric = YearMetric.COST,
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
)

/**
 * 整年数据只在切到「热力」时才拉，并且有缓存就先用缓存：
 * 一次 365 天的响应约 1.5MB，不该拦着首屏。
 *
 * 跟着设置流走：换 key / 换服务器 / 退出登录时清空并按新身份重拉，
 * 不能只在第一次读一下 settings 就一直用下去。
 */
class HeatmapViewModel(
    private val repository: UsageRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val today: (ZoneId) -> LocalDate = { LocalDate.now(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(HeatmapState())
    val state: StateFlow<HeatmapState> = _state.asStateFlow()

    private var inflight: Job? = null
    private var loadedAt: Long = 0L
    private var settings: StoredSettings? = null
    private var visible = false

    init {
        viewModelScope.launch {
            repository.settings.collect { stored ->
                val previous = settings
                settings = stored
                if (previous != null && previous.cacheScope == stored.cacheScope) return@collect

                // 身份变了（含第一次拿到设置）：丢掉旧账号的数据。
                inflight?.cancel()
                loadedAt = 0L
                _state.value = HeatmapState(metric = _state.value.metric)
                if (visible && stored.configured) load()
            }
        }
    }

    fun onShown() {
        visible = true
        val current = _state.value
        val fresh = current.data != null && now() - loadedAt < STALE_AFTER_MS
        if (fresh && !current.stale) return
        if (inflight?.isActive == true) return
        load()
    }

    fun onHidden() {
        visible = false
    }

    fun refresh() = load()

    fun selectMetric(metric: YearMetric) {
        if (metric != _state.value.metric) _state.update { it.copy(metric = metric) }
    }

    private fun load() {
        // 设置还没到：init 里的收集器拿到设置后会按 visible 补一次。
        val stored = settings ?: return
        if (!stored.configured) return
        inflight?.cancel()
        inflight = viewModelScope.launch {
            val zone = ZoneId.systemDefault()

            // 还没有数据时先把缓存摆上去，网络慢也不至于一直空着。
            if (_state.value.data == null) {
                repository.cachedYear(stored, zone)?.let { cached ->
                    val data = build(cached.usage, zone)
                    _state.update {
                        it.copy(
                            accountHandle = cached.usage?.accountHandle ?: it.accountHandle,
                            data = data ?: it.data,
                            fetchedAt = cached.fetchedAt,
                            stale = true,
                        )
                    }
                }
            }

            _state.update { it.copy(loading = true, error = null) }

            val snapshot = repository.loadYear(stored, zone)
            val data = build(snapshot.usage, zone)
            loadedAt = now()
            _state.update {
                it.copy(
                    accountHandle = snapshot.usage?.accountHandle ?: it.accountHandle,
                    data = data ?: it.data,
                    fetchedAt = snapshot.fetchedAt ?: it.fetchedAt,
                    loading = false,
                    error = snapshot.error,
                    stale = snapshot.fromCache || (snapshot.error != null && it.data != null),
                )
            }
        }
    }

    /** 365 天聚合放到后台线程，别在主线程上折腾 1.5MB 的数据。 */
    private suspend fun build(usage: Usage?, zone: ZoneId): YearData? =
        usage?.let { withContext(Dispatchers.Default) { YearAgg.build(it, today(zone), zone) } }

    companion object {
        private const val STALE_AFTER_MS = 10 * 60 * 1000L

        fun factory(repository: UsageRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HeatmapViewModel(repository) as T
        }
    }
}
