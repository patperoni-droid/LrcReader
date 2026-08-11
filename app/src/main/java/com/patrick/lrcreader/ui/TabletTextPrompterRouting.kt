package com.patrick.lrcreader.ui

internal fun shouldUseTabletSplitTextPrompter(
    tabletMode: Boolean,
    tabletExperimentalModeEnabled: Boolean,
    selectedTabSupportsSplit: Boolean,
    textPrompterId: String?
): Boolean =
    tabletMode &&
        tabletExperimentalModeEnabled &&
        selectedTabSupportsSplit &&
        !textPrompterId.isNullOrBlank()
