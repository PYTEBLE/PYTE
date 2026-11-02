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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    val settings = repository.settings.catch { _loadFailed.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun skipSilence(enabled: Boolean) = update { repository.setSkipSilence(enabled) }
    fun recordHistory(enabled: Boolean) = update { library.setHistoryEnabled(enabled) }
    fun clearHistory() = update {
        library.clearHistory()
        _message.value = "Listening history cleared"
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
                _message.value = "Could not save changes. Please try again."
            } finally {
                _busy.value = false
            }
        }
    }
}
