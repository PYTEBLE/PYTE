package dev.pyte.playback

/** Extract only a status code; never show signed media URLs or raw server responses. */
internal fun playbackErrorMessage(errorCodeName: String, cause: Throwable?): String {
    val statusPattern = Regex("(?:HTTP\\s+|Response code:\\s*)(\\d{3})")
    val status = generateSequence(cause) { it.cause }
        .mapNotNull { statusPattern.find(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }
        .firstOrNull()
    return when (status) {
        403, 410 -> "YouTube lehnt den Audiozugriff ab (HTTP $status). Bitte einen anderen Titel oder ein anderes Netzwerk testen."
        429 -> "YouTube begrenzt die Anfragen (HTTP 429). Bitte später erneut versuchen."
        null -> "Wiedergabe fehlgeschlagen ($errorCodeName). Bitte erneut versuchen."
        else -> "Audio konnte nicht geladen werden (HTTP $status). Bitte erneut versuchen."
    }
}
