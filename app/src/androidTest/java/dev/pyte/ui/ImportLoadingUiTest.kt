package dev.pyte.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pyte.ui.screens.ImportSheet
import dev.pyte.ui.theme.PyteTheme
import dev.pyte.ui.viewmodels.ImportUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class ImportLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstPlaylistImportHasRoundSpinnerAndClearLoadingLabel() {
        compose.setContent {
            PyteTheme { ImportSheet("https://music.youtube.com/playlist?list=test", {}, {},
                MutableStateFlow<ImportUiState>(ImportUiState.Loading)) }
        }
        compose.onNodeWithTag("playlistImportSpinner").assertIsDisplayed()
            .assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(18f)).assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(18f))
        compose.onNodeWithText("Importiere …").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        captureUi("import-loading.png")
    }
}

internal fun captureUi(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
        FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
