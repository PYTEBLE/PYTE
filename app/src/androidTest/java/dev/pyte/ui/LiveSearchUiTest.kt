package dev.pyte.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pyte.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveSearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun onlineSongCanBeAddedToANewLocalPlaylistAndOpenedFromLibrary() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSearch") == "true")
        val title = "Search test ${System.currentTimeMillis()}"
        compose.onNodeWithText("Search", useUnmergedTree = false).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("schönhauser Gustav")
        compose.onNodeWithContentDescription("Search YouTube Music").performClick()
        val options = hasContentDescription("Options for schönhauser", substring = true)
        compose.waitUntil(120_000) { compose.onAllNodes(options).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(options)[0].performClick()
        compose.onNodeWithText("Add to local playlist").performClick()
        compose.onNodeWithText("New local playlist name").performTextInput(title)
        compose.onNodeWithText("Create & add").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Create & add").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Library").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("On this device • 1 tracks").assertExists()
        compose.onNodeWithContentDescription("Playlist options").performClick()
        compose.onNodeWithText("Refresh from YouTube").assertDoesNotExist()
        compose.onNodeWithText("Delete playlist").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }
}
