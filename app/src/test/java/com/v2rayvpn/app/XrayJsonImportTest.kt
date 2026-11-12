package com.v2rayvpn.app

import com.v2rayvpn.app.data.parse.ConfigParseException
import com.v2rayvpn.app.data.parse.XrayJsonImport
import com.v2rayvpn.app.domain.model.Protocol
import com.v2rayvpn.app.domain.model.SecurityType
import com.v2rayvpn.app.domain.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class XrayJsonImportTest {

    @Test
    fun importsVmessOutbound() {
        val json = """
            {
              "outbounds": [
                {"tag":"proxy","protocol":"vmess",
                 "settings":{"vnext":[{"address":"v.example.com","port":8443,
                   "users":[{"id":"11111111-2222-3333-4444-555555555555","alterId":0,"security":"auto"}]}]},
                 "streamSettings":{"network":"ws","security":"tls",
                   "wsSettings":{"path":"/path","headers":{"Host":"h.example.com"}},
                   "tlsSettings":{"serverName":"h.example.com"}}}
              ]
            }
        """.trimIndent()
        val profile = XrayJsonImport.parse(json)
        assertEquals(Protocol.VMESS, profile.protocol)
        assertEquals("v.example.com", profile.server)
        assertEquals(8443, profile.port)
        assertEquals(Transport.WS, profile.transport)
        assertEquals(SecurityType.TLS, profile.security)
        assertEquals("/path", profile.wsPath)
        assertEquals("h.example.com", profile.wsHost)
        assertEquals("proxy", profile.name)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsBalancers() {
        val json = """
            {
              "routing":{"balancers":[{"tag":"bal"}]},
              "outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"a","port":443,
                "users":[{"id":"11111111-2222-3333-4444-555555555555"}]}]}}]
            }
        """.trimIndent()
        XrayJsonImport.parse(json)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsUnsupportedProtocol() {
        val json = """{"outbounds":[{"protocol":"wireguard","settings":{}}]}"""
        XrayJsonImport.parse(json)
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsInvalidJson() {
        XrayJsonImport.parse("{not json}")
    }

    @Test(expected = ConfigParseException::class)
    fun rejectsOversizedConfiguration() {
        val big = "{\"padding\":\"" + "a".repeat(XrayJsonImport.MAX_JSON_LENGTH) + "\"}"
        XrayJsonImport.parse(big)
    }
}
