package com.v2rayvpn.app.vpn

import android.content.Context
import com.v2rayvpn.app.data.parse.XrayConfigBuilder
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real proxy round-trip measurement for a single profile.
 *
 * The engine builds a throw-away instance from the profile's own configuration and requests the
 * test URL through it. This measures handshake plus round-trip through the proxy — it is not a
 * throughput test, and the UI labels it as such.
 */
object LatencyTester {

    const val TEST_TYPE = "proxy round-trip"

    suspend fun measure(
        context: Context,
        profile: Profile,
        settings: AppSettings
    ): Long? = withContext(Dispatchers.IO) {
        XrayEngine.ensureEnv(context)
        val config = try {
            XrayConfigBuilder.build(profile, settings)
        } catch (e: Exception) {
            return@withContext null
        }
        val result = XrayEngine.measureProfileDelay(config, settings.latencyTestUrl)
        if (result < 0) null else result
    }
}
