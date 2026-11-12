package dev.pyte.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.pyte.R

enum class UiSound(@RawRes val resource: Int) {
    TAP(R.raw.ui_tap), ON(R.raw.ui_on), OFF(R.raw.ui_off), SUCCESS(R.raw.ui_success),
}

/** Short, one-shot interface sounds. Callers check the user's setting before playing. */
class UiSoundPlayer(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = UiSound.entries.associateWith { pool.load(context, it.resource, 1) }

    fun play(sound: UiSound) {
        val id = ids[sound] ?: return
        pool.play(id, .55f, .55f, 1, 0, 1f)
    }

    fun release() = pool.release()
}

val LocalUiSounds = staticCompositionLocalOf<UiSoundPlayer?> { null }

@Composable
fun rememberUiSoundPlayer(): UiSoundPlayer {
    val context = LocalContext.current
    val player = remember(context) { UiSoundPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
