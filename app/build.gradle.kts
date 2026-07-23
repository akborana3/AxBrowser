import java.net.URL

tasks.register("downloadYtDlpBinaries") {
    val arm64File = file("src/main/jniLibs/arm64-v8a/libytdlp.so")
    val x86File   = file("src/main/jniLibs/x86_64/libytdlp.so")
    outputs.files(arm64File, x86File)

    doLast {
        arm64File.parentFile.mkdirs()
        x86File.parentFile.mkdirs()

        // Try multiple URLs for ARM64
        val arm64Urls = listOf(
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
        )
        if (!arm64File.exists() || arm64File.length() < 1_000_000L) {
            for (url in arm64Urls) {
                try {
                    println("Trying $url for arm64-v8a...")
                    URL(url).openStream().use { src -> arm64File.outputStream().use { src.copyTo(it) } }
                    if (arm64File.length() > 1_000_000L) {
                        println("arm64 binary: ${arm64File.length()} bytes")
                        break
                    }
                } catch (e: Exception) {
                    println("Failed: ${e.message}")
                    arm64File.delete()
                }
            }
        }

        // Try multiple URLs for x86_64
        val x86Urls = listOf(
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
        )
        if (!x86File.exists() || x86File.length() < 1_000_000L) {
            for (url in x86Urls) {
                try {
                    println("Trying $url for x86_64...")
                    URL(url).openStream().use { src -> x86File.outputStream().use { src.copyTo(it) } }
                    if (x86File.length() > 1_000_000L) {
                        println("x86_64 binary: ${x86File.length()} bytes")
                        break
                    }
                } catch (e: Exception) {
                    println("Failed: ${e.message}")
                    x86File.delete()
                }
            }
        }

        if (!arm64File.exists() && !x86File.exists()) {
            println("WARNING: Could not download yt-dlp binaries. yt-dlp feature will be disabled.")
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
