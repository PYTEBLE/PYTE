package dev.pyte.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.Track
import dev.pyte.ui.components.*
import dev.pyte.ui.theme.PyteTheme
import dev.pyte.ui.theme.LivingUiPolicy
import dev.pyte.ui.theme.LocalLivingUi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val song = Track("YGr2G2fc48U", "Mein Song", "Künstler", 100000, null)

    @Test fun directSaveAndLongPressWorkEvenWithoutScreenSpecificPlaylistCallbacks() {
        var saved: Track? = null
        var opened: Track? = null
        var plays = 0
        compose.setContent { PyteTheme {
            CompositionLocalProvider(LocalSongActions provides SongActions({ opened = it }, { saved = it }),
                LocalLivingUi provides LivingUiPolicy(systemAnimations = false)) {
                TrackRow(song, false, false, { plays++ }, {})
            }
        } }
        compose.onNodeWithContentDescription("„Mein Song“ zur Playlist hinzufügen").performClick()
        compose.runOnIdle { assertEquals(song, saved); assertEquals(0, plays) }
        compose.onNodeWithText(song.title).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(song, opened); assertEquals(0, plays) }
    }

    @Test fun sharedSheetHasVisiblePlaylistActionAndDoesNotPlayOnOpen() {
        var saves = 0
        compose.setContent { PyteTheme {
            SongActionsSheet(song, false, {}, { saves++ }, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Zur Playlist hinzufügen").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithText("Als Nächstes abspielen").assertExists()
        compose.onNodeWithText("Zur Warteschlange").assertExists()
    }

    @Test fun saveFeedbackAndUndoRemainReachableInsideASheet() {
        var undo = false
        compose.setContent { PyteTheme {
            CompositionLocalProvider(LocalSongFeedback provides SongFeedback("In „Abend“ gespeichert", { undo = true })) {
                dev.pyte.ui.screens.QueueSheet(dev.pyte.playback.PlayerUiState(current = song, queue = listOf(song), currentIndex = 0),
                    {}, {}, { _, _ -> }, {})
            }
        } }
        compose.onNodeWithText("In „Abend“ gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Rückgängig").performClick()
        compose.runOnIdle { assertTrue(undo) }
    }

    @Test fun playlistRowKeepsItsSourceWhenAnotherPlaylistIsPlaying() {
        var selected: Pair<Track, String>? = null
        compose.setContent { PyteTheme {
            CompositionLocalProvider(LocalSongActions provides SongActions({}, {}, { track, id -> selected = track to id })) {
                TrackRow(song, false, false, {}, {}, sourcePlaylistId = "local:selected")
            }
        } }
        compose.onNodeWithContentDescription("Aktionen für „Mein Song“").performClick()
        compose.runOnIdle { assertEquals(song to "local:selected", selected) }
    }

    @Test fun playlistRemovalActionDispatchesOnceWithoutStartingPlayback() {
        var removals = 0
        var plays = 0
        compose.setContent { PyteTheme {
            SongActionsSheet(song, false, {}, {}, { plays++ }, { plays++ }, {}, {},
                onRemoveFromPlaylist = { removals++ })
        } }
        compose.onNodeWithText("Aus dieser Playlist entfernen").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, removals); assertEquals(0, plays) }
    }
}
