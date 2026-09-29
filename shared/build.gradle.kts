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
    // Room's KSP-generated `actual object *DatabaseConstructor` declarations are expect/actual
    // classes, which Kotlin still reports as Beta (KT-61573). The generated code is not ours to
    // annotate, so opt in build-wide rather than leaving 12 unactionable warnings in every build.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            api(libs.androidx.lifecycle.viewmodel)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose.multiplatform)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            // The Android-only jank harness — see JankStatsTracker. Declared here rather than in :app
            // because the tracker is shared/androidMain code: :app cannot be referenced from :shared,
            // and the reporter seam has to reach commonMain so shared screens can report state.
            implementation(libs.androidx.metrics.performance)
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
    add("androidMainImplementation", platform(libs.androidx.compose.bom))
    add("androidMainImplementation", "androidx.compose.foundation:foundation")
}

// AGP's lint reads the KSP-generated sources but does not declare them as inputs, so Gradle fails the
// whole invocation with an implicit-dependency validation error the moment KSP and lint run together —
// which is every `./gradlew build` after a change to commonTest. Both sides are affected: the model
// generation (`generateAndroidHostTestLintModel`) and the analysis (`lintAnalyzeAndroidHostTest`). CI
// never saw it because it runs the two test tasks and never `build`, and the compile break that
// preceded this fix masked it locally.
//
// Only the Android KSP tasks are named: they are the ones whose output lint reads, and depending on
// all of them would drag the iOS targets' KSP into an Android lint run.
val androidKspTasks = tasks.matching { it.name == "kspAndroidMain" || it.name == "kspAndroidHostTest" }
tasks.matching { it.name.startsWith("lint") || it.name.endsWith("LintModel") }.configureEach {
    dependsOn(androidKspTasks)
}
