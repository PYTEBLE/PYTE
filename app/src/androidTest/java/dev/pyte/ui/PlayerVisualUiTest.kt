package dev.pyte.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.pyte.MainActivity
import dev.pyte.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Visual fixture only: does not claim remote playback, inject spectra, or alter library data. */
@RunWith(AndroidJUnit4::class)
class PlayerVisualUiTest {
    @Test fun playerActionsAndSoundStudioAreReachableWithArtwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val cover = File(context.cacheDir, "ui-review-cover.png")
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 800f, 800f, intArrayOf(0xFF5225D8.toInt(), 0xFFDB387D.toInt(), 0xFFEFAD6C.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 800f, 800f, paint)
        paint.shader = null; paint.color = 0x99FFFFFF.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f
        repeat(7) { canvas.drawCircle(400f, 360f, 80f + it * 35f, paint) }
        paint.style = Paint.Style.FILL; paint.textSize = 62f; paint.color = -1
        canvas.drawText("SOUND / SPACE", 110f, 690f, paint)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        ActivityScenario.launch(MainActivity::class.java).use {
            val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .buildAsync().get(60, TimeUnit.SECONDS)
            lateinit var original: List<MediaItem>
            var index = 0; var position = 0L; var playing = false
            instrumentation.runOnMainSync {
                original = (0 until controller.mediaItemCount).map(controller::getMediaItemAt)
                index = controller.currentMediaItemIndex; position = controller.currentPosition; playing = controller.playWhenReady
                controller.pause()
                controller.setMediaItem(MediaItem.Builder().setMediaId("YGr2G2fc48U").setMediaMetadata(MediaMetadata.Builder()
                    .setTitle("Eine neue Klangwelt").setArtist("PYTE · visuelle Testszene")
                    .setArtworkUri(Uri.fromFile(cover)).setExtras(Bundle().apply { putString("queueEntryId", "ui-review"); putLong("durationMs", 120000) }).build()).build())
            }
            try {
                val open = device.wait(Until.findObject(By.desc("Player öffnen")), 60000)
                assertNotNull(open); open!!.click()
                assertNotNull(device.wait(Until.findObject(By.text("Eine neue Klangwelt")), 60000))
                assertNotNull(device.wait(Until.findObject(By.desc("Abspielen")), 30000))
                captureUi("player-artwork.png")
                val sound = device.wait(Until.findObject(By.textContains("Klangstudio")), 30000)
                assertNotNull(sound); sound!!.click()
                assertNotNull(device.wait(Until.findObject(By.text("15 Bänder. Dein Klang.")), 30000))
                captureUi("sound-studio.png")
                device.findObject(By.desc("Schließen"))!!.click()
                assertNotNull(device.wait(Until.findObject(By.desc("Warteschlange öffnen")), 30000))
            } finally {
                instrumentation.runOnMainSync {
                    if (original.isEmpty()) { controller.stop(); controller.clearMediaItems() }
                    else { controller.setMediaItems(original, index.coerceIn(original.indices), position); controller.prepare(); controller.playWhenReady = playing }
                    controller.release()
                }
                cover.delete()
            }
        }
    }
}
