package dev.pyte.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.Track
import dev.pyte.ui.components.FavoriteButton
import dev.pyte.ui.components.LivingPlayButton
import dev.pyte.ui.components.SongMetadata
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.LivingUiPolicy
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LivingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reducedMotionControlsStillDispatchExactlyOnce() {
        var plays = 0
        var favorites = 0
        compose.setContent {
            var playing by remember { mutableStateOf(false) }
            var favorite by remember { mutableStateOf(false) }
            PyteTheme {
                CompositionLocalProvider(LocalLivingUi provides LivingUiPolicy(systemAnimations = false, haptics = false)) {
                    Column {
                        LivingPlayButton(playing, { plays++; playing = !playing })
                        FavoriteButton(favorite, { favorites++; favorite = !favorite })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Abspielen").performClick()
        compose.onNodeWithContentDescription("Pausieren").assertExists()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").assertExists()
        compose.runOnIdle { assertEquals(1, plays); assertEquals(1, favorites) }
    }

    @Test fun rapidSongUpdatesDoNotQueueOldMetadataInReducedMotion() {
        val song = mutableStateOf(Track("first", "First song", "Artist", 1000, null))
        compose.setContent {
            PyteTheme {
                CompositionLocalProvider(LocalLivingUi provides LivingUiPolicy(systemAnimations = false)) {
                    SongMetadata(song.value, 1)
                }
            }
        }
        compose.runOnIdle {
            song.value = song.value.copy(videoId = "second", title = "Second song")
            song.value = song.value.copy(videoId = "third", title = "Third song")
        }
        compose.onNodeWithText("Third song").assertExists()
        compose.onNodeWithText("First song").assertDoesNotExist()
        compose.onNodeWithText("Second song").assertDoesNotExist()
    }
}
