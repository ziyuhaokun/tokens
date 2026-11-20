package com.ziyuhaokun.tokens

import android.content.Context
import com.ziyuhaokun.tokens.data.DeviceLoginApi
import com.ziyuhaokun.tokens.data.SettingsStore
import com.ziyuhaokun.tokens.data.UpdateApi
import com.ziyuhaokun.tokens.data.UpdateManager
import com.ziyuhaokun.tokens.data.UsageApi
import com.ziyuhaokun.tokens.data.UsageCache
import com.ziyuhaokun.tokens.data.UsageRepository
import com.ziyuhaokun.tokens.widget.WidgetSync

/** 手动注入，不上 DI 框架：这个 App 只有这几个对象。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val cache = UsageCache(appContext)
    val api = UsageApi()
    val deviceLogin = DeviceLoginApi()
    val repository = UsageRepository(api, settings, cache, deviceLogin)
    val widgetSync = WidgetSync(appContext, repository)
    val updater = UpdateManager(appContext, UpdateApi(), settings)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = AppContainer(context)
                }
            }
        }

        fun get(context: Context): AppContainer {
            init(context)
            return instance!!
        }
    }
}
