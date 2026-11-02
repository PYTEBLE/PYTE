package dev.pyte.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.data.settings.AppSettings
import dev.pyte.ui.screens.SettingsContent
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun togglesDispatchAndHistoryDeletionRequiresConfirmation() {
        var skipSilence = false
        var history = true
        var clears = 0
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            PyteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsContent(settings, false,
                        onSkipSilence = { skipSilence = it; settings = settings.copy(skipSilence = it) },
                        onRecordHistory = { history = it; settings = settings.copy(recordHistory = it) },
                        onClearHistory = { clears++ })
                }
            }
        }
        compose.onNodeWithText("Skip silence").performScrollTo().performClick()
        compose.onNodeWithText("Save listening history").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(skipSilence); assertFalse(history) }
        compose.onNodeWithText("Clear listening history").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Clear listening history").performClick()
        compose.onNodeWithText("Clear history", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }
}
