import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val repoRoot = rootProject.projectDir.parentFile

kotlin {
    android {
        namespace = "io.github.raghavsatyadev.tvfreedom.shared"
        compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = 1 } }
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime.mp)
            implementation(libs.compose.ui.mp)
            implementation(libs.compose.foundation.mp)
            implementation(libs.compose.material3.mp)
            implementation(libs.compose.components.resources.mp)
            implementation(libs.lifecycle.viewmodel.compose.mp)
            implementation(libs.lifecycle.runtime.compose.mp)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coroutines.android)
            implementation(libs.libadb)
            implementation(libs.conscrypt)
            implementation(libs.bcpkix)
            implementation(libs.mlkit.genai.prompt)
            implementation(libs.anthropic)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}

// The tests read data/, lists/ and the goldens from the repo, the same inputs tools/make_golden.py uses.
tasks.withType<Test>().configureEach { systemProperty("tvfreedom.repo", repoRoot.path) }
