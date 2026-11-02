package dev.pyte.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track
import dev.pyte.ui.components.AddToPlaylistDialog
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
        compose.onNodeWithContentDescription("Options for schönhauser").performClick()
        compose.onNodeWithText("Add to local playlist").performClick()
        compose.runOnIdle { assertTrue(selected); assertFalse(played) }
    }

    @Test fun dialogSelectsExistingLocalCopy() {
        var chosen: String? = null
        compose.setContent {
            PyteTheme {
                AddToPlaylistDialog(song, listOf(PlaylistSummary("PLtest", "My mix", null, null, 1, "source", 1)),
                    false, null, {}, { chosen = it }, {})
            }
        }
        compose.onNodeWithText("My mix").performClick()
        compose.runOnIdle { assertEquals("PLtest", chosen) }
    }

    @Test fun dialogCanCreatePlaylistWithoutAnyImports() {
        var title: String? = null
        compose.setContent {
            PyteTheme { AddToPlaylistDialog(song, emptyList(), false, null, {}, {}, { title = it }) }
        }
        compose.onNodeWithText("New local playlist name").performTextInput("My new playlist")
        compose.onNodeWithText("Create & add").performClick()
        compose.runOnIdle { assertEquals("My new playlist", title) }
    }
}
