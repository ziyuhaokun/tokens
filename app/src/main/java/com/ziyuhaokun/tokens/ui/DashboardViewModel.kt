package com.ziyuhaokun.tokens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziyuhaokun.tokens.data.DEFAULT_BASE_URL
import com.ziyuhaokun.tokens.data.DevicePollResult
import com.ziyuhaokun.tokens.data.DeviceLoginRequest
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.data.StoredSettings
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.UsageRepository
import com.ziyuhaokun.tokens.widget.WidgetSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 浏览器登录的进度，只影响引导页 / 登录区的显示。 */
sealed interface LoginPhase {
    data object Idle : LoginPhase
    data object Starting : LoginPhase
    data class Waiting(
        val userCode: String,
        val verificationUri: String,
        val expiresAt: Long,
        /** 服务端地址，用来核对验证页的主机是不是同一个。 */
        val baseUrl: String = DEFAULT_BASE_URL,
    ) : LoginPhase
    data object Denied : LoginPhase
    data object Expired : LoginPhase
    data class Failed(val message: String) : LoginPhase
}

data class DashboardState(
    val ready: Boolean = false,
    val configured: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val range: Range = Range.D7,
    val usage: Usage? = null,
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    /** 正在验证 / 保存 key。 */
    val saving: Boolean = false,
    /** 验证 / 保存失败的原因，给引导页和设置页显示。 */
    val saveError: String? = null,
    /** 每保存成功一次 +1：设置页据此收起表单（不靠回调，旋转屏幕也不丢）。 */
    val saveCount: Int = 0,
    val login: LoginPhase = LoginPhase.Idle,
)

class DashboardViewModel(
    private val repository: UsageRepository,
    private val widgetSync: WidgetSync,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var settings: StoredSettings? = null
    private var inflight: Job? = null
    private var saveJob: Job? = null
    private var loginJob: Job? = null
    private var lastLoadedAt = 0L

    /** 当前要看的范围。同步更新，避免「切范围后立刻点刷新」拉到旧范围。 */
    private var currentRange: Range = Range.D7

    init {
        viewModelScope.launch {
            repository.settings.collect { stored ->
                val previous = settings
                settings = stored

                if (!stored.configured) {
                    inflight?.cancel()
                    lastLoadedAt = 0L
                    _state.update {
                        it.copy(
                            ready = true,
                            configured = false,
                            baseUrl = stored.baseUrl,
                            usage = null,
                            fetchedAt = null,
                            error = null,
                            stale = false,
                            loading = false,
                            saveError = if (stored.serverAddressInvalid) {
                                "服务器地址无效，请重新配置"
                            } else {
                                it.saveError
                            },
                        )
                    }
                    return@collect
                }

                // 只有「换了账号 / 服务器」才重新拉：改主题、改字号不该触发请求。
                val identityChanged = previous == null || previous.cacheScope != stored.cacheScope
                if (identityChanged) {
                    if (previous == null) currentRange = stored.range
                    // 换账号：先清掉旧账号的数字，再读新账号的缓存。
                    _state.update {
                        it.copy(
                            ready = true,
                            configured = true,
                            baseUrl = stored.baseUrl,
                            range = currentRange,
                            usage = null,
                            fetchedAt = null,
                            loading = true,
                            error = null,
                            stale = false,
                        )
                    }
                    switchTo(currentRange)
                } else {
                    _state.update { it.copy(ready = true, configured = true, baseUrl = stored.baseUrl) }
                }
            }
        }
    }

    fun selectRange(range: Range) {
        if (range == currentRange) return
        viewModelScope.launch { repository.saveRange(range) }
        switchTo(range)
    }

    fun refresh() {
        val stored = settings ?: return
        if (!stored.configured) return
        val range = currentRange
        inflight?.cancel()
        // range 一起写上：上一次 switchTo 可能还没来得及把它写进 state 就被取消了。
        _state.update { it.copy(loading = true, range = range) }
        inflight = viewModelScope.launch { fetch(range, stored) }
    }

    /** 回到前台：刚拉过（1 分钟内）或正在拉就不重复请求。 */
    fun onResume() {
        if (inflight?.isActive == true) return
        if (now() - lastLoadedAt < RESUME_REFRESH_AFTER_MS) return
        refresh()
    }

    /**
     * 先验证再保存。[apiKey] 为空表示只改服务器地址、沿用旧 key。
     * 成功后设置流会发出新身份，init 里的收集器负责拉数据。
     */
    fun saveCredentials(apiKey: String, baseUrl: String) {
        if (saveJob?.isActive == true) return
        _state.update { it.copy(saving = true, saveError = null) }
        saveJob = viewModelScope.launch {
            val error = repository.verifyAndSaveCredentials(apiKey, baseUrl)
            if (error == null) {
                widgetSync.onCredentialsChanged(configured = true)
                _state.update { it.copy(saving = false, saveError = null, saveCount = it.saveCount + 1) }
            } else {
                _state.update { it.copy(saving = false, saveError = error) }
            }
        }
    }

    /**
     * 浏览器登录：拿验证码 → 打开确认页 → 每几秒问一次结果。
     * 全程用 viewModelScope，转屏不会丢；用户取消或到期就停。
     */
    fun startBrowserLogin() {
        if (loginJob?.isActive == true) return
        val stored = settings ?: return
        _state.update { it.copy(login = LoginPhase.Starting, saveError = null) }
        loginJob = viewModelScope.launch {
            val request = try {
                repository.requestDeviceLogin(stored.baseUrl)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(login = LoginPhase.Failed(e.message ?: "无法开始登录")) }
                return@launch
            }
            val expiresAt = now() + request.expiresInSeconds * 1000L
            _state.update {
                it.copy(
                    login = LoginPhase.Waiting(
                        userCode = request.userCode,
                        verificationUri = request.verificationUri,
                        expiresAt = expiresAt,
                        baseUrl = stored.baseUrl,
                    ),
                )
            }
            pollUntilApproved(stored.baseUrl, request, expiresAt)
        }
    }

    fun cancelBrowserLogin() {
        loginJob?.cancel()
        loginJob = null
        _state.update { it.copy(login = LoginPhase.Idle) }
    }

    private suspend fun pollUntilApproved(baseUrl: String, request: DeviceLoginRequest, expiresAt: Long) {
        while (now() < expiresAt) {
            delay(request.intervalSeconds * 1000L)
            val result = try {
                repository.pollDeviceLogin(baseUrl, request.deviceCode)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 网络抖一下不算失败，继续问到到期。
                continue
            }
            when (result) {
                is DevicePollResult.Approved -> {
                    val error = repository.verifyAndSaveCredentials(result.apiKey, baseUrl)
                    if (error == null) {
                        widgetSync.onCredentialsChanged(configured = true)
                        _state.update {
                            it.copy(login = LoginPhase.Idle, saving = false, saveError = null, saveCount = it.saveCount + 1)
                        }
                    } else {
                        _state.update { it.copy(login = LoginPhase.Failed(error)) }
                    }
                    return
                }
                DevicePollResult.Denied -> {
                    _state.update { it.copy(login = LoginPhase.Denied) }
                    return
                }
                DevicePollResult.Expired -> {
                    _state.update { it.copy(login = LoginPhase.Expired) }
                    return
                }
                is DevicePollResult.Failed -> {
                    _state.update { it.copy(login = LoginPhase.Failed(result.message)) }
                    return
                }
                DevicePollResult.Pending -> Unit
            }
        }
        _state.update { it.copy(login = LoginPhase.Expired) }
    }

    fun clearSaveError() {
        _state.update { it.copy(saveError = null) }
    }

    /** 退出：清 key、清缓存、撤后台任务、让小组件回到「去配置」。 */
    fun signOut() {
        // 当前 UI 下登录/保存只出现在未配置引导页，退出只在已配置设置页；
        // 这里仍一并取消，防止以后加入口后出现「退出后账号又写回来」。
        inflight?.cancel()
        loginJob?.cancel()
        loginJob = null
        saveJob?.cancel()
        saveJob = null
        _state.update { it.copy(login = LoginPhase.Idle, saving = false) }
        viewModelScope.launch {
            repository.signOut()
            widgetSync.onCredentialsChanged(configured = false)
        }
    }

    /** 切范围：先读该范围的缓存一起换上去（不闪空卡），再发请求。 */
    private fun switchTo(range: Range) {
        val stored = settings ?: return
        currentRange = range
        inflight?.cancel()
        inflight = viewModelScope.launch {
            val cached = repository.cached(range, stored)
            _state.update {
                it.copy(
                    ready = true,
                    range = range,
                    usage = cached?.usage,
                    fetchedAt = cached?.fetchedAt,
                    stale = cached != null,
                    error = null,
                    loading = true,
                )
            }
            fetch(range, stored)
        }
    }

    private suspend fun fetch(range: Range, stored: StoredSettings) {
        val result = repository.load(range, stored)
        lastLoadedAt = now()
        _state.update {
            if (it.range != range) {
                it
            } else {
                it.copy(
                    loading = false,
                    usage = result.usage ?: it.usage,
                    fetchedAt = result.fetchedAt ?: it.fetchedAt,
                    error = result.error,
                    stale = result.fromCache && result.usage != null,
                )
            }
        }
    }

    companion object {
        private const val RESUME_REFRESH_AFTER_MS = 60_000L

        fun factory(repository: UsageRepository, widgetSync: WidgetSync) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(repository, widgetSync) as T
        }
    }
}
