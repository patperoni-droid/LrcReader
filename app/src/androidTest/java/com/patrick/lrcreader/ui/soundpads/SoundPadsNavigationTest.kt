package com.patrick.lrcreader.ui.soundpads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.BottomTab
import com.patrick.lrcreader.ui.BottomTabsBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** All phone destinations remain visible, clickable and selected correctly at narrow widths. */
class SoundPadsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun padsRemainVisibleAndNavigationLeavesPadsOnSmallPhone() {
        var destination: BottomTab = BottomTab.Player
        compose.setContent {
            var selected by remember { mutableStateOf<BottomTab>(BottomTab.Player) }
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredWidth(360.dp)) {
                    BottomTabsBar(selected, true, true, PlaybackCoordinator.Source.Player,
                        onSelected = { selected = it; destination = it },
                        onSearchClick = {}, onMoreClick = {}, onPlayerReselect = {})
                }
            }
        }
        val pads = compose.onNodeWithContentDescription(context.getString(R.string.soundpads_title))
        pads.assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(BottomTab.SoundPads, destination)
        listOf(BottomTab.Player, BottomTab.Filler, BottomTab.Dj, BottomTab.Library,
            BottomTab.Search, BottomTab.More).forEach {
            compose.onNodeWithContentDescription(context.getString(it.labelRes)).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.tab_filler))
            .performClick().assertIsSelected()
        assertEquals(BottomTab.Filler, destination)
        pads.assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithContentDescription(context.getString(R.string.tab_player))
            .performClick().assertIsSelected()
        assertEquals(BottomTab.Player, destination)
    }
}
