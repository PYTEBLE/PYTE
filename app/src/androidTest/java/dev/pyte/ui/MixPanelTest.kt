package dev.pyte.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.playback.MixUiState
import dev.pyte.ui.components.MixPanel
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mixActionsRemainReachableAndLoadingCannotDispatchTwice() {
        val state = mutableStateOf(MixUiState())
        var starts = 0
        var cancels = 0
        var restores = 0
        compose.setContent {
            PyteTheme {
                MixPanel(state.value, false,
                    onStart = { starts++; state.value = MixUiState(loading = true) },
                    onCancel = { cancels++; state.value = MixUiState() },
                    onRestore = { restores++ }, modifier = Modifier.width(300.dp))
            }
        }
        compose.onNodeWithText("Mix starten", substring = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Lädt…", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { state.value = MixUiState(title = "Seed song") }
        compose.onNodeWithContentDescription("Mix-Optionen").performClick()
        compose.onNodeWithText("Vorherige Warteschlange wiederherstellen").performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, cancels)
            assertEquals(1, restores)
        }
    }

    @Test fun activeMixOffersDirectSaveWithoutReplacingPlayback() {
        var saves = 0
        var starts = 0
        compose.setContent {
            PyteTheme { MixPanel(MixUiState(title = "Seed"), false, { starts++ }, {}, {},
                modifier = Modifier.width(300.dp), onSaveSong = { saves++ }) }
        }
        compose.onNodeWithText("Song zur Playlist", substring = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saves); assertEquals(0, starts) }
    }

    @Test fun previewDisablesMixWithoutHidingTheReason() {
        compose.setContent {
            PyteTheme { MixPanel(MixUiState(), true, {}, {}, {}) }
        }
        compose.onNodeWithText("Mix starten", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Mix nach dem Beenden der Vorschau starten.").assertIsDisplayed()
    }
}
