package com.ziyuhaokun.tokens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.UsageRepository
import com.ziyuhaokun.tokens.data.YearAgg
import com.ziyuhaokun.tokens.data.YearData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 */
class HeatmapViewModel(private val repository: UsageRepository) : ViewModel() {

    private val _state = MutableStateFlow(HeatmapState())
    val state: StateFlow<HeatmapState> = _state.asStateFlow()

    private var inflight: Job? = null
    private var loadedAt: Long = 0L
    private var settings: StoredSettings? = null

    fun onShown() {
        val snapshotState = _state.value
        val fresh = snapshotState.data != null && System.currentTimeMillis() - loadedAt < STALE_AFTER_MS
        if (fresh && !snapshotState.stale) return
        if (inflight?.isActive == true) return
        load()
    }

    fun refresh() = load()

    fun selectMetric(metric: YearMetric) {
        if (metric != _state.value.metric) _state.value = _state.value.copy(metric = metric)
    }

    private fun load() {
        inflight = viewModelScope.launch {
            val stored = settings ?: repository.snapshot().also { settings = it }

            _state.value = _state.value.copy(loading = true, error = null)

            val snapshot = repository.loadYear(stored, ZoneId.systemDefault())
            val usage = snapshot.usage
            val data = usage?.let { YearAgg.build(it, LocalDate.now(ZoneId.systemDefault())) }
            loadedAt = System.currentTimeMillis()
            _state.value = HeatmapState(
                accountHandle = usage?.accountHandle ?: _state.value.accountHandle,
                data = data ?: _state.value.data,
                metric = _state.value.metric,
                fetchedAt = snapshot.fetchedAt ?: _state.value.fetchedAt,
                loading = false,
                error = snapshot.error,
                stale = snapshot.fromCache,
            )
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 10 * 60 * 1000L

        fun factory(repository: UsageRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HeatmapViewModel(repository) as T
        }
    }
}
