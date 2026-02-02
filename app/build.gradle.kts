// build.gradle.kts du module LrcReader_EXO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {

    flavorDimensions += "mode"

    productFlavors {
        create("concert") {
            dimension = "mode"
            applicationIdSuffix = ".concert"
            versionNameSuffix = "-concert"
        }

        create("labo") {
            dimension = "mode"
            applicationIdSuffix = ".labo"
            versionNameSuffix = "-labo"
        }
    }
    namespace = "com.patrick.lrcreader.exo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patrick.lrcreader.exo"
        minSdk = 23
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            // Ne rien mettre ici : on gère l'identité via les flavors (concert / labo)
        }

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
        buildConfig = true   // ✅ OBLIGATOIRE pour BuildConfig.FLAVOR
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
    implementation("androidx.compose.animation:animation")

    // Icônes Material étendu
    implementation("androidx.compose.material:material-icons-extended")

    // Activité Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Pour la gestion de dossiers / fichiers
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Media3 – ExoPlayer et modules associés
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-extractor:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-common:$media3")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ─────────────────────────────
    // ✅ TESTS
    // ─────────────────────────────

    // Unit tests (src/test)
    testImplementation("junit:junit:4.13.2")

    // Instrumented tests (src/androidTest)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Compose UI tests (androidTest)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}



