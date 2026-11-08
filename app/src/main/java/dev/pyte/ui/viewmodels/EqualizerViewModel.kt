package dev.pyte.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.settings.SettingsRepository
import dev.pyte.playback.EqPoint
import dev.pyte.playback.SoftwareEqualizer
import dev.pyte.playback.PlayerController
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val engine: SoftwareEqualizer,
    player: PlayerController,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val settings = repository.settings.catch { _error.value = "Could not load sound settings. Please reopen the app." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val device = engine.state
    val spectrum = engine.spectrum
    val playing = player.state.map { it.isPlaying }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun analyzing(enabled: Boolean) { engine.analyzing = enabled; if (!enabled) engine.clearSpectrum() }
    fun limiter(value: Boolean) = update { repository.equalizerLimiter(value) }
    private val writeLock = Mutex()
    fun enabled(value: Boolean) = update { repository.enableEqualizer(value) }
    fun select(id: String) = update { repository.selectSoundProfile(id) }
    fun curve(points: List<EqPoint>) = update { repository.setEqualizerCurve(points) }
    fun save(name: String, points: List<EqPoint>) = update { repository.saveSoundProfile(name, points) }
    fun delete(id: String) = update { repository.deleteSoundProfile(id) }
    fun retry() { settings.value?.equalizer?.let(engine::apply) }
    fun preview(points: List<EqPoint>) { settings.value?.equalizer?.let { engine.apply(it.copy(points = points)) } }
    fun cancelPreview() { settings.value?.equalizer?.let(engine::apply) }
    override fun onCleared() { analyzing(false); cancelPreview(); super.onCleared() }
    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            writeLock.withLock {
                _error.value = null
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _error.value = e.message ?: "Could not save sound settings"; cancelPreview() }
            }
        }
    }
}
