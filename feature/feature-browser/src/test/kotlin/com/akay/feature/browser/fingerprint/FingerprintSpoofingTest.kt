package com.akay.feature.browser.fingerprint

import com.akay.core.domain.model.FingerprintProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FingerprintSpoofingTest {

    @Test
    fun scriptMatchesTheProfileAndIsStable() {
        val config = config(seed = "device-alpha", desktop = false)
        val script = FingerprintSpoofing.buildScript(config)
        assertEquals(script, FingerprintSpoofing.buildScript(config))
        val profile = FingerprintProfile.fromSeed(config.seed, desktop = false)
        assertTrue(script.contains(FingerprintSpoofing.jsQuote(profile.userAgent)))
        assertTrue(script.contains(FingerprintSpoofing.jsQuote(profile.navigatorPlatform)))
        assertTrue(script.contains(FingerprintSpoofing.jsQuote(profile.gpuRenderer)))
        assertTrue(script.contains(FingerprintSpoofing.jsQuote(profile.gpuVendor)))
        assertTrue(script.contains("'hardwareConcurrency', ${profile.hardwareConcurrency}"))
        assertTrue(script.contains("'deviceMemory', ${profile.deviceMemory}"))
        assertTrue(script.contains("screen, 'width', ${profile.screenWidth}"))
        assertTrue(script.contains("maxTouchPoints', ${profile.maxTouchPoints}"))
        assertTrue(script.contains("iceTransportPolicy = 'relay'"))
        assertTrue(script.contains("importScripts"))
        assertTrue(script.contains("__axNoise"))
        assertFalse(script.contains("__axRand"))
        assertTrue(script.contains("toBlob"))
        assertTrue(script.contains("AudioBuffer"))
    }

    @Test
    fun togglesRemoveOnlyTheirOwnBlocks() {
        val bare = FingerprintSpoofing.buildScript(
            config(seed = "device-alpha", canvas = false, webGl = false, hardware = false, audio = false)
        )
        assertFalse(bare.contains("toDataURL"))
        assertFalse(bare.contains("getChannelData"))
        assertFalse(bare.contains("37445"))
        assertFalse(bare.contains("__axSanitizeFont"))
        assertTrue(bare.contains("iceTransportPolicy"))
        assertTrue(bare.contains("userAgent"))

        val webGl = FingerprintSpoofing.buildScript(
            config(seed = "device-alpha", canvas = false, webGl = true, hardware = false, audio = false)
        )
        assertTrue(webGl.contains("37445"))
        assertTrue(webGl.contains("37446"))
    }

    @Test
    fun timezoneIsInjectedOnlyWhenItIsSafe() {
        val zoned = FingerprintSpoofing.buildScript(
            config(seed = "device-alpha", timeZone = "America/New_York", locale = "en_US")
        )
        assertTrue(zoned.contains("America/New_York"))
        assertTrue(zoned.contains("en-US"))
        assertTrue(zoned.contains("getTimezoneOffset"))

        val hostile = FingerprintSpoofing.buildScript(
            config(seed = "device-alpha", timeZone = "America/New_York\";alert(1)", locale = "bad locale")
        )
        assertFalse(hostile.contains("alert"))
        assertFalse(hostile.contains("getTimezoneOffset"))
    }

    @Test
    fun desktopScriptDoesNotClaimToBeAPhone() {
        val script = FingerprintSpoofing.buildScript(config(seed = "device-alpha", desktop = true))
        val profile = FingerprintProfile.fromSeed("device-alpha", desktop = true)
        assertTrue(script.contains(FingerprintSpoofing.jsQuote(profile.userAgent)))
        assertTrue(script.contains("maxTouchPoints', 0"))
        assertFalse(script.contains("Mobile Safari"))
    }

    @Test
    fun jsQuoteEscapesCharactersThatWouldBreakTheScript() {
        assertEquals("\"a\\\\b\\\"c\\u003cscript>\"", FingerprintSpoofing.jsQuote("a\\b\"c<script>"))
    }

    private fun config(
        seed: String,
        desktop: Boolean = false,
        canvas: Boolean = true,
        webGl: Boolean = true,
        hardware: Boolean = true,
        audio: Boolean = true,
        timeZone: String? = null,
        locale: String? = null
    ) = FingerprintConfig(seed, canvas, webGl, hardware, audio, desktop, timeZone, locale)
}
