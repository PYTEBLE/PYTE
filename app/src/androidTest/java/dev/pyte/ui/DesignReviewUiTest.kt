package dev.pyte.ui

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Never launches, force-stops, clears or modifies dev.pyte. Only the isolated review package. */
@RunWith(AndroidJUnit4::class)
class DesignReviewUiTest {
    private var statusInset = 0
    private var navigationInset = 0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    private fun review(page: String = "library", state: String = "normal", large: Boolean = false,
        reduced: Boolean = false, block: () -> Unit) {
        assumeTrue("Requires -PpyteTestBuildType=designReview", context.packageName == "dev.pyte.designreview")
        device.wakeUp()
        check(!context.getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Please unlock the phone before design review tests." }
        val previousTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 1000
        val intent = Intent().setComponent(ComponentName(context.packageName, "dev.pyte.designreview.DesignReviewActivity"))
            .putExtra("page", page).putExtra("state", state).putExtra("largeText", large).putExtra("reducedMotion", reduced)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            ActivityScenario.launch<ComponentActivity>(intent).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 15_000))
                scenario.onActivity { activity ->
                    val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    statusInset = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
                    navigationInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                }
                block()
            }
        } finally { Configurator.getInstance().waitForIdleTimeout = previousTimeout }
    }

    private fun node(tag: String) = checkNotNull(device.wait(Until.findObject(By.res(tag)), 8000)) { "Missing review control: $tag" }
    private fun capture(name: String) {
        val dir = File(context.getExternalFilesDir(null), "design-review").apply { mkdirs() }
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        // Exclude the system status area (which can contain private ongoing-activity labels).
        val cropped = Bitmap.createBitmap(screenshot, 0, statusInset, screenshot.width,
            screenshot.height - statusInset - navigationInset)
        try {
            File(dir, name).outputStream().use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            if (cropped !== screenshot) cropped.recycle()
            screenshot.recycle()
        }
    }

    @Test fun representativeScreensRenderOnDevice() {
        review { node("review-library"); capture("library.png") }
        review(page = "player") { node("review-save"); capture("player.png") }
        review(page = "actions") { node("review-action-save"); capture("actions.png") }
    }

    @Test fun saveAndUndoUseOnlyDemoDataAndRetainTheSelectedSong() = review(page = "player") {
        node("review-save").click()
        node("review-pick-night").click()
        val undo = device.wait(Until.findObject(By.text("Rückgängig")), 8000)
        assertNotNull(undo)
        undo!!.click()
        node("review-save").click()
        assertNotNull(device.wait(Until.findObject(By.text("24 Songs")), 8000))
        assertTrue(device.hasObject(By.text("Zwischen den Lichtern")))
        assertFalse(device.hasObject(By.text("Bereits gespeichert · Entwurf")))
        capture("picker.png")
    }

    @Test fun actionsHaveAConsistentPickerAndSearch() = review(page = "actions") {
        node("review-action-save").click()
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
        assertNotNull(input)
        input!!.text = "Blaue"
        node("review-pick-blue")
        assertFalse(device.hasObject(By.res("review-pick-night")))
        // ACTION_SET_TEXT doesn't necessarily open an IME; use the explicit back control.
        val back = device.wait(Until.findObject(By.desc("Zurück")), 8000)
        assertNotNull(back)
        back!!.click()
        node("review-action-save")
    }

    @Test fun largeTextKeepsPrimaryPlayerControlsReachableWithoutChangingPhoneSettings() = review(page = "player", large = true, reduced = true) {
        val save = node("review-save").visibleBounds
        assertTrue(save.top > 0 && save.bottom <= device.displayHeight)
        assertTrue(save.height() >= (48 * context.resources.displayMetrics.density).toInt())
        node("review-play")
        capture("player-large-text.png")
    }

    @Test fun loadingEmptyAndErrorStatesRenderAndRetryRecovers() {
        review(state = "loading", reduced = true) { node("review-library"); capture("library-loading.png") }
        review(state = "empty") {
            assertNotNull(device.wait(Until.findObject(By.text("Deine Sammlung beginnt hier")), 8000))
            capture("library-empty.png")
        }
        review(state = "error") {
            val retry = device.wait(Until.findObject(By.text("Erneut versuchen")), 8000)
            assertNotNull(retry); capture("library-error.png"); retry!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("Nachtfahrt")), 8000))
        }
    }
}
