package com.patrick.lrcreader.ui

internal data class ScrollingTextEditorVisibility(
    val showHeader: Boolean,
    val showChords: Boolean,
    val showMarkupPanel: Boolean = false,
    val showAlignmentPanel: Boolean = false
)

internal fun scrollingTextEditorContentFocused(isPhone: Boolean, isFocused: Boolean, hasFocus: Boolean): Boolean =
    if (isPhone) hasFocus else isFocused

internal fun scrollingTextEditorVisibility(
    isPhone: Boolean,
    contentFocused: Boolean,
    markupPanelOpen: Boolean = false,
    alignmentPanelOpen: Boolean = false
): ScrollingTextEditorVisibility {
    val phoneEditing = isPhone && contentFocused
    return ScrollingTextEditorVisibility(
        showHeader = if (isPhone) !phoneEditing else !contentFocused,
        showChords = !phoneEditing,
        showMarkupPanel = markupPanelOpen,
        showAlignmentPanel = alignmentPanelOpen
    )
}
