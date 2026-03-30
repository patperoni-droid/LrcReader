package com.patrick.lrcreader.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupInstallScreenTest {

    @Test
    fun shouldUsePickedFolderAsSplRoot_returnsTrueForSplMusicFolderItself() {
        assertTrue(
            shouldUsePickedFolderAsSplRoot(
                folderName = "SPL_Music",
                childNames = listOf("BackingTracks", "DJ")
            )
        )
    }

    @Test
    fun shouldUsePickedFolderAsSplRoot_returnsTrueWhenPickedFolderAlreadyContainsBackingTracks() {
        assertTrue(
            shouldUsePickedFolderAsSplRoot(
                folderName = "StageMusicPlayer",
                childNames = listOf("BackingTracks", "Backups")
            )
        )
    }

    @Test
    fun shouldUsePickedFolderAsSplRoot_returnsFalseForParentFolderContainingOnlySplMusicChild() {
        assertFalse(
            shouldUsePickedFolderAsSplRoot(
                folderName = "Documents",
                childNames = listOf("SPL_Music", "Pictures")
            )
        )
    }
}
