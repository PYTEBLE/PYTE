package dev.pyte.playback

import androidx.media3.common.PlaybackException
import dev.pyte.BuildConfig
import dev.pyte.data.remote.StreamUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-process only: the UI and service share this store, so Binder cannot strip the cause. */
@Singleton
class PlaybackDiagnostics @Inject constructor() {
    private val current = MutableStateFlow<String?>(null)
    val report = current.asStateFlow()

    fun record(error: PlaybackException) {
        current.value = buildPlaybackReport(BuildConfig.VERSION_NAME, error.errorCodeName, error)
    }

    fun clear() {
        current.value = null
    }
}

internal fun buildPlaybackReport(version: String, code: String, cause: Throwable): String {
    val chain = generateSequence(cause) { it.cause }.take(12).toList()
    val attempts = chain.filterIsInstance<StreamUnavailableException>()
        .firstOrNull { it.attempts.isNotEmpty() }?.attempts.orEmpty()
    return buildString {
        appendLine("PYTE $version")
        appendLine("Player: $code")
        if (attempts.isNotEmpty()) {
            attempts.forEach { appendLine("${it.client} / ${it.stage} / ${it.reason}") }
        } else {
            // Only exception type names; raw messages may contain IPs or signed URLs.
            appendLine("Ursachen: ${chain.joinToString(" → ") { it.javaClass.simpleName }}")
        }
        append("Keine URLs, Playlistnamen oder Netzwerkadressen enthalten.")
    }
}
