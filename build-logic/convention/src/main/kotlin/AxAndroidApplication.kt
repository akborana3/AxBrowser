import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the

class AxAndroidApplication : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            the<BaseAppModuleExtension>().apply {
                compileSdk = 34

                defaultConfig {
                    applicationId = "com.akay.axbrowser"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0.0"

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    debug {
                        isMinifyEnabled = false
                        applicationIdSuffix = ".debug"
                    }
                }

                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions> {
                jvmTarget = "17"
            }
        }
    }
}
