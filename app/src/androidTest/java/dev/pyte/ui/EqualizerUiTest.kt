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
        compose.onNodeWithContentDescription("Enable equalizer").assertIsNotEnabled()
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
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Delete").performClick()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.runOnIdle { assertEquals("mine", deleted) }
    }
}
