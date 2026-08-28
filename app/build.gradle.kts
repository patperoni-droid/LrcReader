// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val releaseKeystoreFile = file("/Users/patrickperoni/Keystore/stage_music_player.jks")
val releaseKeyAlias = "stageplayer"
val releaseStorePassword = providers.gradleProperty("stageReleaseStorePassword")
    .orElse(providers.environmentVariable("STAGE_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("stageReleaseKeyPassword")
    .orElse(providers.environmentVariable("STAGE_RELEASE_KEY_PASSWORD"))
    .orNull
val hasReleaseSigningSecrets = !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

val enableSoundTouchNative =
    providers.gradleProperty("enableSoundTouchNative")
        .orNull
        ?.toBooleanStrictOrNull()
        ?: true

android {
    namespace = "com.patrick.lrcreader.exo"
    compileSdk = 36

    flavorDimensions += "mode"
    productFlavors {
        create("concert") {
            dimension = "mode"
            applicationIdSuffix = ".concert"
            versionNameSuffix = "-concert"
            externalNativeBuild {
                cmake {
                    arguments += "-DLRC_USE_SOUNDTOUCH=0"
                }
            }
        }
        create("labo") {
            dimension = "mode"
            applicationIdSuffix = ".labo"
            versionNameSuffix = "-labo"
            externalNativeBuild {
                cmake {
                    arguments += "-DLRC_USE_SOUNDTOUCH=${if (enableSoundTouchNative) 1 else 0}"
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.patrick.lrcreader.exo"
        minSdk = 23
        targetSdk = 36
        versionCode = 10
        versionName = "0.4.6-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ on ship la lib sur arm64 + armeabi-v7a (comme ton tel)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    // ✅ déclaration CMake au bon niveau (pas dans un if)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystoreFile.isFile && hasReleaseSigningSecrets) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") { /* rien */ }
        getByName("release") {
            if (releaseKeystoreFile.isFile && hasReleaseSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-extractor:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    kapt("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("ci") {
    group = "verification"
    description = "Runs SPL CI checks: unit tests for labo+concert and assembleDebug."
    dependsOn(
        "testLaboDebugUnitTest",
        "testConcertDebugUnitTest",
        "assembleDebug"
    )
}
