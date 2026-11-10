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
                node(By.text("Suchen")).click()
                node(By.clazz("android.widget.EditText")).text = "schönhauser Gustav"
                node(By.desc("Auf YouTube Music suchen")).click()
                node(By.descStartsWith("Aktionen für „schönhauser")).click()
                node(By.text("Zur Playlist")).click()
                node(By.text("Erstellen und hinzufügen"))
                device.findObjects(By.clazz("android.widget.EditText")).last().text = title
                node(By.text("Erstellen und hinzufügen").enabled(true)).click()
                assertTrue(device.wait(Until.gone(By.text("Erstellen und hinzufügen")), 60_000))
                node(By.text("Bibliothek")).click()
                node(By.text("Deine Playlists"))
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(title))
                node(By.text(title)).click()
                node(By.text("Abspielen")).click()
                node(By.text("Ähnliche Songs")) // Opened automatically, no discover-button press.
                node(By.text("Probehören"))
                assertEquals("YGr2G2fc48U", read { it.currentMediaItem!!.mediaId })
                val entryId = read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("queueEntryId")!! }
                val origin = read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("sourcePlaylistId")!! }
                val position = read { it.currentPosition }
                device.takeScreenshot(File(output, "discovery.png"))
                node(By.text("Probehören")).click()
                awaitController { it.currentMediaItem?.mediaId != "YGr2G2fc48U" && it.isPlaying }
                val previewTitle = read { it.currentMediaItem!!.mediaMetadata.title.toString() }
                node(By.desc("„$previewTitle“ zur Playlist hinzufügen")).click()
                node(By.text(title)).click()
                node(By.textContains("gespeichert"))
                node(By.text("Zur bisherigen Wiedergabe")).click()
                awaitController { it.currentMediaItem?.mediaId == "YGr2G2fc48U" && it.isPlaying }
                assertEquals(1, read { it.mediaItemCount })
                assertEquals(entryId, read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("queueEntryId")!! })
                assertEquals(origin, read { it.currentMediaItem!!.mediaMetadata.extras!!.getString("sourcePlaylistId")!! })
                assertTrue(read { it.currentPosition } >= position - 500)
                node(By.desc("Schließen")).click()
                assertTrue(device.wait(Until.gone(By.text("Ähnliche Songs")), 30_000))
                node(By.text("LOKAL • 2 Songs"))
                node(By.text("Ähnliche Songs entdecken")).click()
                node(By.text("Ähnliche Songs"))
                node(By.desc("Schließen")).click()
                node(By.desc("Player öffnen")).click()
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Entdecken"))
                node(By.text("Entdecken")).click()
                node(By.text("Probehören"))
                node(By.text("Zur Playlist")).click()
                node(By.text("Name der neuen Playlist"))
                node(By.text(title)).click()
                assertTrue(device.wait(Until.hasObject(By.textContains("gespeichert")), 30_000) ||
                    device.hasObject(By.textContains("bereits")))
                node(By.desc("Schließen")).click()
                UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(5)
                node(By.desc("Player schließen")).click()
                node(By.desc("Playlist-Aktionen")).click()
                node(By.text("Playlist löschen")).click()
                node(By.text("Löschen")).click()
            } finally {
                device.findObject(By.desc("Schließen"))?.click()
                instrumentation.runOnMainSync { controller.stop(); controller.release() }
            }
        }
        } finally { Configurator.getInstance().waitForIdleTimeout = previousIdleTimeout }
    }
}
