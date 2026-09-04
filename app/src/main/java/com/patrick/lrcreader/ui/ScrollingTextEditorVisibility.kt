package com.patrick.lrcreader.ui

internal data class ScrollingTextEditorVisibility(val showHeader: Boolean, val showChords: Boolean)

internal fun scrollingTextEditorContentFocused(isPhone: Boolean, isFocused: Boolean, hasFocus: Boolean): Boolean =
    if (isPhone) hasFocus else isFocused

internal fun scrollingTextEditorVisibility(
    isPhone: Boolean,
    contentFocused: Boolean,
    toolsRequested: Boolean
): ScrollingTextEditorVisibility {
    val phoneEditing = isPhone && contentFocused && !toolsRequested
    return ScrollingTextEditorVisibility(
        showHeader = if (isPhone) !phoneEditing else !contentFocused,
        showChords = !phoneEditing
    )
}
