package com.v2rayvpn.app.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.v2rayvpn.app.MainActivity
import com.v2rayvpn.app.core.AppContainer
import com.v2rayvpn.app.core.log.LogBus
import com.v2rayvpn.app.domain.model.ConnectionState

/**
 * Quick Settings tile.
 *
 * It can only *toggle* an already-authorised VPN. Android requires an activity for the first-time
 * consent dialog, so if permission is missing the tile opens the app instead of pretending to
 * connect.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun { toggle() }
    }

    private fun toggle() {
        val state = VpnStateHolder.state.value
        if (state is ConnectionState.Connected ||
            state is ConnectionState.Connecting ||
            state is ConnectionState.Preparing
        ) {
            VpnController.stop(this)
            updateTile()
            return
        }

        val consent = VpnService.prepare(this)
        if (consent != null) {
            LogBus.i("Tile opened the app: VPN consent has not been granted yet")
            openApp()
            return
        }

        val selected = AppContainer.get().repository.settings.value.selectedProfileId
        VpnController.start(this, selected)
        updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val active = when (VpnStateHolder.state.value) {
            is ConnectionState.Connected -> true
            is ConnectionState.Connecting -> true
            is ConnectionState.Preparing -> true
            else -> false
        }
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "V2RAY VPN"
        tile.contentDescription = if (active) "V2RAY VPN connected" else "V2RAY VPN disconnected"
        tile.updateTile()
    }
}
