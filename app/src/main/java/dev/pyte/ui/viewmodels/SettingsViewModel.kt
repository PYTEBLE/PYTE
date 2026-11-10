package dev.pyte.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.pyte.data.offline.OfflineStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val library: LibraryRepository,
    private val offline: OfflineStore,
    private val text: dev.pyte.ui.UiStrings,
) : ViewModel() {
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    val settings = repository.settings.catch { _loadFailed.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val offlineBytes = flow {
        while (true) { emit(offline.usedBytes()); delay(2000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    fun downloadLimit(mb: Long) = update { repository.setDownloadLimit(mb); offline.schedule() }
    fun pauseDownloads(paused: Boolean) = update { repository.pauseDownloads(paused); if (!paused) offline.schedule() }

    fun skipSilence(enabled: Boolean) = update { repository.setSkipSilence(enabled) }
    fun battery(value: dev.pyte.data.settings.BatterySettings) = update {
        val old = repository.settings.first().battery
        repository.setBattery(value)
        if (old.active(old.pauseDownloads) != value.active(value.pauseDownloads)) offline.schedule()
    }
    fun dynamicCoverTheme(enabled: Boolean) = update { repository.setDynamicCoverTheme(enabled) }
    fun effectLevel(level: dev.pyte.data.settings.EffectLevel) = update { repository.setEffectLevel(level) }
    fun hapticFeedback(enabled: Boolean) = update { repository.setHapticFeedback(enabled) }
    fun autoRecommendations(enabled: Boolean) = update { repository.setAutoRecommendations(enabled) }
    fun recordHistory(enabled: Boolean) = update { library.setHistoryEnabled(enabled) }
    fun clearHistory() = update {
        library.clearHistory()
        _message.value = text.get(dev.pyte.R.string.history_cleared)
    }
    fun dismissMessage() { _message.value = null }

    private fun update(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = text.get(dev.pyte.R.string.error_settings)
            } finally {
                _busy.value = false
            }
        }
    }
}
