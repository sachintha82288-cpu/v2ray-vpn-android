package com.v2rayvpn.app

import com.v2rayvpn.app.data.parse.ShareUriBuilder
import com.v2rayvpn.app.data.parse.UriParser
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareUriBuilderTest {

    @Test
    fun vlessRoundTrips() {
        val original = UriParser.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
                "?encryption=none&security=reality&sni=www.example.com&fp=chrome&pbk=PUB&sid=12ab&type=grpc&serviceName=svc#Node"
        )
        val rebuilt = UriParser.parse(ShareUriBuilder.build(original))
        assertEquals(Protocol.VLESS, rebuilt.protocol)
        assertEquals("example.com", rebuilt.server)
        assertEquals(SecurityType.REALITY, rebuilt.security)
        assertEquals("PUB", rebuilt.realityPublicKey)
        assertEquals("12ab", rebuilt.realityShortId)
        assertEquals(Transport.GRPC, rebuilt.transport)
        assertEquals("svc", rebuilt.grpcServiceName)
    }

    @Test
    fun shadowsocksRoundTrips() {
        val credentials = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-256-gcm:pw".toByteArray())
        val original = UriParser.parse("ss://$credentials@ss.example.com:8388#Node")
        val rebuilt = UriParser.parse(ShareUriBuilder.build(original))
        assertEquals("aes-256-gcm", rebuilt.ssMethod)
        assertEquals("pw", rebuilt.password)
        assertEquals("ss.example.com", rebuilt.server)
    }

    @Test
    fun vmessExportStartsWithScheme() {
        val profile = UriParser.parse("trojan://pw@example.com:443?security=tls#T")
        val uri = ShareUriBuilder.build(profile)
        assertTrue(uri.startsWith("trojan://"))
    }
}
