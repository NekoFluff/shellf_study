import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.crazyfluff.shellfstudy"
    compileSdk {
        version = release(libs.versions.androidCompileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.crazyfluff.shellfstudy"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 12
        versionName = "1.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            firebaseAppDistribution {
                groups = "friends"
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates the jank harness — see JankStatsTracker. AGP 9 does not generate
        // BuildConfig unless asked.
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The bytecode target, declared for Kotlin as well as Java so it is a setting rather than a
// side effect of compileOptions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.common)

    // Unit tests
    testImplementation(libs.junit)
    // These three are used by the test sources only — nothing in :app's main sources references
    // material3, ui-graphics or kotlinx.serialization — so they stay off the main compile classpath
    // rather than widening it for every build.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.compose.ui.graphics)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    // Stateless Compose screen tests run under Robolectric here instead of on a device/emulator —
    // see DashboardScreenTest/LessonScreenTest. Tests needing real device behavior (Keystore,
    // Espresso system back) stay in androidTest.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.media3.test.utils)
    // Only :shared depends on this (as `implementation`, not `api`) — pulled in directly here for
    // PopBackStackSafelyTest, which drives a real NavHostController.
    testImplementation(libs.navigation.compose.multiplatform)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.koin.bom))
    androidTestImplementation(libs.koin.android)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // No Firebase code exists anywhere in the sources by design: Crashlytics and Analytics both
    // self-initialize via ContentProviders, so crash reports and baseline session/first_open
    // events are collected without any explicit call. See README "Crash reporting & analytics".
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

}