package com.akay.feature.browser.fingerprint

import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.akay.core.domain.model.FingerprintProfile

/**
 * Applies the same identity the document-start script reports to the network
 * stack (User-Agent and, when WebView supports it, User-Agent Client Hints).
 * JS overrides alone leave `Sec-CH-UA-Model` advertising the real phone.
 */
object FingerprintWebSettings {

    private val androidDevice = Regex("""Android ([0-9.]+);\s*([^;)]+)""")
    private val chromeFull = Regex("""Chrome/(\d+\.\d+\.\d+\.\d+)""")
    private val chromeMajor = Regex("""Chrome/(\d+)""")

    /**
     * @return true when client-hint metadata was set from [profile]. The caller
     *   passes that back as [metadataOverridden] so turning protection off can
     *   put the original device back, without touching hints the user never spoofed.
     */
    fun apply(
        settings: WebSettings,
        profile: FingerprintProfile?,
        desktopMode: Boolean,
        originalUserAgent: String,
        metadataOverridden: Boolean
    ): Boolean {
        if (profile != null) {
            settings.userAgentString = profile.userAgent
            writeMetadata(settings, metadataFor(profile))
            return true
        }
        val restored = if (desktopMode) {
            FingerprintProfile.DESKTOP_USER_AGENT
        } else {
            originalUserAgent
                .replace(" wv)", ")")
                .replace("; wv", "")
                .ifBlank { originalUserAgent }
        }
        if (restored.isNotBlank()) settings.userAgentString = restored
        if (metadataOverridden) {
            metadataFromUserAgent(restored)?.let { writeMetadata(settings, it) }
        }
        return false
    }

    private fun writeMetadata(settings: WebSettings, metadata: UserAgentMetadata) {
        val supported = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
        }.getOrDefault(false)
        if (!supported) return
        runCatching { WebSettingsCompat.setUserAgentMetadata(settings, metadata) }
    }

    private fun brand(name: String, major: String, full: String): UserAgentMetadata.BrandVersion =
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand(name)
            .setMajorVersion(major)
            .setFullVersion(full)
            .build()

    private fun metadataFor(profile: FingerprintProfile): UserAgentMetadata {
        val full = profile.chromeFull
        val major = profile.chromeMajor
        return UserAgentMetadata.Builder()
            .setBrandVersionList(
                listOf(
                    brand("Chromium", major, full),
                    brand("Google Chrome", major, full),
                    brand("Not-A.Brand", "99", "99.0.0.0")
                )
            )
            .setFullVersion(full)
            .setPlatform(profile.clientPlatform)
            .setPlatformVersion(profile.clientPlatformVersion)
            .setArchitecture(profile.clientArchitecture)
            .setModel(profile.clientModel)
            .setMobile(profile.mobile)
            .setBitness(profile.clientBitness.toIntOrNull() ?: 64)
            .setWow64(false)
            .build()
    }

    private fun metadataFromUserAgent(userAgent: String): UserAgentMetadata? {
        val full = chromeFull.find(userAgent)?.groupValues?.getOrNull(1) ?: return null
        val major = chromeMajor.find(userAgent)?.groupValues?.getOrNull(1) ?: return null
        val desktop = !userAgent.contains("Mobile") && userAgent.contains("X11")
        val android = androidDevice.find(userAgent)
        val platform = if (desktop) "Linux" else "Android"
        val platformVersion = when {
            desktop -> "6.5.0"
            android != null -> {
                val version = android.groupValues[1]
                if (version.count { it == '.' } >= 2) version else "$version.0.0"
            }
            else -> "14.0.0"
        }
        val model = if (desktop || android == null) {
            ""
        } else {
            android.groupValues[2].substringBefore(" Build").trim().let {
                if (it.equals("Mobile", ignoreCase = true)) "" else it
            }
        }
        return UserAgentMetadata.Builder()
            .setBrandVersionList(
                listOf(
                    brand("Chromium", major, full),
                    brand("Google Chrome", major, full),
                    brand("Not-A.Brand", "99", "99.0.0.0")
                )
            )
            .setFullVersion(full)
            .setPlatform(platform)
            .setPlatformVersion(platformVersion)
            .setArchitecture(if (desktop) "x86" else "arm")
            .setModel(model)
            .setMobile(!desktop)
            .setBitness(64)
            .setWow64(false)
            .build()
    }
}
