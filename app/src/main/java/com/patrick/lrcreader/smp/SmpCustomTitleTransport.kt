package com.patrick.lrcreader.smp

import android.content.Context
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.config.TitleAliasesStore

internal fun captureCustomTitleContract(
    context: Context,
    songId: String
): SmpConfig.CustomTitleContract {
    val cleanSongId = songId.trim().takeIf(String::isNotEmpty)
        ?: return SmpConfig.CustomTitleContract(value = null)
    return SmpConfig.CustomTitleContract(
        value = TitleAliasesStore.getTitleForTrack(context, buildSmpItem(cleanSongId))
    )
}

internal fun applyCustomTitleContract(
    contract: SmpConfig.CustomTitleContract?,
    replace: (String) -> Boolean,
    clear: () -> Boolean
): Boolean {
    return when {
        contract == null -> true
        contract.value == null -> clear()
        else -> replace(contract.value)
    }
}

internal fun applyCustomTitleContract(
    context: Context,
    songId: String,
    contract: SmpConfig.CustomTitleContract?
): Boolean {
    val cleanSongId = songId.trim().takeIf(String::isNotEmpty) ?: return false
    val trackIdentity = buildSmpItem(cleanSongId)
    return applyCustomTitleContract(
        contract = contract,
        replace = { title ->
            TitleAliasesStore.setTitleForTrack(context, trackIdentity, title)
        },
        clear = {
            TitleAliasesStore.clearTitleForTrack(context, trackIdentity)
        }
    )
}
