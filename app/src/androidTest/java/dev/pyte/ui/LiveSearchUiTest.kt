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
        compose.onNodeWithText("Suchen", useUnmergedTree = false).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("schönhauser Gustav")
        compose.onNodeWithContentDescription("Auf YouTube Music suchen").performClick()
        val options = hasContentDescription("Aktionen für „schönhauser", substring = true)
        compose.waitUntil(120_000) { compose.onAllNodes(options).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(options)[0].performClick()
        compose.onNodeWithText("Zur Playlist").performClick()
        compose.onNodeWithText("Name der neuen Playlist").performTextInput(title)
        compose.onNodeWithText("Erstellen und hinzufügen").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Erstellen und hinzufügen").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Bibliothek").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("LOKAL • 1 Song").assertExists()
        compose.onNodeWithContentDescription("Playlist-Aktionen").performClick()
        compose.onNodeWithText("Von YouTube aktualisieren").assertDoesNotExist()
        compose.onNodeWithText("Playlist löschen").performClick()
        compose.onNodeWithText("Löschen", substring = false).performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
    }
}
