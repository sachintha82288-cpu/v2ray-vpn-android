package com.v2rayvpn.app

import com.v2rayvpn.app.data.parse.B64
import com.v2rayvpn.app.data.parse.ConfigParseException
import com.v2rayvpn.app.data.parse.UriParser
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriParserTest {

    @Test
    fun parsesVlessWebSocketTls() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&sni=cdn.example.com&type=ws&path=%2Fws&host=edge.example.com#My%20Server"
        val profile = UriParser.parse(link)
        assertEquals(Protocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.server)
        assertEquals(443, profile.port)
        assertEquals(SecurityType.TLS, profile.security)
        assertEquals(Transport.WS, profile.transport)
        assertEquals("/ws", profile.wsPath)
        assertEquals("edge.example.com", profile.wsHost)
        assertEquals("cdn.example.com", profile.sni)
        assertEquals("My Server", profile.name)
    }

    @Test
    fun parsesVlessReality() {
        val link = "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443" +
            "?security=reality&sni=www.microsoft.com&fp=chrome&pbk=PUBKEY&sid=ab12&type=tcp&flow=xtls-rprx-vision#reality"
        val profile = UriParser.parse(link)
        assertEquals(SecurityType.REALITY, profile.security)
        assertEquals("PUBKEY", profile.realityPublicKey)
        assertEquals("ab12", profile.realityShortId)
        assertEquals("xtls-rprx-vision", profile.flow)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsRealityWithoutPublicKey() {
        UriParser.parse("vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443?security=reality&sni=a.com")
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsInvalidUuid() {
        UriParser.parse("vless://not-a-uuid@example.com:443?security=tls")
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsPortOutOfRange() {
        UriParser.parse("trojan://password@example.com:99999?security=tls")
    }

    @Test
    fun parsesVmessBase64() {
        val json = """
            {"v":"2","ps":"VMess Test","add":"vmess.example.com","port":"8443","id":"11111111-2222-3333-4444-555555555555",
             "aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn.example.com","path":"/ray","tls":"tls","sni":"cdn.example.com"}
        """.trimIndent()
        val profile = UriParser.parse("vmess://" + B64.encode(json))
        assertEquals(Protocol.VMESS, profile.protocol)
        assertEquals("vmess.example.com", profile.server)
        assertEquals(8443, profile.port)
        assertEquals(Transport.WS, profile.transport)
        assertEquals(SecurityType.TLS, profile.security)
        assertEquals("/ray", profile.wsPath)
        assertEquals("VMess Test", profile.name)
    }

    @Test
    fun parsesShadowsocksSip002() {
        val credentials = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-256-gcm:secret".toByteArray())
        val profile = UriParser.parse("ss://$credentials@ss.example.com:8388#SS%20Node")
        assertEquals(Protocol.SHADOWSOCKS, profile.protocol)
        assertEquals("aes-256-gcm", profile.ssMethod)
        assertEquals("secret", profile.password)
        assertEquals("SS Node", profile.name)
    }

    @Test
    fun parsesShadowsocksLegacy() {
        val legacy = java.util.Base64.getEncoder()
            .encodeToString("chacha20-ietf-poly1305:pw@1.2.3.4:8389".toByteArray())
        val profile = UriParser.parse("ss://$legacy#legacy")
        assertEquals("1.2.3.4", profile.server)
        assertEquals(8389, profile.port)
        assertEquals("pw", profile.password)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsShadowsocksPlugin() {
        UriParser.parse("ss://YWVzLTI1Ni1nY206cHc@1.2.3.4:8388?plugin=obfs-local%3Bobfs%3Dhttp#x")
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsUnsupportedCipher() {
        val credentials = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("rc4-md5:secret".toByteArray())
        UriParser.parse("ss://$credentials@ss.example.com:8388")
    }

    @Test
    fun parsesTrojan() {
        val profile = UriParser.parse("trojan://pass123@trojan.example.com:443?security=tls&sni=t.example.com&type=grpc&serviceName=grpc#T")
        assertEquals(Protocol.TROJAN, profile.protocol)
        assertEquals("pass123", profile.password)
        assertEquals(SecurityType.TLS, profile.security)
        assertEquals(Transport.GRPC, profile.transport)
        assertEquals("grpc", profile.grpcServiceName)
    }

    @Test
    fun parsesSocksWithAuthentication() {
        val profile = UriParser.parse("socks://user:p%40ss@10.0.0.5:1080#Local")
        assertEquals(Protocol.SOCKS, profile.protocol)
        assertEquals("user", profile.username)
        assertEquals("p@ss", profile.password)
    }

    @Test
    fun parsesIpv6Literal() {
        val profile = UriParser.parse("trojan://pw@[2001:db8::1]:443?security=tls")
        assertEquals("2001:db8::1", profile.server)
        assertEquals(443, profile.port)
    }

    @Test
    fun credentialSummaryNeverContainsSecret() {
        val profile = UriParser.parse("trojan://supersecret@example.com:443?security=tls")
        assertNotNull(profile.credentialSummary)
        assertTrue(!profile.credentialSummary.contains("supersecret"))
    }
}
