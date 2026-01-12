// build.gradle.kts du module LrcReader_EXO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.patrick.lrcreader.exo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patrick.lrcreader.exo"
        minSdk = 23
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        // --- version de développement ---
        getByName("debug") {
            applicationIdSuffix = ".dev"      // → com.patrick.lrcreader.exo.dev
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "LRC Reader EXO (dev)")
        }

        // --- version "prod" ---
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // BOM Compose pour gérer les versions cohérentes
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI de base
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ✅ Paquet d’icônes Material étendu :
    //    contient beaucoup d’icônes supplémentaires (par exemple Headset, Search, etc.)
    //    Utile si tu veux changer l’icône DJ par un casque audio.
    implementation("androidx.compose.material:material-icons-extended")

    // Activité Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Pour la gestion de dossiers / fichiers
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Media3 – ExoPlayer et modules associés
    //  une seule version pour éviter les conflits
    val media3 = "1.6.1"
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-extractor:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.compose.animation:animation")
    // ViewModel pour Jetpack Compose (nécessaire pour viewModel())
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}



