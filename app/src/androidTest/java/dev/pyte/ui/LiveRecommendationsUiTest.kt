package dev.pyte.ui

import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import dev.pyte.MainActivity
import dev.pyte.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Real windows and real time: a Compose test clock must not fast-forward a live audio session. */
@RunWith(AndroidJUnit4::class)
class LiveRecommendationsUiTest {
    @Test fun finalPlaylistSongOpensSuggestionsAndPreviewRestoresOriginalPlayback() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveRecommendations") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: context.getExternalFilesDir(null)!!
        output.mkdirs()
        fun node(selector: BySelector): androidx.test.uiautomator.UiObject2 {
            val found = device.wait(Until.findObject(selector), 120_000)
            if (found == null) {
                device.takeScreenshot(File(output, "discovery-failure.png"))
                device.dumpWindowHierarchy(File(output, "discovery-failure.xml"))
            }
            return requireNotNull(found) { "Missing UI: $selector" }
        }
        val title = "Discovery ${System.currentTimeMillis() % 100000}"
        val previousIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 500
        try {
        ActivityScenario.launch(MainActivity::class.java).use {
            val controller = MediaController.Builder(context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync().get(60, TimeUnit.SECONDS)
            fun <T> read(block: (MediaController) -> T): T {
                var value: T? = null
                instrumentation.runOnMainSync { value = block(controller) }
                return requireNotNull(value)
            }
            fun awaitController(predicate: (MediaController) -> Boolean) {
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
                while (System.nanoTime() < end) {
                    if (read(predicate)) return
                    Thread.sleep(200)
                }
                fail("Timed out waiting for the live media session")
            }
            try {
                instrumentation.runOnMainSync { controller.repeatMode = Player.REPEAT_MODE_OFF }
                node(By.text("Search")).click()
                node(By.clazz("android.widget.EditText")).text = "schönhauser Gustav"
                node(By.desc("Search YouTube Music")).click()
                node(By.descStartsWith("Options for schönhauser")).click()
                node(By.text("Add to local playlist")).click()
                node(By.text("Create & add"))
                node(By.clazz("android.widget.EditText")).text = title
                node(By.text("Create & add").enabled(true)).click()
                assertTrue(device.wait(Until.gone(By.text("Create & add")), 60_000))
                node(By.text("Library")).click()
                node(By.text("Your library"))
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(title))
                node(By.text(title)).click()
                node(By.text("Play")).click()
                node(By.text("Similar songs")) // Opened automatically, no discover-button press.
                node(By.text("Listen"))
                assertEquals("YGr2G2fc48U", read { it.currentMediaItem!!.mediaId })
                val entryId = read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("queueEntryId")!! }
                val origin = read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("sourcePlaylistId")!! }
                val position = read { it.currentPosition }
                device.takeScreenshot(File(output, "discovery.png"))
                node(By.text("Listen")).click()
                awaitController { it.currentMediaItem?.mediaId != "YGr2G2fc48U" && it.isPlaying }
                val previewTitle = read { it.currentMediaItem!!.mediaMetadata.title.toString() }
                node(By.desc("Add $previewTitle to a local playlist")).click()
                node(By.text("Added"))
                node(By.text("Return to playback")).click()
                awaitController { it.currentMediaItem?.mediaId == "YGr2G2fc48U" && it.isPlaying }
                assertEquals(1, read { it.mediaItemCount })
                assertEquals(entryId, read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("queueEntryId")!! })
                assertEquals(origin, read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("sourcePlaylistId")!! })
                assertTrue(read { it.currentPosition } >= position - 500)
                node(By.desc("Close suggestions")).click()
                assertTrue(device.wait(Until.gone(By.text("Similar songs")), 30_000))
                node(By.text("On this device • 2 tracks"))
                node(By.text("Find similar songs")).click()
                node(By.text("Similar songs"))
                node(By.desc("Close suggestions")).click()
                node(By.desc("Open player")).click()
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Similar to this song"))
                node(By.text("Similar to this song")).click()
                node(By.text("Listen"))
                node(By.text("Add")).click()
                node(By.text("Add to local playlist"))
                node(By.text(title)).click()
                node(By.text("Added"))
                node(By.desc("Close suggestions")).click()
                UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(5)
                node(By.desc("Close player")).click()
                node(By.desc("Playlist options")).click()
                node(By.text("Delete playlist")).click()
                node(By.text("Delete")).click()
            } finally {
                device.findObject(By.desc("Close suggestions"))?.click()
                instrumentation.runOnMainSync { controller.stop(); controller.release() }
            }
        }
        } finally { Configurator.getInstance().waitForIdleTimeout = previousIdleTimeout }
    }
}
