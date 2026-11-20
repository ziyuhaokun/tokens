package com.ziyuhaokun.tokens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.FontScaleChoice
import com.ziyuhaokun.tokens.data.ThemeMode
import com.ziyuhaokun.tokens.widget.RefreshScheduler
import com.ziyuhaokun.tokens.widget.UsageWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer.get(applicationContext) }
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.factory(container.repository)
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
                var saving by remember { mutableStateOf(false) }
                var saveError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val motion = stored?.motion ?: true
                val versionName = remember {
                    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                        .getOrNull() ?: "—"
                }

                fun afterConfigured() {
                    saving = false
                    saveError = null
                    RefreshScheduler.schedule(applicationContext)
                    scope.launch { UsageWidget().updateAll(applicationContext) }
                }

                fun refreshEverything() {
                    viewModel.refresh()
                    heatmap.refresh()
                    scope.launch {
                        RefreshScheduler.schedule(applicationContext)
                        UsageWidget().updateAll(applicationContext)
                    }
                }

                when {
                    !state.ready -> Box(Modifier.fillMaxSize().background(Palette.Bg))

                    !state.configured -> SetupScreen(
                        saving = saving,
                        error = saveError,
                        onSave = { key, url ->
                            saving = true
                            saveError = null
                            viewModel.saveCredentials(key, url) { error ->
                                if (error == null) afterConfigured() else {
                                    saving = false
                                    saveError = error
                                }
                            }
                        },
                    )

                    showSettings -> SettingsScreen(
                        settings = stored,
                        versionName = versionName,
                        saving = saving,
                        saveError = saveError,
                        onBack = { showSettings = false },
                        onThemeMode = { scope.launch { container.repository.saveThemeMode(it) } },
                        onAccent = { scope.launch { container.repository.saveAccent(it) } },
                        onFontScale = { scope.launch { container.repository.saveFontScale(it) } },
                        onMotion = { scope.launch { container.repository.saveMotion(it) } },
                        onSaveCredentials = { key, url ->
                            if (key.isBlank()) {
                                viewModel.saveBaseUrl(url)
                                saveError = null
                            } else {
                                saving = true
                                saveError = null
                                viewModel.saveCredentials(key, url) { error ->
                                    if (error == null) {
                                        afterConfigured()
                                    } else {
                                        saving = false
                                        saveError = error
                                    }
                                }
                            }
                        },
                        onSignOut = {
                            viewModel.signOut()
                            showSettings = false
                            saveError = null
                        },
                        onClearCache = { scope.launch { container.repository.clearCache() } },
                        onRefreshAll = { refreshEverything() },
                    )

                    else -> {
                        // 整年数据第一次切过去才拉；之后 10 分钟内复用。
                        LaunchedEffect(tab) {
                            if (tab == AppTab.YEAR) heatmap.onShown()
                        }
                        val content: @Composable (AppTab?) -> Unit = {
                            when (tab) {
                                AppTab.OVERVIEW -> DashboardScreen(
                                    state = state,
                                    tab = tab,
                                    onTab = { tab = it },
                                    onRange = viewModel::selectRange,
                                    onRefresh = viewModel::refresh,
                                    onSettings = { showSettings = true },
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
                        if (motion) {
                            Crossfade(targetState = tab, label = "tab") { content(it) }
                        } else {
                            content(tab)
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
        viewModel.refresh()
    }
}
