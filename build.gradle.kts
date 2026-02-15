plugins {
    // Plugin Android + Kotlin pour tout le projet
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.0" apply false

    // ✅ Nouveau plugin obligatoire depuis Kotlin 2.0
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
