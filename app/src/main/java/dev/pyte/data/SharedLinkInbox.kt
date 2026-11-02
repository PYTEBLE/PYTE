package dev.pyte.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds a YouTube link received from a share sheet or deep link until the UI consumes it. */
@Singleton
class SharedLinkInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(link: String) {
        _pending.value = link
    }

    fun consume() {
        _pending.value = null
    }
}
