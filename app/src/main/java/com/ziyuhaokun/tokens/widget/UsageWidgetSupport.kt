package com.ziyuhaokun.tokens.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ziyuhaokun.tokens.AppContainer
import com.ziyuhaokun.tokens.data.RefreshOutcome
import com.ziyuhaokun.tokens.data.UsageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** 4x2（用量概览）。沿用原类名，桌面上已有的小组件升级后照常工作。 */
class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/** 2x2（今日费用）。内容和 4x2 是同一个 UsageWidget，按实际尺寸换布局。 */
class UsageWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/** 4x4（用量趋势）。 */
class UsageWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/** 三个尺寸，给设置页「添加到桌面」和生成预览用。 */
enum class WidgetSize(val label: String, val receiver: Class<out GlanceAppWidgetReceiver>) {
    SMALL("2×2 今日费用", UsageWidgetSmallReceiver::class.java),
    MEDIUM("4×2 用量概览", UsageWidgetReceiver::class.java),
    LARGE("4×4 用量趋势", UsageWidgetLargeReceiver::class.java),
}

/** 点小组件右上角「刷新」：立即抓一次、写缓存、重绘，不走 WorkManager 的 15 分钟节流。 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppContainer.get(context).widgetSync.refresh()
    }
}

class UsageRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = AppContainer.get(applicationContext).widgetSync.refresh()
        return when (outcome) {
            null, RefreshOutcome.OK -> Result.success()
            // 没 key / key 失效：重试也没用，等用户在 App 里重新配置。
            RefreshOutcome.NOT_CONFIGURED, RefreshOutcome.UNAUTHORIZED -> Result.success()
            RefreshOutcome.FAILED -> Result.retry()
        }
    }
}

object RefreshScheduler {
    private const val WORK_NAME = "usage-refresh"
    private const val NOW_WORK_NAME = "usage-refresh-now"
    private const val INTERVAL_MINUTES = 30L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageRefreshWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * 马上补拉一次小组件的两份数据（今天 + 7 天）。用于：刚配好 key、App 冷启动。
     * 否则升级后旧缓存被清掉，小组件要等最多 30 分钟才有 7 天数据。
     */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(NOW_WORK_NAME)
    }
}

/** 账号状态变化后统一收尾：排 / 撤后台任务，并让小组件重画。 */
class WidgetSync(private val context: Context, private val repository: UsageRepository) {
    private val refreshLock = Mutex()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** 桌面点击和后台任务共用一次刷新。null 表示已有请求在跑。 */
    suspend fun refresh(): RefreshOutcome? {
        if (!refreshLock.tryLock()) return null
        val outcome = try {
            _refreshing.value = true
            // 活跃的卡片通过状态流更新，redraw 也会唤起其他尺寸的会话。
            redraw()
            repository.refreshForWidget()
        } finally {
            _refreshing.value = false
            refreshLock.unlock()
        }
        redraw()
        return outcome
    }

    /** 桌面是否支持从 App 里发起小组件添加请求。 */
    fun canPin(): Boolean =
        android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** 返回 true 表示请求已交给桌面处理，确认和添加结果由桌面决定。 */
    suspend fun pin(size: WidgetSize): Boolean = runCatching {
        if (!canPin()) return@runCatching false
        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(receiver = size.receiver)
    }.getOrElse {
        if (it is CancellationException) throw it
        false
    }

    /**
     * Android 15+ 的生成式预览：把 UsageWidget.providePreview 画的示例卡片交给系统，
     * 小部件列表里看到的就是真实样式。系统对调用有限频，只在每个版本第一次启动时调。
     */
    suspend fun publishPreviewsOnce(versionCode: Long) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("widget_previews", Context.MODE_PRIVATE)
        val manager = GlanceAppWidgetManager(context)
        for (size in WidgetSize.entries) {
            // 已发布过旧图表的安装也要更新 4×4 预览。
            val previewKey = if (size == WidgetSize.LARGE) "large_area_preview" else size.name
            if (prefs.getLong(previewKey, -1L) == versionCode) continue
            val result = manager.setWidgetPreviews(size.receiver.kotlin)
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                prefs.edit().putLong(previewKey, versionCode).apply()
            }
        }
    }

    suspend fun onCredentialsChanged(configured: Boolean) {
        if (configured) {
            RefreshScheduler.schedule(context)
            RefreshScheduler.refreshNow(context)
        } else {
            RefreshScheduler.cancel(context)
        }
        redraw()
    }

    suspend fun redraw() {
        runCatching { UsageWidget().updateAll(context) }.onFailure {
            if (it is CancellationException) throw it
        }
    }
}
