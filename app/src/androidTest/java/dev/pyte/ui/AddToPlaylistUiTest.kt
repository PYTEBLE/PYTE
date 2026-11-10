package dev.pyte.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track
import dev.pyte.ui.components.AddToPlaylistSheet
import dev.pyte.ui.components.TrackRow
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddToPlaylistUiTest {
    @get:Rule val compose = createComposeRule()
    private val song = Track("YGr2G2fc48U", "schönhauser", "Zartmann", 155000L, null)

    @Test fun trackMenuOffersLocalPlaylistWithoutStartingPlayback() {
        var selected = false
        var played = false
        compose.setContent {
            PyteTheme {
                TrackRow(song, false, false, onClick = { played = true }, onFavoriteClick = {},
                    onAddToPlaylist = { selected = true })
            }
        }
        compose.onNodeWithContentDescription("Aktionen für „schönhauser“").performClick()
        compose.onNodeWithText("Zur Playlist hinzufügen").performClick()
        compose.runOnIdle { assertTrue(selected); assertFalse(played) }
    }

    @Test fun sheetSelectsExistingLocalCopy() {
        var chosen: String? = null
        compose.setContent {
            PyteTheme {
                AddToPlaylistSheet(song, listOf(PlaylistSummary("PLtest", "My mix", null, null, 1, "source", 1)),
                    false, null, {}, { chosen = it }, {})
            }
        }
        compose.onNodeWithText("My mix").performClick()
        compose.runOnIdle { assertEquals("PLtest", chosen) }
    }

    @Test fun createFlowOpensFromTheChooserAndCreatesPlaylist() {
        var title: String? = null
        compose.setContent {
            PyteTheme { AddToPlaylistSheet(song, emptyList(), false, null, {}, {}, { title = it }) }
        }
        compose.onNodeWithText("Neue Playlist erstellen").performClick()
        compose.onNodeWithText("Name der neuen Playlist").performTextInput("My new playlist")
        compose.onNodeWithText("Erstellen und hinzufügen").performClick()
        compose.runOnIdle { assertEquals("My new playlist", title) }
    }

    @Test fun searchAppearsOnlyForLongListsAndFiltersChoices() {
        var chosen: String? = null
        val playlists = listOf(
            PlaylistSummary("local:a", "Abend", null, null, 1, "", 0),
            PlaylistSummary("local:b", "Morgen", null, null, 2, "", 0),
            PlaylistSummary("local:c", "Nacht", null, null, 3, "", 0),
            PlaylistSummary("local:d", "Regen", null, null, 4, "", 0),
            PlaylistSummary("local:e", "Sommer", null, null, 5, "", 0),
            PlaylistSummary("local:f", "Winter", null, null, 6, "", 0))
        compose.setContent { PyteTheme {
            AddToPlaylistSheet(song, playlists, false, null, {}, { chosen = it }, {}, recentId = "local:b")
        } }
        compose.onNodeWithText("Zuletzt verwendet").assertExists()
        compose.onNodeWithText("Playlists durchsuchen").performTextInput("abend")
        compose.onNodeWithText("Morgen").assertDoesNotExist()
        compose.onNodeWithText("Abend").performClick()
        compose.runOnIdle { assertEquals("local:a", chosen) }
    }

    @Test fun busySaveDisablesCreationAndCancel() {
        compose.setContent { PyteTheme { AddToPlaylistSheet(song, emptyList(), true, null, {}, {}, {}) } }
        compose.onNodeWithText("Neue Playlist erstellen").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").assertIsNotEnabled()
    }
}
