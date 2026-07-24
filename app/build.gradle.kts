import java.net.HttpURLConnection
import java.net.URL

fun downloadWithRedirects(urlStr: String, destFile: File): Boolean {
    var currentUrl = urlStr
    var redirects = 0
    val maxRedirects = 10

    while (redirects <= maxRedirects) {
        val connection = URL(currentUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout    = 300_000
            connection.setRequestProperty("User-Agent", "AxBrowser-Build/1.0")
            connection.setRequestProperty("Accept", "*/*")
            connection.connect()

            val code = connection.responseCode
            when (code) {
                200 -> {
                    connection.inputStream.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val size = destFile.length()
                    if (size < 1_000_000L) {
                        println("  ✗ File too small after download: $size bytes (expected >1MB)")
                        destFile.delete()
                        return false
                    }
                    println("  ✓ Downloaded ${size / (1024 * 1024)}MB → ${destFile.name}")
                    return true
                }
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        println("  ✗ Redirect with no Location header from $currentUrl")
                        return false
                    }
                    currentUrl = if (location.startsWith("http")) location
                                 else "https://github.com$location"
                    redirects++
                    println("  → Redirect $redirects → $currentUrl")
                }
                else -> {
                    println("  ✗ HTTP $code from $currentUrl")
                    return false
                }
            }
        } finally {
            connection.disconnect()
        }
    }
    println("  ✗ Too many redirects for $urlStr")
    return false
}

tasks.register("downloadYtDlpBinaries") {
    val arm64File = file("src/main/jniLibs/arm64-v8a/libytdlp.so")
    val x86File   = file("src/main/jniLibs/x86_64/libytdlp.so")

    doLast {
        arm64File.parentFile.mkdirs()
        x86File.parentFile.mkdirs()

        if (!arm64File.exists() || arm64File.length() < 1_000_000L) {
            arm64File.delete()
            println("\n⬇  Downloading yt-dlp ARM64 (yt-dlp_android)...")
            val ok = downloadWithRedirects(
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android",
                arm64File
            )
            if (!ok) {
                error("FATAL: Failed to download yt-dlp ARM64 binary. " +
                      "Check internet connection and try again: ./gradlew downloadYtDlpBinaries")
            }
        } else {
            println("✓  arm64 yt-dlp already present (${arm64File.length() / (1024*1024)}MB)")
        }

        if (!x86File.exists() || x86File.length() < 1_000_000L) {
            x86File.delete()
            println("\n⬇  Downloading yt-dlp x86_64 (yt-dlp_linux)...")
            val ok = downloadWithRedirects(
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux",
                x86File
            )
            if (!ok) {
                println("⚠  Warning: x86_64 binary download failed. " +
                        "yt-dlp won't work on emulators but will work on real devices.")
            }
        } else {
            println("✓  x86_64 yt-dlp already present (${x86File.length() / (1024*1024)}MB)")
        }
    }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn("downloadYtDlpBinaries")
}

plugins {
    id("axbrowser.android.application")
    id("axbrowser.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.akay.axbrowser"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(project(":core:core-ui"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":feature:feature-browser"))
    implementation(project(":feature:feature-downloads"))
    implementation(project(":feature:feature-bookmarks"))
    implementation(project(":feature:feature-history"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":feature:feature-filemanager"))
    implementation(project(":feature:feature-videoplayer"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}
