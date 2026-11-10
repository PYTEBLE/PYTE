package dev.pyte.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.playback.MixUiState
import dev.pyte.ui.components.MixAction
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingMixCanBeCancelledAndCannotDispatchTwice() {
        val state = mutableStateOf(MixUiState())
        var starts = 0
        var cancels = 0
        var restores = 0
        compose.setContent {
            PyteTheme {
                MixAction(state.value, false,
                    onStart = { starts++; state.value = MixUiState(loading = true) },
                    onCancel = { cancels++; state.value = MixUiState() },
                    onRestore = { restores++ }, modifier = Modifier.width(300.dp))
            }
        }
        compose.onNodeWithText("Mix starten", substring = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Abbrechen").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = MixUiState(title = "Seed song") }
        compose.onNodeWithText("Dein Mix").performClick()
        compose.onNodeWithText("Vorherige Warteschlange wiederherstellen").performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, cancels)
            assertEquals(1, restores)
        }
    }

    @Test fun activeMixStartsANewMixOnlyWhenChosen() {
        var starts = 0
        compose.setContent {
            PyteTheme { MixAction(MixUiState(title = "Seed"), false, { starts++ }, {}, {},
                modifier = Modifier.width(300.dp)) }
        }
        compose.onNodeWithText("Dein Mix").performClick()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText("Neuen Mix starten").performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun previewDisablesMixWithoutStartingIt() {
        compose.setContent {
            PyteTheme { MixAction(MixUiState(), true, {}, {}, {}) }
        }
        compose.onNodeWithText("Mix starten", substring = true).assertIsNotEnabled()
    }
}
