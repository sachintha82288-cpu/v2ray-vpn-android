package com.v2rayvpn.app.domain.model

import java.util.UUID

enum class Protocol(val label: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    SOCKS("SOCKS"),
    HTTP("HTTP");

    companion object {
        fun fromScheme(scheme: String): Protocol? = when (scheme.lowercase()) {
            "vmess" -> VMESS
            "vless" -> VLESS
            "trojan" -> TROJAN
            "ss" -> SHADOWSOCKS
            "socks", "socks5" -> SOCKS
            "http", "https" -> HTTP
            else -> null
        }
    }
}

enum class Transport(val label: String, val xrayName: String) {
    TCP("TCP", "tcp"),
    WS("WebSocket", "ws"),
    GRPC("gRPC", "grpc"),
    HTTP2("HTTP/2", "http"),
    XHTTP("XHTTP", "xhttp"),
    QUIC("QUIC", "quic");

    companion object {
        fun fromXray(name: String?): Transport = when (name?.lowercase()) {
            "ws", "websocket" -> WS
            "grpc", "gun" -> GRPC
            "h2", "http", "http2" -> HTTP2
            "xhttp", "splithttp" -> XHTTP
            "quic" -> QUIC
            else -> TCP
        }
    }
}

enum class SecurityType(val label: String, val xrayName: String) {
    NONE("None", "none"),
    TLS("TLS", "tls"),
    REALITY("REALITY", "reality");

    companion object {
        fun fromXray(name: String?): SecurityType = when (name?.lowercase()) {
            "tls", "xtls" -> TLS
            "reality" -> REALITY
            else -> NONE
        }
    }
}

/**
 * A single proxy endpoint. Secrets are stored only inside the Keystore-encrypted local store.
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol,
    val server: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val username: String? = null,
    val alterId: Int = 0,
    val vmessSecurity: String = "auto",
    val vlessEncryption: String = "none",
    val flow: String? = null,
    val ssMethod: String? = null,
    val transport: Transport = Transport.TCP,
    val security: SecurityType = SecurityType.NONE,
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
    val realitySpiderX: String? = null,
    val wsPath: String? = null,
    val wsHost: String? = null,
    val grpcServiceName: String? = null,
    val grpcMultiMode: Boolean = false,
    val httpPath: String? = null,
    val httpHost: List<String> = emptyList(),
    val xhttpPath: String? = null,
    val xhttpHost: String? = null,
    val sourceUri: String? = null,
    val subscriptionId: String? = null,
    val subscriptionTitle: String? = null,
    val favorite: Boolean = false,
    val group: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val lastLatencyMs: Long? = null,
    val lastTestAt: Long? = null,
    val lastTestType: String? = null
) {
    /** Identity used for de-duplication. Contains no secrets beyond the credential already in the profile. */
    val fingerprintKey: String
        get() = listOf(
            protocol.name, server.lowercase(), port.toString(), transport.name, security.name,
            uuid ?: "", username ?: "", password ?: "", wsPath ?: "", grpcServiceName ?: "",
            sni ?: "", realityPublicKey ?: ""
        ).joinToString("|")

    val endpoint: String get() = "$server:$port"

    val credentialSummary: String
        get() = when (protocol) {
            Protocol.VMESS, Protocol.VLESS -> "UUID configured"
            Protocol.TROJAN, Protocol.SHADOWSOCKS -> "Password configured"
            Protocol.SOCKS, Protocol.HTTP -> if (username.isNullOrBlank()) "No authentication" else "Username/password"
        }
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Long? = null,
    val lastError: String? = null,
    val profileCount: Int = 0,
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiryEpochSeconds: Long? = null,
    val autoRefreshHours: Int = 0
)
