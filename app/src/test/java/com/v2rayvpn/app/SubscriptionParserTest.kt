package com.v2rayvpn.app

import com.v2rayvpn.app.data.parse.B64
import com.v2rayvpn.app.data.parse.ConfigParseException
import com.v2rayvpn.app.data.parse.SubscriptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {

    private val vlessA = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#A"
    private val vlessB = "vless://11111111-2222-3333-4444-555555555555@b.example.com:443?security=tls#B"

    @Test
    fun parsesBase64LinkList() {
        val body = B64.encode("$vlessA\n$vlessB\n")
        val result = SubscriptionParser.parse(body, "sub-1", "Provider")
        assertEquals(2, result.profiles.size)
        assertEquals("base64 link list", result.format)
        assertTrue(result.profiles.all { it.subscriptionId == "sub-1" })
    }

    @Test
    fun parsesPlainLinkList() {
        val result = SubscriptionParser.parse("$vlessA\n\n$vlessB", null, null)
        assertEquals(2, result.profiles.size)
        assertEquals("plain link list", result.format)
    }

    @Test
    fun deduplicatesIdenticalEntries() {
        val result = SubscriptionParser.parse("$vlessA\n$vlessA\n$vlessB", null, null)
        assertEquals(2, result.profiles.size)
    }

    @Test
    fun skipsUnparsableLinesButKeepsTheRest() {
        val result = SubscriptionParser.parse("$vlessA\ngarbage-line\n$vlessB", null, null)
        assertEquals(2, result.profiles.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun parsesJsonArrayOfConfigurations() {
        val body = """
            [
              {"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"t.example.com","port":443,"password":"pw"}]},
               "streamSettings":{"network":"tcp","security":"tls"}}]}
            ]
        """.trimIndent()
        val result = SubscriptionParser.parse(body, null, null)
        assertEquals(1, result.profiles.size)
        assertEquals("t.example.com", result.profiles[0].server)
    }

    @Test(expected = ConfigParseException::class)
    fun emptyBodyIsRejected() {
        SubscriptionParser.parse("   ", null, null)
    }

    @Test(expected = ConfigParseException::class)
    fun bodyWithoutUsableConfigurationIsRejected() {
        SubscriptionParser.parse("hello world", null, null)
    }

    @Test
    fun parsesSubscriptionUserInfo() {
        val info = SubscriptionParser.parseUserInfo("upload=1024; download=2048; total=4096; expire=1893456000")
        assertNotNull(info)
        assertEquals(1024L, info!!.uploadBytes)
        assertEquals(2048L, info.downloadBytes)
        assertEquals(4096L, info.totalBytes)
        assertEquals(1893456000L, info.expiryEpochSeconds)
    }

    @Test
    fun missingUserInfoHeaderYieldsNull() {
        assertNull(SubscriptionParser.parseUserInfo(null))
        assertNull(SubscriptionParser.parseUserInfo(""))
        assertNull(SubscriptionParser.parseUserInfo("nonsense"))
    }
}
