package com.patrick.lrcreader.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrompterKeyMappingTest {

    @Test
    fun mapPrompterKey_mapsNextKeys() {
        val keys = listOf(
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT
        )

        keys.forEach { keyCode ->
            assertEquals(PrompterAction.NEXT, mapPrompterKey(keyCode))
        }
    }

    @Test
    fun mapPrompterKey_mapsPrevKeys() {
        val keys = listOf(
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT
        )

        keys.forEach { keyCode ->
            assertEquals(PrompterAction.PREV, mapPrompterKey(keyCode))
        }
    }

    @Test
    fun mapPrompterKey_mapsToggleKeys() {
        val keys = listOf(
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        )

        keys.forEach { keyCode ->
            assertEquals(PrompterAction.TOGGLE, mapPrompterKey(keyCode))
        }
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
