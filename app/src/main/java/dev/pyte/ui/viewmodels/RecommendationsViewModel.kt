package dev.pyte.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.offline.OfflineStore
import dev.pyte.data.remote.RecommendationsRepository
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.data.settings.SettingsRepository
import dev.pyte.playback.DiscoveryPromptGate
import dev.pyte.playback.PlayerController
import dev.pyte.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryContext(val title: String, val playlistId: String? = null, val song: Track? = null, val automatic: Boolean = false)
data class DiscoveryState(
    val context: DiscoveryContext? = null,
    val loading: Boolean = false,
    val songs: List<SongRecommendation> = emptyList(),
    val error: String? = null,
    val partial: Boolean = false,
    val added: Set<String> = emptySet(),
    val savingId: String? = null,
    val picker: Track? = null,
    val saveError: String? = null,
    val status: String? = null,
)

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val repository: RecommendationsRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val settings: SettingsRepository,
    private val offline: OfflineStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryState())
    val state = _state.asStateFlow()
    val playback = player.state
    val playlists = library.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val visible = MutableStateFlow(false to false)
    private val gate = DiscoveryPromptGate()
    private var request: Job? = null

    init {
        viewModelScope.launch {
            combine(player.state, settings.settings.catch { emit(dev.pyte.data.settings.AppSettings(autoRecommendations = false)) }, visible) { playback, preferences, visibility ->
                if (gate.shouldOpen(playback, preferences.autoRecommendations, visibility.first,
                        visibility.second && _state.value.context == null)) playback.playlistOrigin?.playlistId else null
            }.filterNotNull().collect { openPlaylist(it, automatic = true) }
        }
    }

    fun visibility(foreground: Boolean, unobstructed: Boolean) { visible.value = foreground to unobstructed }
    fun openPlaylist(id: String, title: String = "Your playlist", automatic: Boolean = false) = open(DiscoveryContext(title, id, automatic = automatic))
    fun openSong(song: Track) = open(DiscoveryContext(song.title, song = song))

    private fun open(context: DiscoveryContext) {
        if (_state.value.savingId != null) return
        player.finishPreview()
        request?.cancel()
        _state.value = DiscoveryState(context, loading = true)
        load(context)
    }

    fun retry() { _state.value.context?.let { load(it) } }

    private fun load(context: DiscoveryContext) {
        request?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, status = null)
        request = viewModelScope.launch {
            try {
                val detail = context.playlistId?.let { library.observePlaylist(it).first() ?: error("Playlist no longer exists") }
                val tracks = detail?.second ?: listOfNotNull(context.song)
                val resolved = context.copy(title = detail?.first?.title ?: context.title)
                _state.value = _state.value.copy(context = resolved)
                val batch = repository.discover(tracks, tracks.map { it.videoId }.toSet() + _state.value.added)
                _state.value = _state.value.copy(loading = false, songs = batch.songs, partial = batch.partial)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false,
                    error = e.message ?: "Could not load suggestions. Please try again.")
            }
        }
    }

    fun listen(track: Track) {
        if (player.state.value.isPreview && player.state.value.current?.videoId == track.videoId) player.togglePlayPause()
        else player.preview(track)
    }
    fun returnToPlayback() { player.finishPreview() }
    fun dismiss() {
        if (_state.value.savingId != null) return
        request?.cancel()
        player.finishPreview()
        _state.value = DiscoveryState()
    }

    fun add(track: Track) {
        val id = _state.value.context?.playlistId
        if (id == null) _state.value = _state.value.copy(picker = track, saveError = null)
        else save(track, id, null)
    }
    fun dismissPicker() { if (_state.value.savingId == null) _state.value = _state.value.copy(picker = null, saveError = null) }
    fun choosePlaylist(id: String) { _state.value.picker?.let { save(it, id, null) } }
    fun createPlaylist(title: String) { _state.value.picker?.let { save(it, null, title) } }

    private fun save(track: Track, id: String?, title: String?) {
        if (_state.value.savingId != null || _state.value.context == null) return
        _state.value = _state.value.copy(savingId = track.videoId, saveError = null, status = null)
        viewModelScope.launch {
            try {
                val added = if (id != null) library.addToPlaylist(id, track) else {
                    library.createPlaylist(checkNotNull(title), track); true
                }
                _state.value = _state.value.copy(savingId = null, picker = null,
                    added = _state.value.added + track.videoId,
                    status = if (added) "Added to your local playlist" else "Already in this playlist")
                // Respect existing offline selections and Wi-Fi/storage constraints for newly added songs.
                try { offline.schedule() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { _state.value = _state.value.copy(status = "Song added. Offline downloads will retry later.") }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(savingId = null,
                    saveError = "Could not add this song. The playlist may have been deleted. Please try again.")
            }
        }
    }

    override fun onCleared() {
        player.finishPreview()
        super.onCleared()
    }
}
