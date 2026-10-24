import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Release signing comes from tvfreedom-android/secret.properties (gitignored): storeFile, storePassword, keyAlias, keyPassword.
val secretProps = Properties().apply {
    rootProject.file("secret.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val repoRoot = rootProject.projectDir.parentFile

// lists/ in the repo is the single source of truth: copied into the APK's assets at build time.
val bundleLists by tasks.registering(Sync::class) {
    from(File(repoRoot, "lists")) { include("**/*.txt", "community.lst") }
    into(layout.buildDirectory.dir("generated/tvfreedomAssets/lists"))
}

android {
    namespace = "io.github.raghavsatyadev.tvfreedom"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "io.github.raghavsatyadev.tvfreedom"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }

    signingConfigs {
        create("release") {
            secretProps.getProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = secretProps.getProperty("storePassword")
            keyAlias = secretProps.getProperty("keyAlias")
            keyPassword = secretProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (secretProps.getProperty("storeFile") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false // libadb loads Conscrypt by reflection; keep v1 simple
        }
    }
    buildFeatures { compose = true }
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("generated/tvfreedomAssets").get().asFile.path)
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
    }
}

// The TVFreedom app runs scripts/termux-setup.sh in Termux to install Claude Code for the user.
val bundleTermuxSetup by tasks.registering(Sync::class) {
    from(File(repoRoot, "scripts")) { include("termux-setup.sh") }
    into(layout.buildDirectory.dir("generated/tvfreedomAssets/termux"))
}

tasks.named("preBuild") { dependsOn(bundleLists, bundleTermuxSetup) }

dependencies {
    implementation(projects.composeApp)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
}
