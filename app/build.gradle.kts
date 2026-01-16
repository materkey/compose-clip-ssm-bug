import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Determine SSM status based on build variant or Gradle property
// Usage: ./gradlew :app:assembleRelease -PdisableStrongSkipping=true
val disableSsm = gradle.startParameter.taskNames.any {
    it.contains("SsmOff", ignoreCase = true)
} || project.hasProperty("disableStrongSkipping")

android {
    namespace = "work.vkkovalev.samplecomposebug"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "work.vkkovalev.samplecomposebug"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Use debug keystore for release builds (sample app only!)
    // This allows installing release APKs without a real signing key.
    signingConfigs {
        getByName("debug") {
            // Debug keystore is already configured by default
        }
    }

    // Use release build for tests via Gradle property: -PtestOnRelease=true
    if (project.hasProperty("testOnRelease")) {
        testBuildType = "release"
    }

    buildTypes {
        debug {
            // SSM ON in debug too (default Compose behavior)
            buildConfigField("boolean", "SSM_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Test APK proguard rules (comprehensive keep rules)
            testProguardFiles("proguard-rules-test.pro")
            // SSM status based on gradle property
            buildConfigField("boolean", "SSM_ENABLED", if (disableSsm) "false" else "true")
            // Use debug signing for sample app (allows easy installation)
            signingConfig = signingConfigs.getByName("debug")
        }
        create("ssmOff") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            // SSM OFF variant
            buildConfigField("boolean", "SSM_ENABLED", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    if (disableSsm) {
        featureFlags.add(ComposeFeatureFlag.StrongSkipping.disabled())
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // SSM OFF variant test dependencies
    "ssmOffImplementation"(libs.androidx.compose.ui.tooling)
    "ssmOffImplementation"(libs.androidx.compose.ui.test.manifest)

    // Release variant test dependencies (for R8 testing)
    releaseImplementation(libs.androidx.compose.ui.tooling)
    releaseImplementation(libs.androidx.compose.ui.test.manifest)

    // Tracing library needed by AndroidJUnitRunner (must be explicit for release tests)
    androidTestImplementation("androidx.tracing:tracing:1.2.0")
}
