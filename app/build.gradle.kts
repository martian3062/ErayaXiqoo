plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.evolet.tachyon"
    // Current AndroidX/Compose/OkHttp need compileSdk ≥ 37. targetSdk stays 35 (CLAUDE.md §4).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.evolet.tachyon"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.7.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            // R8 shrink: ~32 MB debug APK → small shareable APK. Debug-signed so it sideloads like the debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log in agents is a no-op in JVM tests
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Only used to reach the Tier 2 servers on 127.0.0.1 (see net/LocalHttp.kt).
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // App-like navigation with saveable, independent back stacks for each bottom tab.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // F19 reminders: survives reboot/Doze without the exact-alarm permission.
    implementation(libs.androidx.work)

    testImplementation(libs.junit)
}
