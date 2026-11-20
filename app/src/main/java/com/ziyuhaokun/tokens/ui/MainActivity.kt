package com.ziyuhaokun.tokens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziyuhaokun.tokens.AppContainer
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
        // App 永远是深色界面，所以状态栏/导航栏图标必须始终是亮的。
        // 沿用 enableEdgeToEdge() 的默认值会跟随系统深浅色：系统在浅色模式时
        // 会给深色图标，叠在我们的近黑背景上就完全看不见了。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            TokensTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val heatmapState by heatmap.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }
                var showSettings by remember { mutableStateOf(false) }
                var saving by remember { mutableStateOf(false) }
                var saveError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                fun afterConfigured() {
                    showSettings = false
                    saving = false
                    saveError = null
                    RefreshScheduler.schedule(applicationContext)
                    scope.launch { UsageWidget().updateAll(applicationContext) }
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
                                if (error == null) {
                                    afterConfigured()
                                } else {
                                    saving = false
                                    saveError = error
                                }
                            }
                        },
                    )

                    else -> {
                        // 整年数据第一次切过去才拉；之后 10 分钟内复用。
                        LaunchedEffect(tab) {
                            if (tab == AppTab.YEAR) heatmap.onShown()
                        }
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

                        if (showSettings) {
                            SettingsDialog(
                                baseUrl = state.baseUrl,
                                saving = saving,
                                error = saveError,
                                onDismiss = { if (!saving) showSettings = false },
                                onSave = { key, url ->
                                    if (key.isBlank()) {
                                        viewModel.saveBaseUrl(url)
                                        showSettings = false
                                        saveError = null
                                    } else {
                                        saving = true
                                        saveError = null
                                        viewModel.saveCredentials(key, url) { error ->
                                            if (error == null) afterConfigured()
                                            else {
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
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun SettingsDialog(
    baseUrl: String,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SoftCard(contentPadding = 18) {
            Column {
                Text("设置", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Key 留空只更新服务端地址。",
                    color = Palette.Dim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))
                CredentialsForm(
                    initialKey = "",
                    initialUrl = baseUrl,
                    saving = saving,
                    error = error,
                    saveLabel = "保存",
                    onSave = onSave,
                    onCancel = onDismiss,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}
