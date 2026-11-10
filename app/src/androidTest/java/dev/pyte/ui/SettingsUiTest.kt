package dev.pyte.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.data.settings.AppSettings
import dev.pyte.ui.screens.DataPage
import dev.pyte.ui.screens.HistoryPage
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyToggleDispatchesAndDeletionRequiresConfirmation() {
        var history = true
        var clears = 0
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            PyteTheme {
                HistoryPage(settings, false,
                    onRecordHistory = { history = it; settings = settings.copy(recordHistory = it) },
                    onClearHistory = { clears++ }, onBack = {})
            }
        }
        compose.onNodeWithText("Hörverlauf speichern").performClick()
        compose.runOnIdle { assertFalse(history) }
        compose.onNodeWithText("Hörverlauf löschen").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Hörverlauf löschen").performClick()
        compose.onNodeWithText("Löschen", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test fun dataPageShowsExportAndImportActions() {
        compose.setContent { PyteTheme { DataPage(false, {}, {}, {}) } }
        compose.onNodeWithText("Backup speichern").assertExists()
        compose.onNodeWithText("Backup-Datei wählen").assertExists()
    }
}
