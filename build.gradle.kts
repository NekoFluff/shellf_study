// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.detekt) apply false
}

// Static analysis for both modules: detekt's default rules, over every Kotlin source set. Findings the
// code already had when this was added are recorded in each module's detekt-baseline.xml, so the
// build fails only on new ones — regenerate a baseline with `./gradlew detektBaseline` after
// deliberately fixing some of them.
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        source.setFrom(fileTree("src") { include("**/*.kt") })
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(file("detekt-baseline.xml"))
    }
}