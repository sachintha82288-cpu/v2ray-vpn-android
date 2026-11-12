package com.v2rayvpn.app.vpn

import com.v2rayvpn.app.domain.model.ConnectionState
import com.v2rayvpn.app.domain.model.TrafficSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide connection state shared by the VPN service and the UI. */
object VpnStateHolder {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _profileId = MutableStateFlow<String?>(null)
    val profileId: StateFlow<String?> = _profileId.asStateFlow()

    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp: StateFlow<String?> = _publicIp.asStateFlow()

    fun setState(state: ConnectionState) {
        _state.value = state
    }

    fun setTraffic(snapshot: TrafficSnapshot) {
        _traffic.value = snapshot
    }

    fun setProfileId(id: String?) {
        _profileId.value = id
    }

    fun setPublicIp(ip: String?) {
        _publicIp.value = ip
    }

    fun resetTraffic() {
        _traffic.value = TrafficSnapshot()
    }
}
