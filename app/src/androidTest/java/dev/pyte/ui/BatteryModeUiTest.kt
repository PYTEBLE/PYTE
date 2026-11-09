package dev.pyte.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.data.settings.BatterySettings
import dev.pyte.ui.screens.BatteryModeDialog
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryModeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choicesAreDisabledUntilEnabledAndOnlyApplySaves() {
        var saved: BatterySettings? = null
        compose.setContent { PyteTheme { BatteryModeDialog(BatterySettings(), false, { saved = it }, {}) } }
        compose.onNodeWithText("Lower mobile-data quality").assertExists()
        compose.onNodeWithText("Enable battery mode").performClick()
        assertNull(saved)
        compose.onNodeWithText("Lower mobile-data quality").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle {
            assertTrue(saved!!.enabled)
            assertFalse(saved!!.lowData)
            assertEquals(0, saved!!.screenOffTimerMinutes)
        }
    }

    @Test fun cancelDoesNotSaveDraft() {
        var saved = false
        compose.setContent { PyteTheme { BatteryModeDialog(BatterySettings(), false, { saved = true }, {}) } }
        compose.onNodeWithText("Enable battery mode").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(saved)
    }
}
