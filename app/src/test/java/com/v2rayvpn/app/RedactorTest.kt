package com.v2rayvpn.app

import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.core.util.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun removesUuids() {
        val out = Redactor.redact("user id 11111111-2222-3333-4444-555555555555 failed")
        assertFalse(out.contains("11111111"))
        assertTrue(out.contains("[uuid]"))
    }

    @Test
    fun removesShareLinks() {
        val out = Redactor.redact("importing vless://11111111-2222-3333-4444-555555555555@a.com:443?security=tls")
        assertFalse(out.contains("11111111"))
    }

    @Test
    fun removesKeyValueSecrets() {
        val out = Redactor.redact("config password=hunter2 uuid=abc123")
        assertFalse(out.contains("hunter2"))
        assertFalse(out.contains("abc123"))
    }

    @Test
    fun removesBearerTokens() {
        val out = Redactor.redact("Authorization: Bearer abc.def.ghi")
        assertFalse(out.contains("abc.def.ghi"))
    }

    @Test
    fun formatsBytesAndDurations() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.0 KB", Formatters.bytes(1024))
        assertEquals("00:05", Formatters.duration(5000))
        assertEquals("1:01:01", Formatters.duration(3661000))
        assertEquals("—", Formatters.latency(null))
        assertEquals("42 ms", Formatters.latency(42))
    }
}
