package com.v2rayvpn.app.data.parse

import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.domain.model.Profile
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.RoutingMode
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Xray-core JSON configuration that the VPN service hands to the engine.
 *
 * The inbound is Xray's own TUN inbound, which takes the Android file descriptor from the
 * `xray.tun.fd` environment variable (set by the engine binding when the loop starts). The VPN
 * service owns that descriptor and the Android side of the interface.
 *
 * Layout follows the verified Android integration contract:
 *  - `tun` inbound, sniffing enabled, no local SOCKS/HTTP port is opened;
 *  - outbounds: the user's proxy, `direct`, `block`, and `dns-out` when the Xray DNS module is used;
 *  - statistics enabled for real traffic counters.
 */
object XrayConfigBuilder {

    const val TUN_TAG = "tun"
    const val PROXY_TAG = "proxy"
    const val DIRECT_TAG = "direct"
    const val BLOCK_TAG = "block"
    const val DNS_OUT_TAG = "dns-out"

    fun build(profile: Profile, settings: AppSettings): String {
        val root = JSONObject()

        root.put("log", JSONObject().put("loglevel", settings.logLevel))
        root.put("stats", JSONObject())
        root.put(
            "policy",
            JSONObject()
                .put(
                    "levels",
                    JSONObject().put(
                        "8",
                        JSONObject()
                            .put("handshake", 4)
                            .put("connIdle", 300)
                            .put("uplinkOnly", 1)
                            .put("downlinkOnly", 1)
                    )
                )
                .put(
                    "system",
                    JSONObject()
                        .put("statsOutboundUplink", true)
                        .put("statsOutboundDownlink", true)
                )
        )

        if (settings.dnsMode == DnsMode.VPN) {
            root.put("dns", buildDns(settings))
        }

        root.put("inbounds", JSONArray().put(buildTunInbound(settings)))
        root.put("outbounds", buildOutbounds(profile, settings))
        root.put("routing", buildRouting(settings))
        return root.toString()
    }

    private fun buildTunInbound(settings: AppSettings): JSONObject {
        val tunSettings = JSONObject()
            .put("name", "xray0")
            .put("mtu", settings.mtu)
            .put("userLevel", 8)
        return JSONObject()
            .put("tag", TUN_TAG)
            .put("protocol", "tun")
            .put("settings", tunSettings)
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            )
    }

    private fun buildDns(settings: AppSettings): JSONObject {
        val servers = JSONArray().put(JSONObject().put("address", settings.remoteDns))
        if (settings.localDns.isNotBlank() && settings.localDns != settings.remoteDns) {
            servers.put(
                JSONObject()
                    .put("address", settings.localDns)
                    .put("domains", JSONArray().put("geosite:private"))
            )
        }
        return JSONObject()
            .put("servers", servers)
            .put("queryStrategy", "UseIP")
    }

    private fun buildOutbounds(profile: Profile, settings: AppSettings): JSONArray {
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(profile))
        outbounds.put(
            JSONObject()
                .put("tag", DIRECT_TAG)
                .put("protocol", "freedom")
                .put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        JSONObject().put("domainStrategy", "UseIP")
                    )
                )
        )
        outbounds.put(
            JSONObject()
                .put("tag", BLOCK_TAG)
                .put("protocol", "blackhole")
                .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
        )
        if (settings.dnsMode == DnsMode.VPN) {
            outbounds.put(JSONObject().put("tag", DNS_OUT_TAG).put("protocol", "dns"))
        }
        return outbounds
    }

    private fun buildRouting(settings: AppSettings): JSONObject {
        val rules = JSONArray()
        if (settings.dnsMode == DnsMode.VPN) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put(TUN_TAG))
                    .put("port", "53")
                    .put("outboundTag", DNS_OUT_TAG)
            )
        }
        if (settings.blockAdsAndTrackers) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray().put("geosite:category-ads-all"))
                    .put("outboundTag", BLOCK_TAG)
            )
        }
        if (settings.routingMode == RoutingMode.BYPASS_LAN) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("outboundTag", DIRECT_TAG)
            )
        }
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "tcp,udp")
                .put("outboundTag", PROXY_TAG)
        )
        return JSONObject()
            .put("domainStrategy", "AsIs")
            .put("rules", rules)
    }

    private fun buildProxyOutbound(profile: Profile): JSONObject {
        val outbound = JSONObject().put("tag", PROXY_TAG)
        when (profile.protocol) {
            Protocol.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.server)
                                .put("port", profile.port)
                                .put(
                                    "users",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("id", profile.uuid)
                                            .put("alterId", profile.alterId)
                                            .put("security", profile.vmessSecurity)
                                            .put("level", 8)
                                    )
                                )
                        )
                    )
                )
            }

            Protocol.VLESS -> {
                outbound.put("protocol", "vless")
                val user = JSONObject()
                    .put("id", profile.uuid)
                    .put("encryption", profile.vlessEncryption.ifBlank { "none" })
                    .put("level", 8)
                profile.flow?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.server)
                                .put("port", profile.port)
                                .put("users", JSONArray().put(user))
                        )
                    )
                )
            }

            Protocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.server)
                                .put("port", profile.port)
                                .put("password", profile.password)
                                .put("level", 8)
                        )
                    )
                )
            }

            Protocol.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.server)
                                .put("port", profile.port)
                                .put("method", profile.ssMethod ?: "aes-256-gcm")
                                .put("password", profile.password)
                                .put("level", 8)
                        )
                    )
                )
            }

            Protocol.SOCKS, Protocol.HTTP -> {
                val server = JSONObject()
                    .put("address", profile.server)
                    .put("port", profile.port)
                    .put("level", 8)
                if (!profile.username.isNullOrBlank()) {
                    server.put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("user", profile.username)
                                .put("pass", profile.password ?: "")
                                .put("level", 8)
                        )
                    )
                }
                outbound.put("protocol", if (profile.protocol == Protocol.SOCKS) "socks" else "http")
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(server)))
            }
        }
        outbound.put("streamSettings", buildStreamSettings(profile))
        outbound.put("mux", JSONObject().put("enabled", false))
        return outbound
    }

    private fun buildStreamSettings(profile: Profile): JSONObject {
        val stream = JSONObject()
        stream.put("network", profile.transport.xrayName)
        stream.put("security", profile.security.xrayName)

        when (profile.security) {
            SecurityType.TLS -> {
                val tls = JSONObject()
                tls.put("serverName", profile.sni ?: profile.server)
                tls.put("allowInsecure", profile.allowInsecure)
                profile.fingerprint?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                if (profile.alpn.isNotEmpty()) {
                    tls.put("alpn", JSONArray(profile.alpn))
                }
                stream.put("tlsSettings", tls)
            }

            SecurityType.REALITY -> {
                val reality = JSONObject()
                reality.put("serverName", profile.sni ?: profile.server)
                reality.put("fingerprint", profile.fingerprint ?: "chrome")
                reality.put("publicKey", profile.realityPublicKey ?: "")
                reality.put("shortId", profile.realityShortId ?: "")
                reality.put("spiderX", profile.realitySpiderX ?: "/")
                stream.put("realitySettings", reality)
            }

            SecurityType.NONE -> Unit
        }

        when (profile.transport) {
            Transport.WS -> {
                val ws = JSONObject().put("path", profile.wsPath ?: "/")
                profile.wsHost?.takeIf { it.isNotBlank() }?.let {
                    ws.put("headers", JSONObject().put("Host", it))
                }
                stream.put("wsSettings", ws)
            }

            Transport.GRPC -> {
                stream.put(
                    "grpcSettings",
                    JSONObject()
                        .put("serviceName", profile.grpcServiceName ?: "")
                        .put("multiMode", profile.grpcMultiMode)
                )
            }

            Transport.HTTP2 -> {
                val http = JSONObject().put("path", profile.httpPath ?: "/")
                if (profile.httpHost.isNotEmpty()) {
                    http.put("host", JSONArray(profile.httpHost))
                }
                stream.put("httpSettings", http)
            }

            Transport.XHTTP -> {
                val xhttp = JSONObject().put("path", profile.xhttpPath ?: "/")
                profile.xhttpHost?.takeIf { it.isNotBlank() }?.let { xhttp.put("host", it) }
                stream.put("xhttpSettings", xhttp)
            }

            Transport.QUIC -> stream.put(
                "quicSettings",
                JSONObject()
                    .put("security", profile.sni ?: "")
                    .put("key", "")
                    .put("header", JSONObject().put("type", "none"))
            )

            Transport.TCP -> Unit
        }
        return stream
    }
}
