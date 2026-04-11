package com.patrick.lrcreader.core

enum class AppEdition {
    LITE,
    PRO
}

object EditionConfig {
    val current: AppEdition = AppEdition.LITE

    val isLite: Boolean
        get() = current == AppEdition.LITE

    val isPro: Boolean
        get() = current == AppEdition.PRO
}
