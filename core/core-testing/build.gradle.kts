plugins {
    id("axbrowser.android.library")
}

android {
    namespace = "com.akay.core.testing"
}

dependencies {
    implementation(project(":core:core-domain"))

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.test)
    implementation(libs.junit5.api)
    implementation(libs.mockk)
    implementation(libs.turbine)
}
