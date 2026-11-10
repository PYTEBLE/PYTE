package dev.pyte.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.Track
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.screens.RecommendationsSheet
import dev.pyte.ui.theme.PyteTheme
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.LivingUiPolicy
import dev.pyte.ui.viewmodels.DiscoveryContext
import dev.pyte.ui.viewmodels.DiscoveryState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecommendationsUiTest {
    @get:Rule val compose = createComposeRule()
    private val song = Track("YGr2G2fc48U", "New discovery", "Artist", 120000, null)
    private val state = DiscoveryState(DiscoveryContext("My mix", "local:test"),
        songs = listOf(SongRecommendation(song, "A favorite")))

    @Test fun recommendationsCanBeListenedToAndAddedWithVisibleConfirmation() {
        var listened: Track? = null
        var adds = 0
        compose.setContent {
            var current by remember { mutableStateOf(state) }
            PyteTheme {
                CompositionLocalProvider(LocalLivingUi provides LivingUiPolicy(systemAnimations = false)) {
                    RecommendationsSheet(current, PlayerUiState(), {}, {}, { listened = it }, {}, {
                        adds++
                        current = current.copy(added = setOf(it.videoId), status = "Song gespeichert")
                    })
                }
            }
        }
        compose.onNodeWithText("Weil du A favorite hörst").assertExists()
        compose.onNodeWithContentDescription("Probehören: New discovery").performClick()
        compose.runOnIdle { assertEquals(song, listened) }
        compose.onNodeWithContentDescription("„New discovery“ zur Playlist hinzufügen").performClick()
        compose.onNodeWithText("Song gespeichert").assertExists()
        compose.runOnIdle { assertEquals(1, adds) }
    }

    @Test fun previewHasAnExplicitReturnActionAndSheetCanBeDismissed() {
        var returned = false
        var closed = false
        compose.setContent {
            PyteTheme {
                RecommendationsSheet(state, PlayerUiState(current = song, isPreview = true),
                    { closed = true }, {}, {}, { returned = true }, {})
            }
        }
        compose.onNodeWithText("Zur bisherigen Wiedergabe").performClick()
        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.runOnIdle { assertTrue(returned); assertTrue(closed) }
    }

    @Test fun offlineErrorsOfferRetryAndEmptyResultsAreExplained() {
        var retried = false
        compose.setContent {
            var current by remember { mutableStateOf(state.copy(songs = emptyList(), error = "No connection")) }
            PyteTheme {
                RecommendationsSheet(current, PlayerUiState(), {}, {
                    retried = true
                    current = current.copy(error = null)
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText("No connection").assertExists()
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.onNodeWithText("Gerade keine neuen Treffer").assertExists()
        compose.runOnIdle { assertTrue(retried) }
    }
}
