package com.v2rayvpn.app

import com.v2rayvpn.app.data.parse.UriParser
import com.v2rayvpn.app.data.parse.XrayConfigBuilder
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.domain.model.RoutingMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    private val vless = UriParser.parse(
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&type=ws&path=%2Fws&sni=example.com#A"
    )

    @Test
    fun buildsTunInboundWithFdContract() {
        val config = JSONObject(XrayConfigBuilder.build(vless, AppSettings()))
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("protocol"))
        assertEquals(XrayConfigBuilder.TUN_TAG, inbound.getString("tag"))
        assertTrue(inbound.getJSONObject("sniffing").getBoolean("enabled"))
        assertEquals(1500, inbound.getJSONObject("settings").getInt("mtu"))
    }

    @Test
    fun proxyOutboundCarriesVlessUser() {
        val config = JSONObject(XrayConfigBuilder.build(vless, AppSettings()))
        val proxy = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", proxy.getString("protocol"))
        val user = proxy.getJSONObject("settings").getJSONArray("vnext")
            .getJSONObject(0).getJSONArray("users").getJSONObject(0)
        assertEquals("11111111-2222-3333-4444-555555555555", user.getString("id"))
        assertEquals("ws", proxy.getJSONObject("streamSettings").getString("network"))
        assertEquals("tls", proxy.getJSONObject("streamSettings").getString("security"))
    }

    @Test
    fun statisticsAreEnabled() {
        val config = JSONObject(XrayConfigBuilder.build(vless, AppSettings()))
        assertTrue(config.has("stats"))
        val system = config.getJSONObject("policy").getJSONObject("system")
        assertTrue(system.getBoolean("statsOutboundUplink"))
        assertTrue(system.getBoolean("statsOutboundDownlink"))
    }

    @Test
    fun dnsOutboundAndHijackRuleOnlyInXrayDnsMode() {
        val withDns = JSONObject(XrayConfigBuilder.build(vless, AppSettings(dnsMode = DnsMode.VPN)))
        val rules = withDns.getJSONObject("routing").getJSONArray("rules")
        var hijack = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.optString("outboundTag") == XrayConfigBuilder.DNS_OUT_TAG) hijack = true
        }
        assertTrue(hijack)

        val plain = JSONObject(XrayConfigBuilder.build(vless, AppSettings(dnsMode = DnsMode.SYSTEM)))
        val plainRules = plain.getJSONObject("routing").getJSONArray("rules")
        var plainHijack = false
        for (i in 0 until plainRules.length()) {
            if (plainRules.getJSONObject(i).optString("outboundTag") == XrayConfigBuilder.DNS_OUT_TAG) plainHijack = true
        }
        assertFalse(plainHijack)
    }

    @Test
    fun bypassLanAddsPrivateRuleAndGlobalDoesNot() {
        val bypass = JSONObject(XrayConfigBuilder.build(vless, AppSettings(routingMode = RoutingMode.BYPASS_LAN)))
        val global = JSONObject(XrayConfigBuilder.build(vless, AppSettings(routingMode = RoutingMode.GLOBAL)))
        assertTrue(bypass.toString().contains("geoip:private"))
        assertFalse(global.toString().contains("geoip:private"))
    }

    @Test
    fun shadowsocksOutboundUsesMethodAndPassword() {
        val credentials = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-128-gcm:pw".toByteArray())
        val ss = UriParser.parse("ss://$credentials@ss.example.com:8388")
        val config = JSONObject(XrayConfigBuilder.build(ss, AppSettings()))
        val server = config.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("aes-128-gcm", server.getString("method"))
        assertEquals("pw", server.getString("password"))
    }

    @Test
    fun noLocalProxyPortIsOpened() {
        val config = JSONObject(XrayConfigBuilder.build(vless, AppSettings()))
        val inbounds = config.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertNotNull(inbounds.getJSONObject(0).getJSONObject("settings"))
    }
}
