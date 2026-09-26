package com.patrick.lrcreader.core

import android.view.KeyEvent

enum class PlaylistPedalKeyAction {
    MOVE_PREVIOUS,
    MOVE_NEXT,
    OPEN_SELECTED,
    CONSUME,
    IGNORE
}

fun playlistMoveCommandForKeyCode(keyCode: Int): HardwareListCommand? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_PAGE_UP -> HardwareListCommand.MOVE_PREVIOUS

    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_PAGE_DOWN -> HardwareListCommand.MOVE_NEXT

    else -> null
}

fun resolvePlaylistPedalKeyAction(
    eventAction: Int,
    keyCode: Int,
    repeatCount: Int,
    isPhoneLayout: Boolean,
    pressedKeyCode: Int?,
    longPressAlreadyHandled: Boolean
): PlaylistPedalKeyAction {
    if (playlistMoveCommandForKeyCode(keyCode) == null) return PlaylistPedalKeyAction.IGNORE
    if (eventAction == KeyEvent.ACTION_UP) {
        return if (pressedKeyCode == keyCode) PlaylistPedalKeyAction.CONSUME
        else PlaylistPedalKeyAction.IGNORE
    }
    if (eventAction != KeyEvent.ACTION_DOWN) return PlaylistPedalKeyAction.IGNORE
    if (pressedKeyCode == keyCode && isPhoneLayout) {
        return if (!longPressAlreadyHandled) {
            PlaylistPedalKeyAction.OPEN_SELECTED
        } else {
            PlaylistPedalKeyAction.CONSUME
        }
    }
    if (repeatCount == 0) {
        return when (playlistMoveCommandForKeyCode(keyCode)) {
            HardwareListCommand.MOVE_PREVIOUS -> PlaylistPedalKeyAction.MOVE_PREVIOUS
            HardwareListCommand.MOVE_NEXT -> PlaylistPedalKeyAction.MOVE_NEXT
            else -> PlaylistPedalKeyAction.IGNORE
        }
    }
    return PlaylistPedalKeyAction.CONSUME
}

fun hardwareSelectionTargetIndex(
    anchorIndex: Int,
    itemCount: Int,
    command: HardwareListCommand
): Int? {
    if (itemCount <= 0) return null
    val safeAnchor = anchorIndex.coerceIn(0, itemCount - 1)
    return when (command) {
        HardwareListCommand.MOVE_PREVIOUS -> (safeAnchor - 1).coerceAtLeast(0)
        HardwareListCommand.MOVE_NEXT -> (safeAnchor + 1).coerceAtMost(itemCount - 1)
        HardwareListCommand.ACTIVATE -> safeAnchor
    }
}
