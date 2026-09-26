package com.patrick.lrcreader.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistPedalNavigationTest {
    @Test
    fun initialPressMovesImmediately() {
        assertEquals(
            PlaylistPedalKeyAction.MOVE_PREVIOUS,
            action(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        )
        assertEquals(
            PlaylistPedalKeyAction.MOVE_NEXT,
            action(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)
        )
    }

    @Test
    fun firstAndroidRepeatOpensSelectionOnPhoneOnly() {
        assertEquals(
            PlaylistPedalKeyAction.OPEN_SELECTED,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                repeatCount = 1,
                isPhoneLayout = true,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
            )
        )
        assertEquals(
            PlaylistPedalKeyAction.CONSUME,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                repeatCount = 1,
                isPhoneLayout = false,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
            )
        )
    }

    @Test
    fun repeatedDownWithZeroRepeatCountStillTriggersLongPressOnPhone() {
        assertEquals(
            PlaylistPedalKeyAction.OPEN_SELECTED,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeatCount = 0,
                isPhoneLayout = true,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_UP
            )
        )
        assertEquals(
            PlaylistPedalKeyAction.CONSUME,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeatCount = 0,
                isPhoneLayout = true,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_UP,
                longPressAlreadyHandled = true
            )
        )
    }

    @Test
    fun repeatsCannotOpenTwiceOrForAnotherKey() {
        assertEquals(
            PlaylistPedalKeyAction.CONSUME,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                repeatCount = 2,
                isPhoneLayout = true,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                longPressAlreadyHandled = true
            )
        )
        assertEquals(
            PlaylistPedalKeyAction.CONSUME,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeatCount = 1,
                isPhoneLayout = true,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
            )
        )
    }

    @Test
    fun selectionMovementStopsAtBothEnds() {
        assertEquals(0, hardwareSelectionTargetIndex(0, 3, HardwareListCommand.MOVE_PREVIOUS))
        assertEquals(2, hardwareSelectionTargetIndex(2, 3, HardwareListCommand.MOVE_NEXT))
        assertEquals(1, hardwareSelectionTargetIndex(1, 3, HardwareListCommand.ACTIVATE))
        assertNull(hardwareSelectionTargetIndex(0, 0, HardwareListCommand.MOVE_NEXT))
    }

    @Test
    fun keyUpEndsHoldAndNextPressMovesNormally() {
        assertEquals(
            PlaylistPedalKeyAction.CONSUME,
            action(
                eventAction = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                pressedKeyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                longPressAlreadyHandled = true
            )
        )
        assertEquals(
            PlaylistPedalKeyAction.MOVE_NEXT,
            action(
                eventAction = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                pressedKeyCode = null
            )
        )
    }

    private fun action(
        eventAction: Int,
        keyCode: Int,
        repeatCount: Int = 0,
        isPhoneLayout: Boolean = true,
        pressedKeyCode: Int? = null,
        longPressAlreadyHandled: Boolean = false
    ): PlaylistPedalKeyAction = resolvePlaylistPedalKeyAction(
        eventAction = eventAction,
        keyCode = keyCode,
        repeatCount = repeatCount,
        isPhoneLayout = isPhoneLayout,
        pressedKeyCode = pressedKeyCode,
        longPressAlreadyHandled = longPressAlreadyHandled
    )
}
