package com.ziyuhaokun.tokens.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.AccentChoice
import com.ziyuhaokun.tokens.data.FontScaleChoice
import com.ziyuhaokun.tokens.data.NumberFormats
import com.ziyuhaokun.tokens.data.ReceiptBuilder
import com.ziyuhaokun.tokens.data.ReceiptData
import com.ziyuhaokun.tokens.data.ReceiptLanguage
import com.ziyuhaokun.tokens.data.ReceiptStyle
import com.ziyuhaokun.tokens.data.Usage
import com.ziyuhaokun.tokens.data.ThemeMode
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.withFrameNanos
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
        // 冷启动/转屏重建的第一帧可能就有 key 输入框：先无条件保护，
        // 等设置流确认已配置后再由下面的 LaunchedEffect 清掉。
        setSecureWindow(true)
        setContent {
            val scope = rememberCoroutineScope()
            // 提到最外层：录制层要知道设置页是否打开，小票 / 浮层也都从这里读。
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val stored by container.repository.settings.collectAsStateWithLifecycle(initialValue = null)
            // 主题切换自己留一份即时值：点下去立刻换配色，不等 DataStore 往返。
            var themeOverride by remember { mutableStateOf<ThemeMode?>(null) }
            val themeMode = themeOverride ?: stored?.themeMode ?: ThemeMode.SYSTEM
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 系统栏图标必须跟着 App 自己的深浅色走，不能跟系统主题走。
            LaunchedEffect(dark) { applySystemBars(dark) }
            // 设置流写回来之后才丢掉即时值，避免新旧主题闪一帧。
            LaunchedEffect(stored?.themeMode) {
                if (themeOverride != null && stored?.themeMode == themeOverride) themeOverride = null
            }

            // 货币 / token 单位：整个界面通过 LocalNumberFormats 读取。
            val numberFormats = stored?.numberFormats ?: NumberFormats.DEFAULT

            // 主题切换的圆形揭开：先把当前画面录进一层，换主题后让旧画面从圆外保留、
            // 新主题从触点向外长出。半径只在绘制阶段读，不会重组。
            val captureLayer = rememberGraphicsLayer()
            var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
            var revealCenter by remember { mutableStateOf(Offset.Zero) }
            var canvasSize by remember { mutableStateOf(IntSize.Zero) }
            var boxTopLeft by remember { mutableStateOf(Offset.Zero) }
            val revealRadius = remember { Animatable(0f) }

            fun switchTheme(mode: ThemeMode, at: Offset?) {
                // 动画没放完之前不接第二次点击，免得互相打断。
                if (snapshot != null) return
                val motion = stored?.motion ?: true
                if (!motion) {
                    scope.launch { container.repository.saveThemeMode(mode) }
                    return
                }
                scope.launch {
                    // 顺序很关键：先把旧画面盖上去（和屏幕上此刻完全一样，看不出变化），
                    // 再换配色——否则「换主题」的那一帧会直接露出新配色，就是那下闪屏。
                    val shot = if (canvasSize != IntSize.Zero) {
                        runCatching { captureLayer.toImageBitmap() }.getOrNull()
                    } else {
                        null
                    }
                    if (shot != null) {
                        revealCenter = (at ?: Offset(canvasSize.width / 2f, canvasSize.height / 2f)) - boxTopLeft
                        revealRadius.snapTo(0f)
                        snapshot = shot
                        // 等这一帧画完（盖住旧画面）再动主题。
                        withFrameNanos { }
                    }
                    themeOverride = mode
                    container.repository.saveThemeMode(mode)
                    if (shot != null) {
                        revealRadius.animateTo(
                            targetValue = farthestCornerDistance(revealCenter, canvasSize),
                            animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing),
                        )
                        snapshot = null
                    }
                }
            }

            CompositionLocalProvider(LocalNumberFormats provides numberFormats) {
                // 整屏录一层：切主题的瞬间取上一帧当「旧画面」，圆外继续显示它、圆内已经是新主题。
                // 只在设置页打开时录制（那时才可能切主题），平时不额外占绘制开销。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            canvasSize = coords.size
                            boxTopLeft = coords.positionInWindow()
                        }
                        .drawWithContent {
                            if (showSettings && snapshot == null) {
                                captureLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(captureLayer)
                            } else {
                                drawContent()
                            }
                            snapshot?.let { old ->
                                val radius = revealRadius.value
                                if (radius > 0f) {
                                    val hole = Path().apply {
                                        addOval(Rect(center = revealCenter, radius = radius))
                                    }
                                    clipPath(hole, clipOp = ClipOp.Difference) {
                                        drawImage(
                                            image = old,
                                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                        )
                                    }
                                } else {
                                    drawImage(old, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                                }
                            }
                        },
                ) {
                TokensTheme(
                    themeMode = themeMode,
                    accent = stored?.accent ?: AccentChoice.MONO,
                    fontScale = stored?.fontScale ?: FontScaleChoice.STANDARD,
                    motion = stored?.motion ?: true,
                ) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val heatmapState by heatmap.state.collectAsStateWithLifecycle()
                    var tab by rememberSaveable { mutableStateOf(AppTab.OVERVIEW) }

                    // 正在展示的小票；null = 没打开。按下那一刻的数据定格，之后刷新不改它。
                    // 放在最外层：首页「打印小票」和设置页「预览小票」共用。
                    var receipt by remember { mutableStateOf<ReceiptData?>(null) }
                    fun printReceipt() {
                        // 设置页预览时可能还没拉到数据：用空数据也能看样式，金额全是 0。
                        val usage = state.usage ?: Usage.EMPTY
                        receipt = ReceiptBuilder.build(usage, state.range, state.fetchedAt, state.stale)
                    }
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

                    // 引导页上有 API Key 输入框，这段时间禁止截屏/录屏/最近任务预览。
                    LaunchedEffect(state.ready, state.configured) {
                        setSecureWindow(!state.ready || !state.configured)
                    }

                    // 设置页不是独立 Activity：系统返回键要自己接住，否则会直接退出 App。
                    Box(Modifier.fillMaxSize()) {
                        when {
                            !state.ready -> Box(Modifier.fillMaxSize().background(Palette.Bg))

                            // 先验证再保存：只有验证通过 configured 才会变 true，失败原因留在引导页上。
                            !state.configured -> SetupScreen(
                                saving = state.saving,
                                error = state.saveError,
                                login = state.login,
                                onLogin = viewModel::startBrowserLogin,
                                onCancelLogin = viewModel::cancelBrowserLogin,
                                onSave = viewModel::saveCredentials,
                            )

                            else -> {
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
                                Box(Modifier.fillMaxSize()) {
                                    // 动态效果开关：关掉就硬切，不做淡入。
                                    if (motion) {
                                        Crossfade(targetState = tab, label = "tab") { content(it) }
                                    } else {
                                        content(tab)
                                    }
                                    // 设置页盖在首页上面：下层页面保持在场，返回手势才有可预览的目标。
                                    if (showSettings) {
                                        SettingsOverlay(onDismiss = ::closeSettings) {
                                            SettingsScreen(
                                                settings = stored,
                                                versionName = versionName,
                                                onBack = ::closeSettings,
                                                onThemeMode = { mode -> switchTheme(mode, at = null) },
                                                onThemeModeAt = { mode, at -> switchTheme(mode, at) },
                                                onAccent = { scope.launch { container.repository.saveAccent(it) } },
                                                onFontScale = { scope.launch { container.repository.saveFontScale(it) } },
                                                onMotion = { scope.launch { container.repository.saveMotion(it) } },
                                                onCurrency = { scope.launch { container.repository.saveCurrency(it) } },
                                                onCnyPerUsd = { scope.launch { container.repository.saveCnyPerUsd(it) } },
                                                onUnits = { scope.launch { container.repository.saveUnits(it) } },
                                                onReceiptStyle = { scope.launch { container.repository.saveReceiptStyle(it) } },
                                                onReceiptLanguage = { scope.launch { container.repository.saveReceiptLanguage(it) } },
                                                onPreviewReceipt = ::printReceipt,
                                                canPinWidget = remember { container.widgetSync.canPin() },
                                                onPinWidget = container.widgetSync::pin,
                                                onSignOut = {
                                                    viewModel.signOut()
                                                    closeSettings()
                                                },
                                                onClearCache = {
                                                    scope.launch {
                                                        container.repository.clearCache()
                                                        refreshEverything()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 盖在页面上面；它自己的 BackHandler 后注册，返回键先关小票。
                        receipt?.let { shown ->
                            ReceiptSheet(
                                data = shown,
                                formats = numberFormats,
                                style = stored?.receiptStyle ?: ReceiptStyle.CLASSIC,
                                language = stored?.receiptLanguage ?: ReceiptLanguage.MIXED,
                                onDismiss = { receipt = null },
                            )
                        }
                    }
                }
                }
            }
        }
    }

/**
 * 设置页浮层：左上角的返回按钮、系统返回键和返回手势都走同一条退出路径。
 *
 * 用 [PredictiveBackHandler] 而不是 BackHandler：手指从屏幕边缘往回拖时，
 * 页面会跟着手势右移、缩小，松手前就能看到「回到首页」的预览，不会一按就突然消失。
 */
@Composable
private fun SettingsOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val motion = LocalMotionEnabled.current
    // 1 = 完全打开，0 = 完全退回。进场时从 0 长到 1，返回手势时跟着手指走。
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) reveal.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }

    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event -> reveal.snapTo((1f - event.progress).coerceIn(0f, 1f)) }
            if (motion) reveal.animateTo(0f, tween(durationMillis = 120)) else reveal.snapTo(0f)
            onDismiss()
        } catch (e: CancellationException) {
            // 手势取消：弹回完全打开。
            if (motion) reveal.animateTo(1f, tween(durationMillis = 180)) else reveal.snapTo(1f)
            throw e
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val back = 1f - reveal.value
                // 只位移四分之一屏：拖到底时首页已经露出来，又不会把设置页甩出屏幕。
                translationX = size.width * 0.25f * back
                alpha = 1f - 0.35f * back
                val shrink = 1f - 0.04f * back
                scaleX = shrink
                scaleY = shrink
            },
    ) {
        content()
    }
}

    /**
     * 只有「屏幕上确实有凭据」时才加 FLAG_SECURE。常驻会让用户连自己的用量截图都截不了，
     * 所以按需开关：引导页挂着时开，进主界面就关。
     */
    private fun setSecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
