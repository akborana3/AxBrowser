package com.akay.core.domain.model

/**
 * One coherent device identity derived from a seed.
 *
 * The user agent, platform, client hints, screen, CPU/memory and GPU strings are
 * chosen together. Mixing a Pixel user agent with the real phone's GPU, or a
 * desktop user agent with a touch-screen `maxTouchPoints`, is itself a fingerprint.
 */
data class FingerprintProfile(
    val desktop: Boolean,
    val userAgent: String,
    val navigatorPlatform: String,
    val clientPlatform: String,
    val clientPlatformVersion: String,
    val clientArchitecture: String,
    val clientBitness: String,
    val clientModel: String,
    val chromeMajor: String,
    val chromeFull: String,
    val mobile: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
    val availHeight: Int,
    val colorDepth: Int,
    val hardwareConcurrency: Int,
    val deviceMemory: Int,
    val gpuVendor: String,
    val gpuRenderer: String,
    val maxTouchPoints: Int,
    val androidVersion: String,
    val model: String,
    /** Stable 0..99999 value mixed into canvas/audio noise. Same seed, same noise. */
    val noiseSeed: Int
) {
    val summary: String
        get() = if (desktop) {
            "Linux desktop · Chrome $chromeMajor"
        } else {
            "$model · Android $androidVersion · $gpuRenderer"
        }

    companion object {
        const val CHROME_MAJOR = "124"
        const val CHROME_FULL = "124.0.0.0"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL Safari/537.36"

        private val timeZonePattern = Regex("""[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*""")
        private val localePattern = Regex("""[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*""")

        fun fromSeed(seed: String, desktop: Boolean): FingerprintProfile {
            val hash = fnv1a(seed)
            val noiseSeed = mod(hash, 100_000)
            if (desktop) return desktop(noiseSeed)
            val spec = MOBILE[mod(hash xor (hash ushr 33), MOBILE.size)]
            return FingerprintProfile(
                desktop = false,
                userAgent = "Mozilla/5.0 (Linux; Android ${spec.androidVersion}; ${spec.model}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_FULL Mobile Safari/537.36",
                navigatorPlatform = "Linux armv8l",
                clientPlatform = "Android",
                clientPlatformVersion = spec.platformVersion,
                clientArchitecture = "arm",
                clientBitness = "64",
                clientModel = spec.model,
                chromeMajor = CHROME_MAJOR,
                chromeFull = CHROME_FULL,
                mobile = true,
                screenWidth = spec.screenWidth,
                screenHeight = spec.screenHeight,
                availHeight = spec.availHeight,
                colorDepth = 24,
                hardwareConcurrency = spec.hardwareConcurrency,
                deviceMemory = spec.deviceMemory,
                gpuVendor = spec.gpuVendor,
                gpuRenderer = spec.gpuRenderer,
                maxTouchPoints = 5,
                androidVersion = spec.androidVersion,
                model = spec.model,
                noiseSeed = noiseSeed
            )
        }

        /** Returns a zone id that is safe to embed, or null when the value should be ignored. */
        fun normalizeTimeZone(value: String?): String? {
            val zone = value?.trim().orEmpty()
            if (zone.length !in 1..64 || !timeZonePattern.matches(zone)) return null
            return zone
        }

        /** Returns a BCP 47 tag with `_` normalized to `-`, or null when it isn't one. */
        fun normalizeLocale(value: String?): String? {
            val locale = value?.trim().orEmpty()
            if (locale.length !in 2..32 || !localePattern.matches(locale)) return null
            return locale.replace('_', '-')
        }

        private fun desktop(noiseSeed: Int) = FingerprintProfile(
            desktop = true,
            userAgent = DESKTOP_USER_AGENT,
            navigatorPlatform = "Linux x86_64",
            clientPlatform = "Linux",
            clientPlatformVersion = "6.5.0",
            clientArchitecture = "x86",
            clientBitness = "64",
            clientModel = "",
            chromeMajor = CHROME_MAJOR,
            chromeFull = CHROME_FULL,
            mobile = false,
            screenWidth = 1920,
            screenHeight = 1080,
            availHeight = 1040,
            colorDepth = 24,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            gpuVendor = "Google Inc. (Intel)",
            gpuRenderer = "ANGLE (Intel, Mesa Intel(R) UHD Graphics 630 (CFL GT2), OpenGL 4.6)",
            maxTouchPoints = 0,
            androidVersion = "",
            model = "",
            noiseSeed = noiseSeed
        )

        private fun fnv1a(seed: String): Long {
            var hash = FNV_OFFSET
            seed.toByteArray(Charsets.UTF_8).forEach { byte ->
                hash = hash xor (byte.toLong() and 0xff)
                hash *= FNV_PRIME
            }
            return hash
        }

        private fun mod(value: Long, size: Int): Int {
            val remainder = value % size
            val positive = if (remainder < 0) remainder + size else remainder
            return positive.toInt()
        }

        private const val FNV_OFFSET = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
    }
}

private data class MobileSpec(
    val model: String,
    val androidVersion: String,
    val platformVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val availHeight: Int,
    val hardwareConcurrency: Int,
    val deviceMemory: Int,
    val gpuVendor: String,
    val gpuRenderer: String
)

private val MOBILE = listOf(
    MobileSpec("Pixel 7", "14", "14.0.0", 412, 915, 839, 8, 8, "Qualcomm", "Adreno (TM) 730"),
    MobileSpec("Pixel 8", "14", "14.0.0", 412, 915, 839, 8, 8, "Qualcomm", "Adreno (TM) 740"),
    MobileSpec("Pixel 6", "13", "13.0.0", 412, 915, 839, 8, 8, "ARM", "Mali-G78"),
    MobileSpec("SM-S911B", "14", "14.0.0", 360, 780, 720, 8, 8, "Qualcomm", "Adreno (TM) 740"),
    MobileSpec("SM-A546B", "14", "14.0.0", 384, 854, 800, 8, 4, "ARM", "Mali-G68"),
    MobileSpec("23049RAD8C", "13", "13.0.0", 393, 873, 825, 8, 8, "Qualcomm", "Adreno (TM) 619")
)
