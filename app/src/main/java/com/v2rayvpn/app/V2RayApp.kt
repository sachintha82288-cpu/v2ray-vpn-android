package com.v2rayvpn.app

import android.app.Application
import com.v2rayvpn.app.core.AppContainer
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.vpn.XrayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class V2RayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.init(this)
        container.store.load()
        XrayEngine.ensureEnv(this)
        LogBus.i("Application started")
        appScope.launch {
            container.repository.loadOnce()
        }
    }
}
