package com.v2rayvpn.app.vpn

import android.content.Context
import android.provider.Settings
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.core.util.Redactor
import go.Seq
import java.io.File
import java.util.Base64
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Thin, verified wrapper around the Xray-core Android binding (AndroidLibXrayLite v26.11.9).
 *
 * Only APIs that exist in that binding are used:
 *   Libv2ray.initCoreEnv / checkVersionX / measureOutboundDelay,
 *   CoreController.startLoop(configContent, tunFd) / stopLoop / measureDelay /
 *   queryAllOutboundTrafficStats.
 *
 * The TUN descriptor is handed over through `CoreController.startLoop`; the engine duplicates it
 * and reads from its own copy, so the VPN service keeps ownership of the original descriptor.
 */
class XrayEngine(private val listener: Listener) {

    interface Listener {
        fun onCoreStatus(code: Int, message: String)
        fun onCoreStarted()
        fun onCoreStopped()
    }

    private var controller: CoreController? = null

    private val callbackHandler = object : CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String): Long {
            listener.onCoreStatus(p0.toInt(), p1)
            return 0
        }

        override fun shutdown(): Long {
            listener.onCoreStopped()
            return 0
        }

        override fun startup(): Long {
            listener.onCoreStarted()
            return 0
        }
    }

    val isRunning: Boolean
        get() = controller?.isRunning == true

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (e: Throwable) {
        "unavailable"
    }

    /**
     * @return null on success, or a redacted error message.
     */
    @Synchronized
    fun start(configJson: String, tunFd: Int): String? {
        if (controller?.isRunning == true) return null
        return try {
            val instance = controller ?: Libv2ray.newCoreController(callbackHandler).also { controller = it }
            instance.startLoop(configJson, tunFd)
            LogBus.i("Xray core start requested (tun fd attached)")
            null
        } catch (e: Throwable) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            val safe = Redactor.redact(message)
            LogBus.e("Xray core failed to start: $safe")
            safe
        }
    }

    @Synchronized
    fun stop() {
        val instance = controller ?: return
        try {
            if (instance.isRunning) {
                instance.stopLoop()
                LogBus.i("Xray core stopped")
            }
        } catch (e: Throwable) {
            LogBus.e("Xray core stop error: ${Redactor.redact(e.message ?: e.javaClass.simpleName)}")
        }
    }

    /** Round-trip time through the running tunnel, or -1 when the check fails. */
    fun measureDelay(url: String): Long {
        val instance = controller ?: return -1
        if (!instance.isRunning) return -1
        return try {
            instance.measureDelay(url)
        } catch (e: Throwable) {
            -1
        }
    }

    /**
     * Reads and resets the engine counters.
     * @return pair of (uplink bytes, downlink bytes) for the proxy outbound.
     */
    @Synchronized
    fun consumeTrafficCounters(proxyTag: String): Pair<Long, Long> {
        val instance = controller ?: return 0L to 0L
        if (!instance.isRunning) return 0L to 0L
        val payload = try {
            instance.queryAllOutboundTrafficStats()
        } catch (e: Throwable) {
            return 0L to 0L
        }
        var up = 0L
        var down = 0L
        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach
            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach
            if (parts[0] != proxyTag) return@forEach
            val value = parts[2].toLongOrNull() ?: return@forEach
            when (parts[1]) {
                "uplink" -> up += value
                "downlink" -> down += value
            }
        }
        return up to down
    }

    companion object {

        @Volatile
        private var envReady = false

        /** Must run before any engine call. Safe to call repeatedly. */
        fun ensureEnv(context: Context) {
            if (envReady) return
            synchronized(this) {
                if (envReady) return
                try {
                    Seq.setContext(context.applicationContext)
                    val assetDir = File(context.filesDir, "xray-assets")
                    if (!assetDir.exists()) assetDir.mkdirs()
                    val deviceId = try {
                        val androidId = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ANDROID_ID
                        ) ?: "v2rayvpn"
                        Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(androidId.toByteArray(Charsets.UTF_8))
                    } catch (e: Exception) {
                        "v2rayvpn"
                    }
                    Libv2ray.initCoreEnv(assetDir.absolutePath, deviceId)
                    envReady = true
                    LogBus.i("Xray environment initialised: ${Libv2ray.checkVersionX()}")
                } catch (e: Throwable) {
                    LogBus.e("Xray environment initialisation failed: ${e.javaClass.simpleName}")
                }
            }
        }

        /**
         * Measures latency for a configuration without starting the VPN.
         * This is a proxy handshake/round-trip test, not a throughput or leak test.
         */
        fun measureProfileDelay(configJson: String, url: String): Long {
            return try {
                Libv2ray.measureOutboundDelay(configJson, url)
            } catch (e: Throwable) {
                -1
            }
        }
    }
}
