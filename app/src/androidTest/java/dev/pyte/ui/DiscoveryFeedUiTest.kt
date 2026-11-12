package dev.pyte.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pyte.data.remote.CatalogPlaylist
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.domain.Track
import dev.pyte.domain.PlaylistSummary
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.components.PlaylistCatalogSection
import dev.pyte.ui.components.PlaylistImportDialog
import dev.pyte.ui.components.LibraryCollectionCard
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

    @Test fun featuredPickAndCompactRowsPlayTheirOwnPositionInTheFullList() {
        var playedIndex = -1
        val second = Track("second12345A", "Zweiter Song", "Anderer Künstler", 1000, null)
        val state = SearchSuggestionsState(songs = listOf(SongRecommendation(song, "Letzter Song"),
            SongRecommendation(second, "Anderer Song")))
        compose.setContent { PyteTheme { SearchSuggestionsContent(state, PlayerUiState(), emptySet(), {},
            { _, index -> playedIndex = index }, {}, {}, {}) } }
        compose.onNodeWithText("TOP-VORSCHLAG FÜR DICH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zweiter Song").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, playedIndex) }
        compose.onNodeWithText(song.title).performClick()
        compose.runOnIdle { assertEquals(0, playedIndex) }
    }

    @Test fun disabledHistoryShowsAnHonestEmptyState() {
        compose.setContent { PyteTheme { SearchSuggestionsContent(SearchSuggestionsState(historyEnabled = false), PlayerUiState(), emptySet(), {}, { _, _ -> }, {}, {}, {}) } }
        compose.onNodeWithText("Der Hörverlauf ist ausgeschaltet. Es werden keine persönlichen Vorschläge geladen.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun magazineLayoutHasFullWidthChartThenPairedChartsAndVerticalPlaylistTips() {
        val second = playlist.copy(id = "PLsecond", title = "Weitere Charts")
        val third = playlist.copy(id = "PLthird", title = "Video Charts")
        val firstTip = playlist.copy(id = "PLtip1", title = "Neue Sounds", charts = false)
        val lastTip = playlist.copy(id = "PLtip2", title = "Abendmusik", charts = false)
        compose.setContent { PyteTheme {
            Box(Modifier.width(360.dp)) {
                PlaylistCatalogSection(CatalogState(playlists = listOf(playlist, firstTip, second, lastTip, third)), emptySet(), {}, {})
            }
        } }
        val hero = compose.onNodeWithContentDescription("„Charts zum Testen“ entdecken").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("„Weitere Charts“ entdecken"))
        val left = compose.onNodeWithContentDescription("„Weitere Charts“ entdecken").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription("„Video Charts“ entdecken").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.width > left.width * 1.8f)
        assertEquals(left.top, right.top, 2f)
        assertTrue(left.right < right.left)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("„Abendmusik“ entdecken"))
        compose.onNodeWithContentDescription("„Abendmusik“ entdecken").assertIsDisplayed()
        val firstBounds = compose.onNodeWithContentDescription("„Neue Sounds“ entdecken").fetchSemanticsNode().boundsInRoot
        val lastBounds = compose.onNodeWithContentDescription("„Abendmusik“ entdecken").fetchSemanticsNode().boundsInRoot
        assertEquals(firstBounds.top, lastBounds.top, 2f)
    }

    @Test fun largeTextUsesSingleColumnAndKeepsLastPlaylistReachable() {
        val tip = playlist.copy(id = "PLlarge", title = "Entspannte Musik für einen langen Abend", charts = false)
        compose.setContent { PyteTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                Box(Modifier.width(360.dp)) { PlaylistCatalogSection(CatalogState(playlists = listOf(playlist, tip)), emptySet(), {}, {}) }
            }
        } }
        val heroWidth = compose.onNodeWithContentDescription("„Charts zum Testen“ entdecken").fetchSemanticsNode().boundsInRoot.width
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("„Entspannte Musik für einen langen Abend“ entdecken"))
        val tipBounds = compose.onNodeWithContentDescription("„Entspannte Musik für einen langen Abend“ entdecken")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(heroWidth, tipBounds.width, 2f)
    }

    @Test fun singleCollectionUsesTheFullWidthAndOpensOnlyOnTap() {
        var opened = 0
        var expectedWidth = 0f
        val collection = PlaylistSummary("local:test", "Meine Sammlung", null, null, 225, "", 0L)
        compose.setContent { PyteTheme {
            expectedWidth = with(LocalDensity.current) { 360.dp.toPx() }
            Box(Modifier.width(360.dp)) { LibraryCollectionCard(collection, { opened++ }, {}) }
        } }
        assertEquals(expectedWidth, compose.onNode(hasClickAction() and hasText("Meine Sammlung")).fetchSemanticsNode().boundsInRoot.width, 2f)
        compose.onNodeWithText("225 Songs").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithText("Meine Sammlung").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun unavailableCatalogKeepsRetryAvailableWithoutInventingCards() {
        var retries = 0
        compose.setContent { PyteTheme { PlaylistCatalogSection(CatalogState(error = true), emptySet(), { retries++ }, {}) } }
        compose.onNodeWithText("YouTube-Music-Playlists sind gerade nicht erreichbar.").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onAllNodesWithContentDescription("„Charts zum Testen“ entdecken").assertCountEquals(0)
    }
}
