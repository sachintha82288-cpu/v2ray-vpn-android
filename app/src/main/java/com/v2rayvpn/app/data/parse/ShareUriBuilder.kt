package com.v2rayvpn.app.data.parse

import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import java.net.URLEncoder
import org.json.JSONObject

/** Rebuilds share links so a profile can be exported, copied or shown as a QR code. */
object ShareUriBuilder {

    fun build(profile: Profile): String = when (profile.protocol) {
        Protocol.VMESS -> buildVmess(profile)
        Protocol.VLESS -> buildVless(profile)
        Protocol.TROJAN -> buildTrojan(profile)
        Protocol.SHADOWSOCKS -> buildShadowsocks(profile)
        Protocol.SOCKS -> buildSocksHttp(profile, "socks")
        Protocol.HTTP -> buildSocksHttp(profile, "http")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun buildVmess(profile: Profile): String {
        val json = JSONObject()
            .put("v", "2")
            .put("ps", profile.name)
            .put("add", profile.server)
            .put("port", profile.port.toString())
            .put("id", profile.uuid ?: "")
            .put("aid", profile.alterId.toString())
            .put("scy", profile.vmessSecurity)
            .put("net", profile.transport.xrayName)
            .put("type", "none")
            .put("host", profile.wsHost ?: profile.httpHost.firstOrNull() ?: "")
            .put("path", profile.wsPath ?: profile.grpcServiceName ?: profile.httpPath ?: "")
            .put(
                "tls",
                when (profile.security) {
                    SecurityType.TLS -> "tls"
                    SecurityType.REALITY -> "reality"
                    SecurityType.NONE -> ""
                }
            )
            .put("sni", profile.sni ?: "")
            .put("alpn", profile.alpn.joinToString(","))
            .put("fp", profile.fingerprint ?: "")
        return "vmess://" + B64.encode(json.toString())
    }

    private fun buildVless(profile: Profile): String {
        val params = linkedMapOf(
            "encryption" to profile.vlessEncryption,
            "security" to profile.security.xrayName,
            "type" to profile.transport.xrayName
        )
        profile.sni?.let { params["sni"] = it }
        profile.fingerprint?.let { params["fp"] = it }
        profile.flow?.let { params["flow"] = it }
        profile.realityPublicKey?.let { params["pbk"] = it }
        profile.realityShortId?.let { params["sid"] = it }
        profile.realitySpiderX?.let { params["spx"] = it }
        profile.wsPath?.let { params["path"] = it }
        profile.wsHost?.let { params["host"] = it }
        profile.grpcServiceName?.let { params["serviceName"] = it }
        if (profile.alpn.isNotEmpty()) params["alpn"] = profile.alpn.joinToString(",")
        val query = params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        return "vless://${profile.uuid}@${profile.server}:${profile.port}?$query#${enc(profile.name)}"
    }

    private fun buildTrojan(profile: Profile): String {
        val params = linkedMapOf(
            "security" to profile.security.xrayName,
            "type" to profile.transport.xrayName
        )
        profile.sni?.let { params["sni"] = it }
        profile.fingerprint?.let { params["fp"] = it }
        profile.wsPath?.let { params["path"] = it }
        profile.wsHost?.let { params["host"] = it }
        profile.grpcServiceName?.let { params["serviceName"] = it }
        if (profile.alpn.isNotEmpty()) params["alpn"] = profile.alpn.joinToString(",")
        val query = params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        return "trojan://${enc(profile.password ?: "")}@${profile.server}:${profile.port}?$query#${enc(profile.name)}"
    }

    private fun buildShadowsocks(profile: Profile): String {
        val credentials = "${profile.ssMethod ?: "aes-256-gcm"}:${profile.password ?: ""}"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "ss://$encoded@${profile.server}:${profile.port}#${enc(profile.name)}"
    }

    private fun buildSocksHttp(profile: Profile, scheme: String): String {
        val auth = if (!profile.username.isNullOrBlank()) {
            "${enc(profile.username)}:${enc(profile.password ?: "")}@"
        } else ""
        return "$scheme://$auth${profile.server}:${profile.port}#${enc(profile.name)}"
    }
}
