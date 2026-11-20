package com.ziyuhaokun.tokens.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
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
import java.util.concurrent.TimeUnit

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/** 点小组件右上角「刷新」：立即抓一次、写缓存、重绘，不走 WorkManager 的 15 分钟节流。 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppContainer.get(context).repository.refreshForWidget()
        UsageWidget().updateAll(context)
    }
}

class UsageRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = AppContainer.get(applicationContext).repository.refreshForWidget()
        UsageWidget().updateAll(applicationContext)
        return when (outcome) {
            RefreshOutcome.OK -> Result.success()
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
class WidgetSync(private val context: Context) {

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
        runCatching { UsageWidget().updateAll(context) }
    }
}
