package com.v2rayvpn.app.data.parse

import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports a client-side Xray JSON configuration.
 *
 * Only single-outbound proxy configurations are supported. Anything that would need features this
 * client does not implement (balancers, observatory, chained outbounds, unsupported protocols) is
 * rejected with a message that names the reason, never silently downgraded.
 */
object XrayJsonImport {

    const val MAX_JSON_LENGTH = 512 * 1024

    private val supportedOutboundProtocols = setOf(
        "vmess", "vless", "trojan", "shadowsocks", "socks", "http"
    )

    fun parse(text: String, fallbackName: String? = null): Profile {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw ConfigParseException("Empty configuration")
        if (trimmed.length > MAX_JSON_LENGTH) {
            throw ConfigParseException("Configuration is larger than ${MAX_JSON_LENGTH / 1024} KB")
        }
        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw ConfigParseException("Invalid JSON configuration")
        }

        root.optJSONObject("routing")?.let { routing ->
            if (routing.has("balancers") && routing.optJSONArray("balancers")?.length() ?: 0 > 0) {
                throw ConfigParseException("Balancers are not supported by this client")
            }
        }
        if (root.has("observatory")) {
            throw ConfigParseException("Observatory-based outbound selection is not supported")
        }

        val outbounds = root.optJSONArray("outbounds")
            ?: throw ConfigParseException("Configuration has no outbounds")
        val proxyOutbound = (0 until outbounds.length())
            .mapNotNull { outbounds.optJSONObject(it) }
            .firstOrNull { it.optString("protocol").lowercase() in supportedOutboundProtocols }
            ?: throw ConfigParseException(
                "No supported outbound found. Supported protocols: VMess, VLESS, Trojan, Shadowsocks, SOCKS, HTTP"
            )

        val protocolName = proxyOutbound.optString("protocol").lowercase()
        val protocol = when (protocolName) {
            "vmess" -> Protocol.VMESS
            "vless" -> Protocol.VLESS
            "trojan" -> Protocol.TROJAN
            "shadowsocks" -> Protocol.SHADOWSOCKS
            "socks" -> Protocol.SOCKS
            else -> Protocol.HTTP
        }
        val settings = proxyOutbound.optJSONObject("settings") ?: JSONObject()
        val stream = proxyOutbound.optJSONObject("streamSettings")
        val transport = Transport.fromXray(stream?.optString("network"))
        val security = SecurityType.fromXray(stream?.optString("security"))
        val tls = stream?.optJSONObject("tlsSettings")
        val reality = stream?.optJSONObject("realitySettings")
        val ws = stream?.optJSONObject("wsSettings")
        val grpc = stream?.optJSONObject("grpcSettings")
        val httpSettings = stream?.optJSONObject("httpSettings")
        val xhttp = stream?.optJSONObject("xhttpSettings")

        val endpoint = when (protocol) {
            Protocol.VMESS, Protocol.VLESS -> settings.optJSONArray("vnext")?.optJSONObject(0)
            else -> settings.optJSONArray("servers")?.optJSONObject(0)
        } ?: throw ConfigParseException("Outbound has no server entry")

        val address = endpoint.optString("address")
        val port = endpoint.optInt("port", 0)
        if (address.isBlank()) throw ConfigParseException("Outbound has no server address")
        if (port !in 1..65535) throw ConfigParseException("Outbound has an invalid port")

        val user = endpoint.optJSONArray("users")?.optJSONObject(0)
        val name = fallbackName ?: proxyOutbound.optString("tag").takeIf { it.isNotBlank() } ?: "$address:$port"

        return Profile(
            name = name,
            protocol = protocol,
            server = address,
            port = port,
            uuid = when (protocol) {
                Protocol.VMESS, Protocol.VLESS -> user?.optString("id")?.takeIf { it.isNotBlank() }
                else -> null
            },
            alterId = user?.optInt("alterId", 0) ?: 0,
            vmessSecurity = user?.optString("security")?.takeIf { it.isNotBlank() } ?: "auto",
            vlessEncryption = user?.optString("encryption")?.takeIf { it.isNotBlank() } ?: "none",
            flow = user?.optString("flow")?.takeIf { it.isNotBlank() },
            password = when (protocol) {
                Protocol.TROJAN, Protocol.SHADOWSOCKS -> endpoint.optString("password").takeIf { it.isNotBlank() }
                Protocol.SOCKS, Protocol.HTTP -> user?.optString("pass")?.takeIf { it.isNotBlank() }
                else -> null
            },
            username = when (protocol) {
                Protocol.SOCKS, Protocol.HTTP -> user?.optString("user")?.takeIf { it.isNotBlank() }
                else -> null
            },
            ssMethod = endpoint.optString("method").takeIf { it.isNotBlank() },
            transport = transport,
            security = security,
            sni = tls?.optString("serverName")?.takeIf { it.isNotBlank() }
                ?: reality?.optString("serverName")?.takeIf { it.isNotBlank() },
            fingerprint = tls?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                ?: reality?.optString("fingerprint")?.takeIf { it.isNotBlank() },
            alpn = tls?.optJSONArray("alpn")?.toStringList() ?: emptyList(),
            allowInsecure = tls?.optBoolean("allowInsecure", false) ?: false,
            realityPublicKey = reality?.optString("publicKey")?.takeIf { it.isNotBlank() },
            realityShortId = reality?.optString("shortId")?.takeIf { it.isNotBlank() },
            realitySpiderX = reality?.optString("spiderX")?.takeIf { it.isNotBlank() },
            wsPath = ws?.optString("path")?.takeIf { it.isNotBlank() },
            wsHost = ws?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotBlank() },
            grpcServiceName = grpc?.optString("serviceName")?.takeIf { it.isNotBlank() },
            grpcMultiMode = grpc?.optBoolean("multiMode", false) ?: false,
            httpPath = httpSettings?.optString("path")?.takeIf { it.isNotBlank() },
            httpHost = httpSettings?.optJSONArray("host")?.toStringList() ?: emptyList(),
            xhttpPath = xhttp?.optString("path")?.takeIf { it.isNotBlank() },
            xhttpHost = xhttp?.optString("host")?.takeIf { it.isNotBlank() }
        )
    }

    fun looksLikeJson(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") && t.endsWith("}")
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}
