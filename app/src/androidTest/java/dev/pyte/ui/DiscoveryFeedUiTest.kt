package dev.pyte.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.pyte.data.remote.CatalogPlaylist
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.components.PlaylistCatalogSection
import dev.pyte.ui.components.PlaylistImportDialog
import dev.pyte.ui.components.SearchSuggestionsContent
import dev.pyte.ui.theme.PyteTheme
import dev.pyte.ui.viewmodels.CatalogImportState
import dev.pyte.ui.viewmodels.CatalogState
import dev.pyte.ui.viewmodels.SearchSuggestionsState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DiscoveryFeedUiTest {
    @get:Rule val compose = createComposeRule()
    private val playlist = CatalogPlaylist("PLfixture", "Charts zum Testen", "YouTube Music", null, charts = true)
    private val song = Track("YGr2G2fc48U", "Neuer Song", "Künstler", 1000, null)

    @Test fun selectingCatalogCardDoesNotImportOrPlayAnything() {
        var selected: CatalogPlaylist? = null
        compose.setContent { PyteTheme { PlaylistCatalogSection(CatalogState(playlists = listOf(playlist)), emptySet(), {}, { selected = it }) } }
        compose.onNodeWithContentDescription("„Charts zum Testen“ entdecken").performClick()
        compose.runOnIdle { assertEquals(playlist, selected) }
    }

    @Test fun importRequiresExplicitConfirmationAndSupportsCancel() {
        var confirmations = 0
        var dismissals = 0
        compose.setContent { PyteTheme { PlaylistImportDialog(CatalogImportState(selected = playlist), { confirmations++ }, { dismissals++ }) } }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(0, confirmations); assertEquals(1, dismissals) }
        compose.onNodeWithText("In Bibliothek übernehmen").performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test fun pendingImportDisablesDuplicateConfirmationAndCancel() {
        compose.setContent { PyteTheme { PlaylistImportDialog(CatalogImportState(selected = playlist, importing = true), {}, {}) } }
        compose.onNodeWithText("In Bibliothek übernehmen").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").assertIsNotEnabled()
        compose.onNodeWithText("Playlist wird übernommen …").assertExists()
    }

    @Test fun recommendationsShowTheirReasonAndPlayTheMixedQueueOnlyOnTap() {
        var played: List<Track>? = null
        var playedIndex = -1
        val state = SearchSuggestionsState(songs = listOf(SongRecommendation(song, "Letzter Song")))
        compose.setContent { PyteTheme { SearchSuggestionsContent(state, PlayerUiState(), emptySet(), {},
            { tracks, index -> played = tracks; playedIndex = index }, {}, {}, {}) } }
        compose.onNodeWithText("Passt zu „Letzter Song“").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(played) }
        compose.onNodeWithText(song.title).performClick()
        compose.runOnIdle { assertEquals(listOf(song), played); assertEquals(0, playedIndex) }
    }

    @Test fun disabledHistoryShowsAnHonestEmptyState() {
        compose.setContent { PyteTheme { SearchSuggestionsContent(SearchSuggestionsState(historyEnabled = false), PlayerUiState(), emptySet(), {}, { _, _ -> }, {}, {}, {}) } }
        compose.onNodeWithText("Der Hörverlauf ist ausgeschaltet. Es werden keine persönlichen Vorschläge geladen.")
            .performScrollTo().assertIsDisplayed()
    }
}
