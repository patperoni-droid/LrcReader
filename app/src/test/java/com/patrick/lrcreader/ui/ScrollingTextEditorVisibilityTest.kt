package com.patrick.lrcreader.ui

import org.junit.Assert.*
import org.junit.Test

class ScrollingTextEditorVisibilityTest {
    @Test fun phoneBeforeFocusShowsHeaderAndChords() {
        assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(true, false, false))
    }
    @Test fun phoneDuringEditingReleasesHeaderAndChordSpace() {
        assertEquals(ScrollingTextEditorVisibility(false, false), scrollingTextEditorVisibility(true, true, false))
    }
    @Test fun phoneCanRecallToolsWithoutLosingTextFocus() {
        assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(true, true, true))
    }
    @Test fun leavingFocusRestoresPhoneTools() {
        assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(true, false, false))
    }
    @Test fun tabletKeepsItsOriginalFocusRuleAndVisibleChordButtons() {
        for (tools in listOf(false, true)) {
            assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(false, false, tools))
            assertEquals(ScrollingTextEditorVisibility(false, true), scrollingTextEditorVisibility(false, true, tools))
        }
    }
    @Test fun phoneRecognizesFocusInsideTextFieldWhileTabletRuleIsUnchanged() {
        assertTrue(scrollingTextEditorContentFocused(true, false, true))
        assertFalse(scrollingTextEditorContentFocused(false, false, true))
        assertTrue(scrollingTextEditorContentFocused(false, true, true))
        assertFalse(scrollingTextEditorContentFocused(true, false, false))
    }
}
