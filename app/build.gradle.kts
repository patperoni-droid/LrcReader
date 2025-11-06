plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.patrick.lrcreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patrick.lrcreader"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // --- version de développement ---
        getByName("debug") {
            applicationIdSuffix = ".dev"      // → "com.patrick.lrcreader.dev"
            versionNameSuffix = "-dev"        // → "1.0-dev"
            resValue("string", "app_name", "LRC (dev)")  // 👈 nom affiché sur le téléphone
        }

        // --- version stable (pour la scène) ---
        getByName("release") {
            isMinifyEnabled = false
            // garde le nom normal défini dans res/values/strings.xml
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Media3 (lecture audio)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}