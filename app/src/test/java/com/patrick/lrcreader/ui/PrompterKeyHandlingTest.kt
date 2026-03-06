package com.patrick.lrcreader.ui

import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterKeyHandlingTest {

    @Test
    fun prompterKeySequence_keyDownKeyUp_dispatchesActionOnlyOnKeyDown() {
        val sequence = listOf(
            resolvePrompterKeyHandling(KeyEventType.KeyDown, PrompterAction.NEXT),
            resolvePrompterKeyHandling(KeyEventType.KeyUp, PrompterAction.NEXT),
            resolvePrompterKeyHandling(KeyEventType.KeyDown, PrompterAction.TOGGLE),
            resolvePrompterKeyHandling(KeyEventType.KeyUp, PrompterAction.TOGGLE),
            resolvePrompterKeyHandling(KeyEventType.KeyDown, null)
        )

        assertEquals(listOf(true, true, true, true, false), sequence.map { it.consumed })
        assertEquals(
            listOf(PrompterAction.NEXT, null, PrompterAction.TOGGLE, null, null),
            sequence.map { it.actionToDispatch }
        )
        assertEquals(2, sequence.count { it.actionToDispatch != null })
    }

    @Test
    fun resolvePrompterKeyHandling_unsupportedKey_isNotConsumed() {
        val decision = resolvePrompterKeyHandling(
            eventType = KeyEventType.KeyUp,
            action = null
        )

        assertFalse(decision.consumed)
        assertNull(decision.actionToDispatch)
    }

    @Test
    fun resolvePrompterKeyHandling_keyUpSupportedKey_isConsumedWithoutDispatch() {
        val decision = resolvePrompterKeyHandling(
            eventType = KeyEventType.KeyUp,
            action = PrompterAction.END
        )

        assertTrue(decision.consumed)
        assertNull(decision.actionToDispatch)
    }
}
