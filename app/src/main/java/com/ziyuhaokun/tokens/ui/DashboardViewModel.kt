package com.ziyuhaokun.tokens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.Snapshot
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val ready: Boolean = false,
    val configured: Boolean = false,
    val baseUrl: String = "https://vibecafe.ai",
    val range: Range = Range.D7,
    val usage: Usage? = null,
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
)

class DashboardViewModel(private val repository: UsageRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var settings: StoredSettings? = null
    private var inflight: Job? = null

    init {
        viewModelScope.launch {
            repository.settings.collect { stored ->
                val previous = settings
                settings = stored
                // 缓存写入等无关变更不应重复发请求：只在身份/地址/范围真的变了才拉。
                val identityChanged = previous == null ||
                    previous.apiKey != stored.apiKey ||
                    previous.baseUrl != stored.baseUrl ||
                    previous.configured != stored.configured
                val storedRangeChanged = previous != null && previous.range != stored.range
                val first = _state.value
                val next = _state.value.copy(
                    ready = true,
                    configured = stored.configured,
                    baseUrl = stored.baseUrl,
                    range = if (first.usage == null) stored.range else first.range,
                )
                _state.value = next
                when {
                    !stored.configured ->
                        _state.value = next.copy(usage = null, error = null, stale = false, loading = false)

                    identityChanged -> {
                        showCached(next.range)
                        load(next.range)
                    }

                    storedRangeChanged -> selectRange(stored.range)

                    first.usage == null -> {
                        showCached(next.range)
                        load(next.range)
                    }
                }
            }
        }
    }

    fun selectRange(range: Range) {
        if (range == _state.value.range) return
        _state.value = _state.value.copy(range = range, usage = null, fetchedAt = null, error = null)
        viewModelScope.launch { repository.saveRange(range) }
        showCached(range)
        load(range)
    }

    fun refresh() = load(_state.value.range)

    fun saveCredentials(apiKey: String, baseUrl: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            repository.saveCredentials(apiKey, baseUrl)
            val stored = repository.snapshot()
            val result = repository.load(_state.value.range, stored)
            onDone(result.error)
            _state.value = _state.value.copy(
                configured = stored.configured,
                usage = result.usage ?: _state.value.usage,
                fetchedAt = result.fetchedAt ?: _state.value.fetchedAt,
                error = result.error,
                stale = result.fromCache && result.usage != null,
            )
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.clearCredentials() }
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { repository.saveBaseUrl(url) }
    }

    private fun showCached(range: Range) {
        viewModelScope.launch {
            val cached: Snapshot = repository.cached(range) ?: return@launch
            if (_state.value.range != range) return@launch
            _state.value = _state.value.copy(
                usage = cached.usage,
                fetchedAt = cached.fetchedAt,
                stale = true,
            )
        }
    }

    private fun load(range: Range) {
        val stored = settings ?: return
        if (!stored.configured) return
        inflight?.cancel()
        _state.value = _state.value.copy(loading = true)
        inflight = viewModelScope.launch {
            val result = repository.load(range, stored)
            if (_state.value.range != range) return@launch
            _state.value = _state.value.copy(
                loading = false,
                usage = result.usage ?: _state.value.usage,
                fetchedAt = result.fetchedAt ?: _state.value.fetchedAt,
                error = result.error,
                stale = result.fromCache && result.usage != null,
            )
        }
    }

    companion object {
        fun factory(repository: UsageRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(repository) as T
        }
    }
}
