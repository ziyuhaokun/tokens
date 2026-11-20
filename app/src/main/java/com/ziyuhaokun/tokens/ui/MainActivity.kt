package com.ziyuhaokun.tokens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.FontScaleChoice
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.ReceiptBuilder
import com.ziyuhaokun.tokens.data.ReceiptData
import com.ziyuhaokun.tokens.data.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer.get(applicationContext) }
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.factory(container.repository, container.widgetSync)
    }
    private val heatmap: HeatmapViewModel by viewModels {
        HeatmapViewModel.factory(container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val stored by container.repository.settings.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = stored?.themeMode ?: ThemeMode.SYSTEM
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 系统栏图标必须跟着 App 自己的深浅色走，不能跟系统主题走。
            LaunchedEffect(dark) { applySystemBars(dark) }

            // 货币 / token 单位：整个界面通过 LocalNumberFormats 读取。
            val numberFormats = stored?.numberFormats ?: NumberFormats.DEFAULT
            CompositionLocalProvider(LocalNumberFormats provides numberFormats) {
                TokensTheme(
                    themeMode = themeMode,
                    accent = stored?.accent ?: AccentChoice.MONO,
                    fontScale = stored?.fontScale ?: FontScaleChoice.STANDARD,
                    motion = stored?.motion ?: true,
                ) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val heatmapState by heatmap.state.collectAsStateWithLifecycle()
                    var tab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val motion = stored?.motion ?: true
                    val versionName = remember {
                        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                            .getOrNull() ?: "—"
                    }

                    fun refreshEverything() {
                        viewModel.refresh()
                        heatmap.refresh()
                        scope.launch { container.widgetSync.onCredentialsChanged(configured = true) }
                    }

                    fun closeSettings() {
                        showSettings = false
                        viewModel.clearSaveError()
                    }

                    // 设置页不是独立 Activity：系统返回键要自己接住，否则会直接退出 App。
                    BackHandler(enabled = showSettings && state.configured) { closeSettings() }

                    when {
                        !state.ready -> Box(Modifier.fillMaxSize().background(Palette.Bg))

                        // 先验证再保存：只有验证通过 configured 才会变 true，失败原因留在引导页上。
                        !state.configured -> SetupScreen(
                            saving = state.saving,
                            error = state.saveError,
                            onSave = viewModel::saveCredentials,
                        )

                        showSettings -> SettingsScreen(
                            settings = stored,
                            versionName = versionName,
                            saving = state.saving,
                            saveError = state.saveError,
                            saveCount = state.saveCount,
                            onBack = ::closeSettings,
                            onThemeMode = { scope.launch { container.repository.saveThemeMode(it) } },
                            onAccent = { scope.launch { container.repository.saveAccent(it) } },
                            onFontScale = { scope.launch { container.repository.saveFontScale(it) } },
                            onMotion = { scope.launch { container.repository.saveMotion(it) } },
                            // 货币 / 单位变了，小组件也要跟着重画（它跟着设置流走，这里不用额外处理）。
                            onCurrency = { scope.launch { container.repository.saveCurrency(it) } },
                            onCnyPerUsd = { scope.launch { container.repository.saveCnyPerUsd(it) } },
                            onUnits = { scope.launch { container.repository.saveUnits(it) } },
                            // key 留空 = 只改服务器地址，用旧 key 在新地址上验证。
                            onSaveCredentials = viewModel::saveCredentials,
                            onDismissCredentials = viewModel::clearSaveError,
                            onSignOut = {
                                viewModel.signOut()
                                closeSettings()
                            },
                            onClearCache = {
                                // 先清完再刷新，不然新拉到的数据可能被后执行的清理删掉。
                                scope.launch {
                                    container.repository.clearCache()
                                    refreshEverything()
                                }
                            },
                            onRefreshAll = { refreshEverything() },
                        )

                        else -> {
                            // 正在展示的小票；null = 没打开。按下「打印小票」那一刻的数据定格，之后刷新不改它。
                            var receipt by remember { mutableStateOf<ReceiptData?>(null) }
                            fun printReceipt() {
                                val usage = state.usage ?: return
                                receipt = ReceiptBuilder.build(usage, state.range, state.fetchedAt, state.stale)
                            }

                            // 整年数据第一次切过去才拉；之后 10 分钟内复用。
                            LaunchedEffect(tab) {
                                if (tab == AppTab.YEAR) heatmap.onShown() else heatmap.onHidden()
                            }
                            // 用 Crossfade 传进来的那一页（shown），而不是外层的 tab：
                            // 否则淡出的那一层也会画成新页面，淡入淡出等于没做。
                            val content: @Composable (AppTab) -> Unit = { shown ->
                                when (shown) {
                                    AppTab.OVERVIEW -> DashboardScreen(
                                        state = state,
                                        tab = tab,
                                        onTab = { tab = it },
                                        onRange = viewModel::selectRange,
                                        onRefresh = viewModel::refresh,
                                        onSettings = { showSettings = true },
                                        onPrint = ::printReceipt,
                                    )

                                    AppTab.YEAR -> HeatmapScreen(
                                        state = heatmapState,
                                        tab = tab,
                                        onTab = { tab = it },
                                        onMetric = heatmap::selectMetric,
                                        onSettings = { showSettings = true },
                                        onRefresh = heatmap::refresh,
                                    )
                                }
                            }
                            // 动态效果开关：关掉就硬切，不做淡入。
                            Box(Modifier.fillMaxSize()) {
                                if (motion) {
                                    Crossfade(targetState = tab, label = "tab") { content(it) }
                                } else {
                                    content(tab)
                                }
                                // 盖在页面上面；它自己的 BackHandler 后注册，返回键先关小票。
                                receipt?.let { shown ->
                                    ReceiptSheet(data = shown, formats = numberFormats, onDismiss = { receipt = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        // 冷启动时 ViewModel 已经在拉了，这里只在数据老于 1 分钟时补拉，避免重复请求。
        viewModel.onResume()
    }
}
