package dev.pyte.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.pyte.R
import java.util.concurrent.ConcurrentHashMap

enum class UiSound(@RawRes val resource: Int) {
    TAP(R.raw.ui_tap), ON(R.raw.ui_on), OFF(R.raw.ui_off), SUCCESS(R.raw.ui_success),
    TICK(R.raw.ui_tick), DETENT(R.raw.ui_detent),
}

/** Short, preloaded one-shots. UiFeedback checks preferences and foreground state. */
class UiSoundPlayer(context: Context) {
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private var released = false
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
    }
    private val ids = UiSound.entries.associateWith { pool.load(context.applicationContext, it.resource, 1) }

    fun play(sound: UiSound) {
        if (released) return
        val id = ids[sound]?.takeIf { it in loaded } ?: return
        val volume = if (sound == UiSound.TICK) .32f else .55f
        // One-shots never request audio focus or interrupt/duck music playback.
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        if (!released) { released = true; pool.release(); loaded.clear() }
    }
}

@Composable
fun rememberUiSoundPlayer(): UiSoundPlayer {
    val context = LocalContext.current
    val player = remember(context) { UiSoundPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
