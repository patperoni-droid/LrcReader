package com.patrick.lrcreader.core

enum class AppEdition {
    LITE,
    PRO
}

object EditionConfig {
    val current: AppEdition = AppEdition.PRO
    const val isDmxUiEnabled: Boolean = false

    val isLite: Boolean
        get() = current == AppEdition.LITE

    val isPro: Boolean
        get() = current == AppEdition.PRO
}
