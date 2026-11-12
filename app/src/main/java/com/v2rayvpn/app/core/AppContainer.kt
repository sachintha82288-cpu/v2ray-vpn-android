package com.v2rayvpn.app.core

import android.content.Context
import com.v2rayvpn.app.data.net.SubscriptionFetcher
import com.v2rayvpn.app.data.repo.AppRepository
import com.v2rayvpn.app.data.store.AppStore
import java.io.File

/**
 * Manual dependency wiring.
 *
 * Hilt would add an annotation processor to the build for a graph this small; a single container
 * is easier to audit and keeps compile times and build reproducibility intact.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val store: AppStore = AppStore(File(appContext.filesDir, "store.enc"))
    val repository: AppRepository = AppRepository(store)
    val subscriptionFetcher: SubscriptionFetcher = SubscriptionFetcher()

    fun assetPath(): String = "file:///android_asset/"

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun init(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(context).also { instance = it }
        }

        fun get(): AppContainer = instance
            ?: error("AppContainer not initialised — V2RayApp.onCreate must run first")
    }
}
