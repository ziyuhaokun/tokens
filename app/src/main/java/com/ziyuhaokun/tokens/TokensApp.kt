package com.ziyuhaokun.tokens

import android.app.Application
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
            if (settings.configured) RefreshScheduler.schedule(this@TokensApp) else RefreshScheduler.cancel(this@TokensApp)
        }
    }
}
