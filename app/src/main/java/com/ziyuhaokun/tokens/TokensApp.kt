package com.ziyuhaokun.tokens

import android.app.Application
import com.ziyuhaokun.tokens.data.Range
import com.ziyuhaokun.tokens.widget.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TokensApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        val container = AppContainer.get(this)
        appScope.launch {
            container.cache.dropLegacy()
            val settings = container.settings.snapshot()
            if (settings.configured) {
                RefreshScheduler.schedule(this@TokensApp)
                // 小组件的 7 天缓存不在（刚升级 / 刚清过缓存）就立刻补一次；
                // 有缓存时不补，免得后台任务每次拉起进程都多发一轮请求。
                if (container.repository.readCacheRaw(Range.D7, settings) == null) {
                    RefreshScheduler.refreshNow(this@TokensApp)
                }
            } else {
                RefreshScheduler.cancel(this@TokensApp)
            }
        }
    }
}
