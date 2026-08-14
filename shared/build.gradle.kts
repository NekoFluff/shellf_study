plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.crazyfluff.shellfstudy.shared.generated.resources"
}

kotlin {
    android {
        namespace = "com.crazyfluff.shellfstudy.shared"
        compileSdk = 37
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }

        withHostTestBuilder {}.configure {}

        // Works around https://youtrack.jetbrains.com/issue/CMP-9547: without this, Compose
        // Multiplatform's composeResources bundle (.cvr assets) silently never makes it into the
        // Android APK when using AGP9's com.android.kotlin.multiplatform.library plugin.
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.sqlite.bundled)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
            implementation(libs.ksoup)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
