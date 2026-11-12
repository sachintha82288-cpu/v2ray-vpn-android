package com.v2rayvpn.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.v2rayvpn.app.core.log.LogBus

/** UI-side entry point for the VPN service. */
object VpnController {

    /** Returns an intent the activity must start for the system VPN consent dialog, or null when already granted. */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context, profileId: String?) {
        val intent = V2RayVpnService.startIntent(context, profileId)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            LogBus.e("Could not start the VPN service: ${e.javaClass.simpleName}")
        }
    }

    fun stop(context: Context) {
        val intent = V2RayVpnService.stopIntent(context)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            LogBus.e("Could not stop the VPN service: ${e.javaClass.simpleName}")
        }
    }
}
