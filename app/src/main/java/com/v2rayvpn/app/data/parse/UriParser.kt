package com.v2rayvpn.app.data.parse

import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import java.net.URLDecoder
import org.json.JSONObject

/**
 * Parses share links into [Profile] objects.
 *
 * Everything here treats its input as untrusted: sizes are bounded, ports and addresses are
 * validated, and unsupported fields (for example Shadowsocks plugins) are rejected with a clear
 * message instead of being silently ignored.
 */
object UriParser {

    private const val MAX_LINK_LENGTH = 16 * 1024

    private val uuidRegex = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /** Ciphers accepted by the pinned Xray-core build. */
    private val supportedSsCiphers = setOf(
        "aes-128-gcm", "aead_aes_128_gcm",
        "aes-256-gcm", "aead_aes_256_gcm",
        "chacha20-poly1305", "chacha20-ietf-poly1305", "aead_chacha20_poly1305",
        "xchacha20-poly1305", "xchacha20-ietf-poly1305", "aead_xchacha20_poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305", "none", "plain"
    )

    fun looksLikeUri(text: String): Boolean {
        val scheme = text.trim().substringBefore("://", "").lowercase()
        return Protocol.fromScheme(scheme) != null
    }

    fun parse(link: String): Profile {
        val raw = link.trim()
        if (raw.isEmpty()) throw ConfigParseException("Empty configuration link")
        if (raw.length > MAX_LINK_LENGTH) throw ConfigParseException("Configuration link is too long")
        val scheme = raw.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vmess" -> parseVmess(raw)
            "vless" -> parseVless(raw)
            "trojan" -> parseTrojan(raw)
            "ss" -> parseShadowsocks(raw)
            "socks", "socks5" -> parseSocksOrHttp(raw, Protocol.SOCKS)
            "http", "https" -> parseSocksOrHttp(raw, Protocol.HTTP)
            "" -> throw ConfigParseException("Not a configuration link (missing scheme)")
            else -> throw ConfigParseException("Unsupported link scheme: $scheme")
        }
    }

    // ---------------------------------------------------------------- helpers

    private data class Parts(
        val userInfo: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String
    )

    private fun split(link: String): Parts {
        val withoutScheme = link.substringAfter("://")
        val fragment = withoutScheme.substringAfterLast('#', "")
        val beforeFragment = withoutScheme.substringBefore('#')
        val queryString = beforeFragment.substringAfter('?', "")
        val authority = beforeFragment.substringBefore('?')
        val userInfo = if (authority.contains('@')) authority.substringBeforeLast('@') else ""
        val hostPort = if (authority.contains('@')) authority.substringAfterLast('@') else authority
        if (hostPort.isBlank()) throw ConfigParseException("Missing server address")
        val host: String
        val portText: String
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) throw ConfigParseException("Malformed IPv6 address")
            host = hostPort.substring(1, close)
            portText = hostPort.substring(close + 1).removePrefix(":")
        } else {
            host = hostPort.substringBeforeLast(':', "")
            portText = hostPort.substringAfterLast(':', "")
        }
        if (host.isBlank()) throw ConfigParseException("Missing server address")
        val port = portText.toIntOrNull() ?: throw ConfigParseException("Missing or invalid port")
        if (port !in 1..65535) throw ConfigParseException("Port out of range: $port")
        val query = if (queryString.isBlank()) emptyMap() else queryString
            .split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val key = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', ""))
                key to value
            }
            .toMap()
        return Parts(userInfo, host, port, query, decode(fragment))
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun requireUuid(value: String?, field: String): String {
        if (value.isNullOrBlank()) throw ConfigParseException("Missing $field")
        if (!uuidRegex.matches(value)) throw ConfigParseException("Invalid $field (expected a UUID)")
        return value
    }

    private fun nameOr(parts: Parts, fallback: String): String =
        parts.fragment.takeIf { it.isNotBlank() } ?: fallback

    private fun transportFrom(type: String?): Transport = Transport.fromXray(type)

    private fun securityFrom(value: String?): SecurityType = SecurityType.fromXray(value)

    // ---------------------------------------------------------------- protocols

    private fun parseVmess(link: String): Profile {
        val payload = link.substringAfter("://")
        val json = B64.decodeToString(payload)
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ConfigParseException("Malformed VMess payload")
        }
        val add = obj.optString("add").ifBlank { throw ConfigParseException("VMess link has no address") }
        val port = obj.optString("port").toIntOrNull() ?: throw ConfigParseException("VMess link has no valid port")
        if (port !in 1..65535) throw ConfigParseException("Port out of range: $port")
        val id = requireUuid(obj.optString("id"), "UUID")
        val net = obj.optString("net", "tcp")
        val tls = obj.optString("tls", "")
        val host = obj.optString("host", "")
        val path = obj.optString("path", "")
        val sni = obj.optString("sni", "").ifBlank { obj.optString("peer", "") }
        val transport = transportFrom(net)
        return Profile(
            name = obj.optString("ps", "").ifBlank { "$add:$port" },
            protocol = Protocol.VMESS,
            server = add,
            port = port,
            uuid = id,
            alterId = obj.optString("aid", "0").toIntOrNull() ?: 0,
            vmessSecurity = obj.optString("scy", "auto").ifBlank { "auto" },
            transport = transport,
            security = if (tls.equals("tls", true) || tls.equals("reality", true)) {
                securityFrom(tls)
            } else SecurityType.NONE,
            sni = sni.ifBlank { null },
            fingerprint = obj.optString("fp", "").ifBlank { null },
            alpn = obj.optString("alpn", "").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            wsPath = if (transport == Transport.WS) path.ifBlank { "/" } else null,
            wsHost = if (transport == Transport.WS) host.ifBlank { null } else null,
            grpcServiceName = if (transport == Transport.GRPC) path.ifBlank { null } else null,
            httpPath = if (transport == Transport.HTTP2) path.ifBlank { "/" } else null,
            httpHost = if (transport == Transport.HTTP2 && host.isNotBlank()) listOf(host) else emptyList(),
            sourceUri = link
        )
    }

    private fun parseVless(link: String): Profile {
        val parts = split(link)
        val uuid = requireUuid(parts.userInfo.substringBefore(':').ifBlank { null }, "UUID")
        val transport = transportFrom(parts.query["type"])
        val security = securityFrom(parts.query["security"])
        val sni = parts.query["sni"] ?: parts.query["host"]?.takeIf { transport != Transport.WS }
        val flow = parts.query["flow"]?.takeIf { it.isNotBlank() }
        if (flow != null && flow !in setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")) {
            throw ConfigParseException("Unsupported VLESS flow: $flow")
        }
        if (security == SecurityType.REALITY && parts.query["pbk"].isNullOrBlank()) {
            throw ConfigParseException("REALITY link is missing the public key (pbk)")
        }
        return Profile(
            name = nameOr(parts, "${parts.host}:${parts.port}"),
            protocol = Protocol.VLESS,
            server = parts.host,
            port = parts.port,
            uuid = uuid,
            vlessEncryption = parts.query["encryption"] ?: "none",
            flow = flow,
            transport = transport,
            security = security,
            sni = sni?.takeIf { it.isNotBlank() },
            fingerprint = parts.query["fp"]?.takeIf { it.isNotBlank() },
            alpn = parts.query["alpn"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            realityPublicKey = parts.query["pbk"]?.takeIf { it.isNotBlank() },
            realityShortId = parts.query["sid"]?.takeIf { it.isNotBlank() },
            realitySpiderX = parts.query["spx"]?.takeIf { it.isNotBlank() },
            wsPath = if (transport == Transport.WS) (parts.query["path"] ?: "/") else null,
            wsHost = if (transport == Transport.WS) parts.query["host"]?.takeIf { it.isNotBlank() } else null,
            grpcServiceName = if (transport == Transport.GRPC) parts.query["serviceName"]?.takeIf { it.isNotBlank() } else null,
            grpcMultiMode = parts.query["mode"] == "multi",
            httpPath = if (transport == Transport.HTTP2) (parts.query["path"] ?: "/") else null,
            httpHost = if (transport == Transport.HTTP2) {
                parts.query["host"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            } else emptyList(),
            xhttpPath = if (transport == Transport.XHTTP) (parts.query["path"] ?: "/") else null,
            xhttpHost = if (transport == Transport.XHTTP) parts.query["host"]?.takeIf { it.isNotBlank() } else null,
            sourceUri = link
        )
    }

    private fun parseTrojan(link: String): Profile {
        val parts = split(link)
        val password = parts.userInfo.substringBefore(':')
        if (password.isBlank()) throw ConfigParseException("Trojan link has no password")
        val transport = transportFrom(parts.query["type"])
        val security = when {
            parts.query["security"].isNullOrBlank() -> SecurityType.TLS
            else -> securityFrom(parts.query["security"])
        }
        return Profile(
            name = nameOr(parts, "${parts.host}:${parts.port}"),
            protocol = Protocol.TROJAN,
            server = parts.host,
            port = parts.port,
            password = password,
            transport = transport,
            security = security,
            sni = (parts.query["sni"] ?: parts.query["peer"])?.takeIf { it.isNotBlank() },
            fingerprint = parts.query["fp"]?.takeIf { it.isNotBlank() },
            alpn = parts.query["alpn"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            allowInsecure = parts.query["allowInsecure"] == "1",
            realityPublicKey = parts.query["pbk"]?.takeIf { it.isNotBlank() },
            realityShortId = parts.query["sid"]?.takeIf { it.isNotBlank() },
            realitySpiderX = parts.query["spx"]?.takeIf { it.isNotBlank() },
            wsPath = if (transport == Transport.WS) (parts.query["path"] ?: "/") else null,
            wsHost = if (transport == Transport.WS) parts.query["host"]?.takeIf { it.isNotBlank() } else null,
            grpcServiceName = if (transport == Transport.GRPC) parts.query["serviceName"]?.takeIf { it.isNotBlank() } else null,
            httpPath = if (transport == Transport.HTTP2) (parts.query["path"] ?: "/") else null,
            httpHost = if (transport == Transport.HTTP2) {
                parts.query["host"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            } else emptyList(),
            sourceUri = link
        )
    }

    private fun parseShadowsocks(link: String): Profile {
        val body = link.substringAfter("://")
        if (body.contains("plugin=", ignoreCase = true)) {
            throw ConfigParseException("Shadowsocks plugins (obfs/v2ray-plugin) are not supported")
        }
        val fragment = body.substringAfterLast('#', "")
        val beforeFragment = body.substringBefore('#')
        val query = beforeFragment.substringAfter('?', "")
        val authority = beforeFragment.substringBefore('?')
        var method: String
        var password: String
        var host: String
        var port: Int
        if (authority.contains('@')) {
            val userInfo = authority.substringBeforeLast('@')
            val decoded = if (userInfo.contains(':')) userInfo else B64.decodeToString(userInfo)
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':', "")
            val hostPort = authority.substringAfterLast('@')
            host = hostPort.substringBeforeLast(':', "")
            port = hostPort.substringAfterLast(':', "").toIntOrNull()
                ?: throw ConfigParseException("Shadowsocks link has no valid port")
        } else {
            val decoded = B64.decodeToString(authority)
            val at = decoded.lastIndexOf('@')
            if (at <= 0) throw ConfigParseException("Malformed Shadowsocks payload")
            val credentials = decoded.substring(0, at)
            method = credentials.substringBefore(':')
            password = credentials.substringAfter(':', "")
            val hostPort = decoded.substring(at + 1)
            host = hostPort.substringBeforeLast(':', "")
            port = hostPort.substringAfterLast(':', "").toIntOrNull()
                ?: throw ConfigParseException("Shadowsocks link has no valid port")
        }
        if (host.isBlank()) throw ConfigParseException("Shadowsocks link has no address")
        if (port !in 1..65535) throw ConfigParseException("Port out of range: $port")
        if (password.isEmpty()) throw ConfigParseException("Shadowsocks link has no password")
        val cipher = method.lowercase()
        if (cipher !in supportedSsCiphers) {
            throw ConfigParseException("Unsupported Shadowsocks cipher: $method")
        }
        if (query.isNotBlank()) {
            // Only "type" style parameters are tolerated; anything that changes transport is rejected.
            val plugin = query.split('&').any { it.startsWith("plugin=", ignoreCase = true) }
            if (plugin) throw ConfigParseException("Shadowsocks plugins are not supported")
        }
        return Profile(
            name = decode(fragment).ifBlank { "$host:$port" },
            protocol = Protocol.SHADOWSOCKS,
            server = host,
            port = port,
            password = password,
            ssMethod = cipher,
            sourceUri = link
        )
    }

    private fun parseSocksOrHttp(link: String, protocol: Protocol): Profile {
        val parts = split(link)
        val username: String?
        val password: String?
        if (parts.userInfo.isBlank()) {
            username = null
            password = null
        } else {
            username = decode(parts.userInfo.substringBefore(':'))
            password = if (parts.userInfo.contains(':')) decode(parts.userInfo.substringAfter(':')) else null
        }
        if (username.isNullOrBlank() && protocol == Protocol.SOCKS) {
            // unauthenticated SOCKS is valid
        }
        return Profile(
            name = nameOr(parts, "${parts.host}:${parts.port}"),
            protocol = protocol,
            server = parts.host,
            port = parts.port,
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() },
            transport = Transport.TCP,
            security = if (protocol == Protocol.HTTP && link.startsWith("https://", true)) {
                SecurityType.TLS
            } else SecurityType.NONE,
            sourceUri = link
        )
    }
}
