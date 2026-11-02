package dev.pyte.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerUiState
import dev.pyte.ui.components.TrackRow
import dev.pyte.ui.screens.QueueSheet
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueUiTest {
    @get:Rule val compose = createComposeRule()
    private val first = Track("YGr2G2fc48U", "First song", "Artist", 155000L, null)
    private val second = first.copy(videoId = "dQw4w9WgXcQ", title = "Second song")

    @Test
    fun queueButtonsMoveAndRemoveAnOccurrence() {
        var moved: Pair<Int, Int>? = null
        var removed: Int? = null
        compose.setContent {
            var tracks by remember { mutableStateOf(listOf(first, second)) }
            PyteTheme {
                QueueSheet(PlayerUiState(queue = tracks), onDismiss = {}, onPlay = {}, onMove = { from, to ->
                    moved = from to to
                    tracks = tracks.toMutableList().apply { add(to, removeAt(from)) }
                }, onRemove = { index ->
                    removed = index
                    tracks = tracks.filterIndexed { i, _ -> i != index }
                })
            }
        }
        compose.onAllNodesWithText("Move down")[0].performClick()
        compose.runOnIdle { assertEquals(0 to 1, moved) }
        compose.onAllNodesWithText("Remove")[0].performClick()
        compose.onNodeWithText("Queue · 1 tracks").assertExists()
        compose.runOnIdle { assertEquals(0, removed) }
    }

    @Test
    fun trackOptionsDispatchPlayNextWithoutStartingRowPlayback() {
        var next = false
        var played = false
        compose.setContent {
            PyteTheme {
                TrackRow(first, false, false, onClick = { played = true }, onFavoriteClick = {},
                    onPlayNext = { next = true }, onAddToQueue = {})
            }
        }
        compose.onNodeWithContentDescription("Options for First song").performClick()
        compose.onNodeWithText("Play next").performClick()
        compose.runOnIdle { assertTrue(next); assertFalse(played) }
    }
}
