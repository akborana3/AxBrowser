plugins {
    id("axbrowser.android.library")
}

android {
    namespace = "com.akay.core.domain"
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}
