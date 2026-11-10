package dev.pyte.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.playback.EqualizerSettings
import dev.pyte.playback.EqualizerState
import dev.pyte.playback.SoundProfile
import dev.pyte.playback.EqualizerCurves
import dev.pyte.ui.screens.EqualizerContent
import dev.pyte.ui.theme.PyteTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EqualizerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsupportedDeviceDisablesSoundControlsAndExplainsWhy() {
        compose.setContent {
            PyteTheme {
                EqualizerContent(EqualizerSettings(), EqualizerState(message = "Unsupported output"), null,
                    {}, {}, {}, {}, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Equalizer aktivieren").assertIsNotEnabled()
        compose.onNodeWithText("Unsupported output").assertExists()
        compose.onNodeWithContentDescription("Equalizer 63 Hz").assertDoesNotExist()
    }

    @Test fun soundProfileDeletionRequiresConfirmation() {
        var deleted: String? = null
        val settings = EqualizerSettings(profiles = listOf(SoundProfile("mine", "Headphones", EqualizerCurves.flat)))
        compose.setContent {
            PyteTheme {
                EqualizerContent(settings, EqualizerState(), null, {}, {}, {}, {}, { _, _ -> }, { deleted = it }, {})
            }
        }
        compose.onNodeWithText("Löschen").performScrollTo().performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Löschen").performClick()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.runOnIdle { assertEquals("mine", deleted) }
    }

    @Test fun fifteenBandGraphAndAccessibleHalfDbAdjustmentCommitCorrectValues() {
        val settings = mutableStateOf(EqualizerSettings(enabled = true))
        var commits = 0
        compose.setContent {
            PyteTheme {
                EqualizerContent(settings.value, EqualizerState(frequencies = EqualizerCurves.frequencies, available = true), null,
                    {}, {}, {}, { points -> commits++; settings.value = settings.value.copy(points = points) }, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Equalizer-Bandkurve. Präzise Regler unter dem Diagramm.")
            .performScrollTo().assertIsDisplayed()
        captureUi("equalizer-15-bands.png")
        compose.onNodeWithContentDescription("Equalizer 25 Hz").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(2.5f) }
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(250, settings.value.points.first { it.hz == 25 }.gainMb)
            assertEquals(15, settings.value.points.size)
        }
    }
}
