package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletArrangementPlaylistLayoutTest {

    @Test
    fun normalLayout_keepsPlaylistVisible() {
        assertTrue(
            shouldShowTabletPlaylistPane(
                focusMode = TabletPlayerFocusMode.NONE,
                arrangementPlaylistVisible = false
            )
        )
        assertFalse(
            shouldExpandTabletArrangementPane(
                focusMode = TabletPlayerFocusMode.NONE,
                arrangementPlaylistVisible = false
            )
        )
    }

    @Test
    fun arrangementWithPlaylistClosed_usesFullEditorWidth() {
        assertFalse(
            shouldShowTabletPlaylistPane(
                focusMode = TabletPlayerFocusMode.ARRANGEMENT,
                arrangementPlaylistVisible = false
            )
        )
        assertTrue(
            shouldExpandTabletArrangementPane(
                focusMode = TabletPlayerFocusMode.ARRANGEMENT,
                arrangementPlaylistVisible = false
            )
        )
    }

    @Test
    fun arrangementWithPlaylistOpen_restoresSplitLayout() {
        assertTrue(
            shouldShowTabletPlaylistPane(
                focusMode = TabletPlayerFocusMode.ARRANGEMENT,
                arrangementPlaylistVisible = true
            )
        )
        assertFalse(
            shouldExpandTabletArrangementPane(
                focusMode = TabletPlayerFocusMode.ARRANGEMENT,
                arrangementPlaylistVisible = true
            )
        )
    }
}
