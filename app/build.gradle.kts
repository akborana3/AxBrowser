import java.net.HttpURLConnection
import java.net.URL

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

tasks.register("downloadYtDlpBinaries") {
    val arm64File = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libytdlp.so").asFile
    val x86File   = layout.projectDirectory.file("src/main/jniLibs/x86_64/libytdlp.so").asFile

    doLast {
        fun downloadWithRedirects(urlStr: String, destFile: File): Boolean {
            var currentUrl = urlStr
            var redirects = 0
            val maxRedirects = 10
            while (redirects <= maxRedirects) {
                val connection = URL(currentUrl).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 300_000
                    connection.setRequestProperty("User-Agent", "AxBrowser-Build/1.0")
                    connection.connect()
                    val code = connection.responseCode
                    when (code) {
                        200 -> {
                            connection.inputStream.use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            val size = destFile.length()
                            if (size < 1_000_000L) {
                                println("  File too small: $size bytes")
                                destFile.delete()
                                return false
                            }
                            println("  Downloaded ${size / (1024 * 1024)}MB")
                            return true
                        }
                        301, 302, 303, 307, 308 -> {
                            val location = connection.getHeaderField("Location")
                            if (location.isNullOrBlank()) return false
                            currentUrl = if (location.startsWith("http")) location else "https://github.com$location"
                            redirects++
                            println("  Redirect $redirects -> $currentUrl")
                        }
                        else -> {
                            println("  HTTP $code from $currentUrl")
                            return false
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            return false
        }

        arm64File.parentFile.mkdirs()
        x86File.parentFile.mkdirs()

        if (!arm64File.exists() || arm64File.length() < 1_000_000L) {
            arm64File.delete()
            println("\nDownloading yt-dlp ARM64...")
            val arm64Urls = listOf(
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64",
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
            )
            var arm64Ok = false
            for (url in arm64Urls) {
                println("  Trying $url ...")
                if (downloadWithRedirects(url, arm64File)) { arm64Ok = true; break }
                arm64File.delete()
            }
            if (!arm64Ok) {
                error("FATAL: Failed to download yt-dlp ARM64 binary.")
            }
        } else {
            println("arm64 yt-dlp already present (${arm64File.length() / (1024*1024)}MB)")
        }

        if (!x86File.exists() || x86File.length() < 1_000_000L) {
            x86File.delete()
            println("\nDownloading yt-dlp x86_64...")
            val ok = downloadWithRedirects(
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux",
                x86File
            )
            if (!ok) println("Warning: x86_64 binary download failed (optional)")
        } else {
            println("x86_64 yt-dlp already present (${x86File.length() / (1024*1024)}MB)")
        }
    }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn("downloadYtDlpBinaries")
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
