package com.patrick.lrcreader.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrompterKeyMappingTest {

    @Test
    fun mapPrompterKey_mapsRightToNext() {
        assertEquals(PrompterAction.NEXT, mapPrompterKey(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun mapPrompterKey_mapsLeftToPrev() {
        assertEquals(PrompterAction.PREV, mapPrompterKey(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun mapPrompterKey_mapsPageAndVerticalNavigationKeys() {
        assertEquals(PrompterAction.NEXT, mapPrompterKey(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(PrompterAction.PREV, mapPrompterKey(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(PrompterAction.NEXT, mapPrompterKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(PrompterAction.PREV, mapPrompterKey(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun mapPrompterKey_returnsNullForUnsupportedToggleKeys() {
        val keys = listOf(
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        )

        keys.forEach { keyCode ->
            assertNull(mapPrompterKey(keyCode))
        }
    }

    @Test
    fun viewportTarget_usesVisibleHeightWithFifteenPercentOverlap() {
        assertEquals(1_050, prompterViewportTarget(200, 2_000, 1_000, direction = 1))
        assertEquals(0, prompterViewportTarget(200, 2_000, 1_000, direction = -1))
    }

    @Test
    fun viewportTarget_clampsAtTextBounds() {
        assertEquals(2_000, prompterViewportTarget(1_800, 2_000, 1_000, direction = 1))
        assertEquals(0, prompterViewportTarget(50, 2_000, 1_000, direction = -1))
    }

    @Test
    fun mapPrompterKey_mapsHomeAndEnd() {
        assertEquals(PrompterAction.HOME, mapPrompterKey(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(PrompterAction.END, mapPrompterKey(KeyEvent.KEYCODE_MOVE_END))
    }

    @Test
    fun mapPrompterKey_returnsNullForUnsupportedKey() {
        assertNull(mapPrompterKey(KeyEvent.KEYCODE_A))
    }
}
