plugins {
    alias(libs.plugins.android.application)
}

// The TV shell: a TV home screen that only draws a saved A2UI design (a JSON list of A2UI v0.9 messages).
// Plain Android views, not Compose: Compose is too slow on weak TV boxes (Mi Box). The phone app stays Compose.
android {
    namespace = "io.github.raghavsatyadev.tvfreedom.shell"
    compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "io.github.raghavsatyadev.tvfreedom.shell"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 3
        versionName = "0.1"
    }
    // Google's material-color-utilities, shared with the launcher (vendored there, Apache-2.0).
    sourceSets["main"].java.directories.add("../../launcher/app/src/thirdParty/java")
    // The colour library uses Java 17 switch expressions.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // Shrunk release build for TV boxes. Debug key until it ships in the phone app.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.a2ui.engine)
    implementation(libs.a2ui.model)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    // Its ready-made designs are checked against this shell.
    testImplementation(projects.composeApp)
}

// ./gradlew :tvShell:testDebugUnitTest -Pdesigns.apps=a,b,c writes the ready-made designs for those apps to build/designs.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("designs.apps").orNull?.let { systemProperty("designs.apps", it) }
    outputs.upToDateWhen { false }
}
