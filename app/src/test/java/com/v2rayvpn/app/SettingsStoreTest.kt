package com.v2rayvpn.app

import com.v2rayvpn.app.data.store.settingsFromJson
import com.v2rayvpn.app.data.store.toJson
import com.v2rayvpn.app.domain.model.AppSettings
import com.v2rayvpn.app.domain.model.DnsMode
import com.v2rayvpn.app.domain.model.PerAppMode
import com.v2rayvpn.app.domain.model.SurfaceStyle
import com.v2rayvpn.app.domain.model.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun settingsRoundTripKeepsThemeAndNetworkChoices() {
        val original = AppSettings(
            onboardingDone = true,
            themeAccent = ThemeAccent.EMERALD,
            surfaceStyle = SurfaceStyle.MIDNIGHT,
            dnsMode = DnsMode.SYSTEM,
            perAppMode = PerAppMode.ALLOW,
            perAppPackages = setOf("com.example.one", "com.example.two"),
            mtu = 1280,
            ipv6Enabled = false
        )
        val restored = settingsFromJson(original.toJson())
        assertEquals(ThemeAccent.EMERALD, restored.themeAccent)
        assertEquals(SurfaceStyle.MIDNIGHT, restored.surfaceStyle)
        assertEquals(DnsMode.SYSTEM, restored.dnsMode)
        assertEquals(PerAppMode.ALLOW, restored.perAppMode)
        assertEquals(setOf("com.example.one", "com.example.two"), restored.perAppPackages)
        assertEquals(1280, restored.mtu)
        assertEquals(false, restored.ipv6Enabled)
    }

    @Test
    fun unknownThemeValuesFallBackToDefaults() {
        val json = AppSettings(themeAccent = ThemeAccent.CYAN).toJson()
        json.put("themeAccent", "NOT_A_THEME")
        json.put("surfaceStyle", "NOT_A_STYLE")
        val restored = settingsFromJson(json)
        assertEquals(ThemeAccent.VIOLET, restored.themeAccent)
        assertEquals(SurfaceStyle.AMOLED, restored.surfaceStyle)
    }

    @Test
    fun mtuIsClampedToTheSupportedRange() {
        val json = AppSettings(mtu = 1500).toJson()
        json.put("mtu", 99999)
        assertTrue(settingsFromJson(json).mtu in 1280..9000)
    }
}
