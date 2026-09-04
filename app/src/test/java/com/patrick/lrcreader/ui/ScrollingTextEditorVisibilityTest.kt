package com.patrick.lrcreader.ui

import org.junit.Assert.*
import org.junit.Test

class ScrollingTextEditorVisibilityTest {
    @Test fun phoneBeforeFocusShowsHeaderAndChords() {
        assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(true, false, false))
    }
    @Test fun phoneDuringEditingHidesHeaderButKeepsChords() {
        assertEquals(ScrollingTextEditorVisibility(false, true), scrollingTextEditorVisibility(true, true, false))
    }
    @Test fun phoneOpensOnlyTextAndColorWhileEditing() {
        assertEquals(ScrollingTextEditorVisibility(false, true, true, false),
            scrollingTextEditorVisibility(true, true, markupPanelOpen = true))
    }
    @Test fun phoneOpensOnlyAlignmentWhileEditing() {
        assertEquals(ScrollingTextEditorVisibility(false, true, false, true),
            scrollingTextEditorVisibility(true, true, alignmentPanelOpen = true))
    }
    @Test fun returningToTextClosesPanelsButKeepsChordsVisible() {
        assertEquals(ScrollingTextEditorVisibility(false, true, false, false),
            scrollingTextEditorVisibility(true, true, markupPanelOpen = false, alignmentPanelOpen = false))
    }
    @Test fun leavingFocusRestoresPhoneTools() {
        assertEquals(ScrollingTextEditorVisibility(true, true), scrollingTextEditorVisibility(true, false, false))
    }
    @Test fun tabletKeepsItsOriginalFocusRuleAndVisibleChordButtons() {
        for ((markup, alignment) in listOf(false to false, true to false, false to true)) {
            assertEquals(ScrollingTextEditorVisibility(true, true, markup, alignment),
                scrollingTextEditorVisibility(false, false, markup, alignment))
            assertEquals(ScrollingTextEditorVisibility(false, true, markup, alignment),
                scrollingTextEditorVisibility(false, true, markup, alignment))
        }
    }
    @Test fun phoneRecognizesFocusInsideTextFieldWhileTabletRuleIsUnchanged() {
        assertTrue(scrollingTextEditorContentFocused(true, false, true))
        assertFalse(scrollingTextEditorContentFocused(false, false, true))
        assertTrue(scrollingTextEditorContentFocused(false, true, true))
        assertFalse(scrollingTextEditorContentFocused(true, false, false))
    }
}
