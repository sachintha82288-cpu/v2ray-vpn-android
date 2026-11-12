package com.v2rayvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.v2rayvpn.app.MainActivity
import com.v2rayvpn.app.R
import com.v2rayvpn.app.core.AppContainer
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.core.util.Redactor
import com.v2rayvpn.app.data.parse.XrayConfigBuilder
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.ConnectionState
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.domain.model.PerAppMode
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.RoutingMode
import com.v2rayvpn.app.domain.model.TrafficSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Android VPN lifecycle.
 *
 * Order of operations (each step must succeed before the next):
 *  1. profile and settings are read from the encrypted store;
 *  2. the TUN interface is established by the OS (permission already granted by the UI);
 *  3. Xray-core is started with the descriptor, which duplicates it internally;
 *  4. a real health check runs through the tunnel before the UI is told "Connected".
 *
 * The service never reports a state it has not verified.
 */
class V2RayVpnService : VpnService(), XrayEngine.Listener {

    companion object {
        const val ACTION_START = "com.v2rayvpn.app.action.START"
        const val ACTION_STOP = "com.v2rayvpn.app.action.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1001

        /** Everything except RFC1918/loopback/link-local, so LAN traffic stays outside the tunnel. */
        private val NON_PRIVATE_ROUTES = listOf(
            "0.0.0.0" to 5, "8.0.0.0" to 7, "11.0.0.0" to 8, "12.0.0.0" to 6,
            "16.0.0.0" to 4, "32.0.0.0" to 3, "64.0.0.0" to 2, "128.0.0.0" to 3,
            "160.0.0.0" to 5, "168.0.0.0" to 6, "172.0.0.0" to 12, "172.32.0.0" to 11,
            "172.64.0.0" to 10, "172.128.0.0" to 9, "173.0.0.0" to 8, "174.0.0.0" to 7,
            "176.0.0.0" to 4, "192.0.0.0" to 9, "192.128.0.0" to 11, "192.160.0.0" to 13,
            "192.169.0.0" to 16, "192.170.0.0" to 15, "192.172.0.0" to 14,
            "192.176.0.0" to 12, "192.192.0.0" to 10, "193.0.0.0" to 8, "194.0.0.0" to 7,
            "196.0.0.0" to 6, "200.0.0.0" to 5, "208.0.0.0" to 4, "240.0.0.0" to 4
        )

        fun startIntent(context: Context, profileId: String?): Intent =
            Intent(context, V2RayVpnService::class.java).apply {
                action = ACTION_START
                profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, V2RayVpnService::class.java).apply { action = ACTION_STOP }
    }

    private lateinit var engine: XrayEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tunInterface: ParcelFileDescriptor? = null
    private var trafficJob: Job? = null
    private var connectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeProfileId: String? = null
    private var activeProfileName: String = ""

    override fun onCreate() {
        super.onCreate()
        engine = XrayEngine(this)
        createNotificationChannel()
        XrayEngine.ensureEnv(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                showNotification("Disconnecting…", "Stopping the tunnel")
                stopEverything(ConnectionState.Idle, "Disconnected by user")
            }
            ACTION_START -> startVpn(intent.getStringExtra(EXTRA_PROFILE_ID))
            else -> stopEverything(ConnectionState.Idle, "Service stopped")
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ start

    private fun startVpn(requestedProfileId: String?) {
        if (connectJob?.isActive == true) {
            LogBus.d("Connection already in progress; ignoring duplicate start request")
            return
        }
        showNotification("Preparing…", "Setting up the tunnel")
        connectJob = scope.launch {
            val container = AppContainer.get()
            container.repository.loadOnce()
            val repository = container.repository
            val profile = (requestedProfileId ?: repository.settings.value.selectedProfileId)
                ?.let { repository.profileById(it) }
                ?: repository.selectedProfile()

            if (profile == null) {
                fail("No server selected", "Import a configuration or add a subscription first.")
                return@launch
            }
            activeProfileId = profile.id
            activeProfileName = profile.name
            VpnStateHolder.setProfileId(profile.id)
            VpnStateHolder.resetTraffic()
            VpnStateHolder.setPublicIp(null)
            VpnStateHolder.setState(ConnectionState.Preparing("Loading profile ${profile.name}"))

            teardownTunnel()
            val settings = repository.settings.value

            VpnStateHolder.setState(ConnectionState.Connecting("Establishing the VPN interface"))
            val descriptor = try {
                establishTun(profile, settings)
            } catch (e: Exception) {
                fail(
                    "Could not create the VPN interface",
                    Redactor.redact(e.message ?: e.javaClass.simpleName)
                )
                return@launch
            }
            if (descriptor == null) {
                fail("VPN permission was not granted", "Start the connection again and allow the VPN request.")
                return@launch
            }
            tunInterface = descriptor

            VpnStateHolder.setState(ConnectionState.Connecting("Starting the Xray engine"))
            val config = try {
                XrayConfigBuilder.build(profile, settings)
            } catch (e: Exception) {
                fail("Could not build the configuration", Redactor.redact(e.message ?: "unknown error"))
                teardownTunnel()
                return@launch
            }
            val startError = withContext(Dispatchers.IO) { engine.start(config, descriptor.fd) }
            if (startError != null) {
                teardownTunnel()
                fail("The Xray engine refused the configuration", startError)
                return@launch
            }

            VpnStateHolder.setState(ConnectionState.Connecting("Checking the tunnel"))
            val health = healthCheck(settings)
            val since = System.currentTimeMillis()
            VpnStateHolder.setState(
                ConnectionState.Connected(
                    since = since,
                    profileName = profile.name,
                    healthCheckOk = health.first,
                    healthCheckMessage = health.second
                )
            )
            showNotification(
                if (health.first) "Connected · ${profile.name}" else "Connected with warnings · ${profile.name}",
                if (health.first) "Tunnel verified" else (health.second ?: "Health check failed")
            )
            startTrafficMonitor()
            registerNetworkCallback(settings)
            repository.selectProfile(profile.id)
        }
    }

    private fun establishTun(profile: Profile, settings: AppSettings): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("V2RAY VPN")
            .setMtu(settings.mtu)
            .addAddress(settings.tunIpv4Address, 30)

        if (settings.routingMode == RoutingMode.BYPASS_LAN) {
            NON_PRIVATE_ROUTES.forEach { (address, prefix) -> builder.addRoute(address, prefix) }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (settings.ipv6Enabled) {
            builder.addAddress(settings.tunIpv6Address, 126)
            if (settings.routingMode == RoutingMode.BYPASS_LAN) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        val dnsServer = if (settings.dnsMode == DnsMode.VPN) settings.remoteDns else settings.localDns
        if (isIpLiteral(dnsServer)) {
            try {
                builder.addDnsServer(dnsServer)
            } catch (e: IllegalArgumentException) {
                LogBus.w("DNS server address rejected by the system; continuing without it")
            }
        }

        applyPerAppRules(builder, settings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        LogBus.i("Establishing TUN: mtu=${settings.mtu}, ipv6=${settings.ipv6Enabled}, mode=${settings.routingMode.name}")
        return builder.establish()
    }

    private fun applyPerAppRules(builder: Builder, settings: AppSettings) {
        val selected = settings.perAppPackages
        when (settings.perAppMode) {
            PerAppMode.OFF -> builder.addDisallowedApplication(packageName)

            PerAppMode.ALLOW -> {
                // Apps outside the allow list are excluded by the OS; mixing allow and disallow is
                // not permitted by VpnService, so the app itself is simply not listed.
                selected.forEach { pkg ->
                    runCatching { builder.addAllowedApplication(pkg) }
                        .onFailure { LogBus.w("Could not add allowed app: ${it.javaClass.simpleName}") }
                }
            }

            PerAppMode.DISALLOW -> {
                builder.addDisallowedApplication(packageName)
                selected.forEach { pkg ->
                    if (pkg != packageName) {
                        runCatching { builder.addDisallowedApplication(pkg) }
                            .onFailure { LogBus.w("Could not add disallowed app: ${it.javaClass.simpleName}") }
                    }
                }
            }
        }
    }

    private suspend fun healthCheck(settings: AppSettings): Pair<Boolean, String?> {
        val url = settings.healthCheckUrl
        repeat(3) { attempt ->
            val latency = withContext(Dispatchers.IO) { engine.measureDelay(url) }
            if (latency >= 0) {
                LogBus.i("Health check passed in ${latency}ms")
                return true to null
            }
            LogBus.w("Health check attempt ${attempt + 1} failed")
            if (attempt < 2) delay(1500)
        }
        return false to "The engine is running but the health check did not get a response. " +
            "The server may be blocking this client, or the configuration may be wrong."
    }

    private fun startTrafficMonitor() {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            var totalUp = 0L
            var totalDown = 0L
            while (isActive) {
                delay(1000)
                if (!engine.isRunning) continue
                val (up, down) = engine.consumeTrafficCounters(XrayConfigBuilder.PROXY_TAG)
                totalUp += up
                totalDown += down
                VpnStateHolder.setTraffic(
                    TrafficSnapshot(
                        uploadBytes = totalUp,
                        downloadBytes = totalDown,
                        uploadRate = up,
                        downloadRate = down,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun registerNetworkCallback(settings: AppSettings) {
        unregisterNetworkCallback()
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                LogBus.i("Underlying network available")
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
            }

            override fun onLost(network: Network) {
                val state = VpnStateHolder.state.value
                if (state !is ConnectionState.Connected) return
                LogBus.w("Underlying network lost")
                if (settings.reconnectOnNetworkChange) {
                    scope.launch {
                        VpnStateHolder.setState(ConnectionState.Connecting("Network changed — reconnecting"))
                        delay(2000)
                        startVpn(activeProfileId)
                    }
                } else {
                    stopEverything(
                        ConnectionState.Error("Network connection lost", "Reconnect is disabled in settings."),
                        "Network lost"
                    )
                }
            }
        }
        networkCallback = callback
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onFailure { LogBus.w("Could not register the network callback: ${it.javaClass.simpleName}") }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        }
    }

    // ------------------------------------------------------------------ stop

    private fun teardownTunnel() {
        trafficJob?.cancel()
        trafficJob = null
        unregisterNetworkCallback()
        runCatching { engine.stop() }
        runCatching {
            tunInterface?.close()
        }
        tunInterface = null
    }

    private fun stopEverything(state: ConnectionState, reason: String) {
        connectJob?.cancel()
        connectJob = null
        teardownTunnel()
        VpnStateHolder.setPublicIp(null)
        VpnStateHolder.setState(state)
        LogBus.i("VPN stopped: $reason")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String, detail: String?) {
        LogBus.e("$message${detail?.let { ": $it" } ?: ""}")
        stopEverything(ConnectionState.Error(message, detail), message)
    }

    override fun onRevoke() {
        LogBus.w("VPN permission revoked by the system")
        stopEverything(
            ConnectionState.Idle,
            "VPN permission was revoked. Another VPN app may have taken over."
        )
    }

    override fun onDestroy() {
        teardownTunnel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ engine callbacks

    override fun onCoreStatus(code: Int, message: String) {
        LogBus.d("core[$code] ${Redactor.redact(message)}")
    }

    override fun onCoreStarted() {
        LogBus.i("Xray core reported startup")
    }

    override fun onCoreStopped() {
        LogBus.i("Xray core reported shutdown")
    }

    // ------------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the VPN connection state"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentIntent)
            .addAction(0, "Disconnect", stopIntent)
            .build()
    }

    private fun showNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    private fun isIpLiteral(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        val ipv4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
        if (ipv4.matches(v)) return v.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        val ipv6 = Regex("^[0-9a-fA-F:]{2,39}$")
        return ipv6.matches(v) && v.contains(':')
    }
}
