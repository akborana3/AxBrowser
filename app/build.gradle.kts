import java.net.URL

tasks.register("downloadYtDlpBinaries") {
    val arm64File = file("src/main/jniLibs/arm64-v8a/libytdlp.so")
    val x86File   = file("src/main/jniLibs/x86_64/libytdlp.so")
    outputs.files(arm64File, x86File)

    doLast {
        arm64File.parentFile.mkdirs()
        x86File.parentFile.mkdirs()
        if (!arm64File.exists() || arm64File.length() < 1_000_000L) {
            println("Downloading yt-dlp for arm64-v8a...")
            URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_android")
                .openStream().use { src -> arm64File.outputStream().use { src.copyTo(it) } }
            println("arm64 binary: ${arm64File.length()} bytes")
        }
        if (!x86File.exists() || x86File.length() < 1_000_000L) {
            println("Downloading yt-dlp for x86_64...")
            URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux")
                .openStream().use { src -> x86File.outputStream().use { src.copyTo(it) } }
            println("x86_64 binary: ${x86File.length()} bytes")
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
    coreLibraryDesugaring("com.android.tools.build:desugaring:2.0.4")

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
