package com.v2rayvpn.app.domain.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Preparing(val message: String) : ConnectionState
    data class Connecting(val message: String) : ConnectionState
    data class Connected(
        val since: Long,
        val profileName: String,
        val healthCheckOk: Boolean = true,
        val healthCheckMessage: String? = null
    ) : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val message: String, val detail: String? = null) : ConnectionState

    val isActive: Boolean
        get() = this is Connecting || this is Connected || this is Preparing

    val label: String
        get() = when (this) {
            Idle -> "Disconnected"
            is Preparing -> "Preparing"
            is Connecting -> "Connecting"
            is Connected -> "Connected"
            Disconnecting -> "Disconnecting"
            is Error -> "Connection error"
        }
}

data class TrafficSnapshot(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadRate: Long = 0,
    val downloadRate: Long = 0,
    val timestamp: Long = 0
)

data class LatencyResult(
    val profileId: String,
    val latencyMs: Long?,
    val error: String?,
    val testedAt: Long,
    val testType: String
)
