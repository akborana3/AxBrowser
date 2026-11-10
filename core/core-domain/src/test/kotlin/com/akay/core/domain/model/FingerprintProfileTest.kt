package com.akay.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FingerprintProfileTest {

    @Test
    fun sameSeedIsStableAndAgreesWithItself() {
        val first = FingerprintProfile.fromSeed("device-alpha", desktop = false)
        val second = FingerprintProfile.fromSeed("device-alpha", desktop = false)
        assertEquals(first, second)
        assertTrue(first.userAgent.contains(first.model))
        assertTrue(first.userAgent.contains("Android ${first.androidVersion}"))
        assertTrue(first.userAgent.contains("Mobile"))
        assertFalse(first.userAgent.contains(" wv"))
        assertEquals("Linux armv8l", first.navigatorPlatform)
        assertEquals("Android", first.clientPlatform)
        assertEquals(first.model, first.clientModel)
        assertEquals(first.androidVersion + ".0.0", first.clientPlatformVersion)
        assertEquals("arm", first.clientArchitecture)
        assertTrue(first.mobile)
        assertEquals(5, first.maxTouchPoints)
        assertTrue(first.deviceMemory == 4 || first.deviceMemory == 8)
        assertTrue(first.screenWidth in 300..500)
        assertTrue(first.screenHeight > first.availHeight)
        assertTrue(first.gpuRenderer.isNotBlank() && first.gpuVendor.isNotBlank())
        assertTrue(first.noiseSeed in 0..99_999)
    }

    @Test
    fun desktopProfileMatchesTheDesktopUserAgent() {
        val profile = FingerprintProfile.fromSeed("device-alpha", desktop = true)
        assertEquals(FingerprintProfile.DESKTOP_USER_AGENT, profile.userAgent)
        assertFalse(profile.userAgent.contains("Mobile"))
        assertFalse(profile.userAgent.contains("Android"))
        assertEquals("Linux x86_64", profile.navigatorPlatform)
        assertEquals("Linux", profile.clientPlatform)
        assertEquals("", profile.clientModel)
        assertFalse(profile.mobile)
        assertEquals(0, profile.maxTouchPoints)
        assertEquals(1920, profile.screenWidth)
        assertEquals(8, profile.hardwareConcurrency)
        assertEquals(8, profile.deviceMemory)
        val otherSeed = FingerprintProfile.fromSeed("device-beta", desktop = true)
        assertEquals(profile.userAgent, otherSeed.userAgent)
        assertEquals(profile.gpuRenderer, otherSeed.gpuRenderer)
    }

    @Test
    fun differentSeedsCanProduceDifferentPhones() {
        val profiles = (0 until 40).map { FingerprintProfile.fromSeed("seed-$it", desktop = false) }
        assertTrue(profiles.map { it.model }.distinct().size > 1)
        assertTrue(profiles.all { it.deviceMemory == 4 || it.deviceMemory == 8 })
    }

    @Test
    fun zoneAndLocaleRejectValuesThatWouldBreakTheScript() {
        assertEquals("America/New_York", FingerprintProfile.normalizeTimeZone(" America/New_York "))
        assertEquals("UTC", FingerprintProfile.normalizeTimeZone("UTC"))
        assertNull(FingerprintProfile.normalizeTimeZone("America/New_York\";alert(1)"))
        assertNull(FingerprintProfile.normalizeTimeZone(""))
        assertNull(FingerprintProfile.normalizeTimeZone(null))
        assertEquals("en-US", FingerprintProfile.normalizeLocale("en_US"))
        assertNull(FingerprintProfile.normalizeLocale("not a locale"))
        assertNull(FingerprintProfile.normalizeLocale("en-US;"))
    }
}
