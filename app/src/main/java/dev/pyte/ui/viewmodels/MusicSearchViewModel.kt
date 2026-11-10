package dev.pyte.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.remote.MusicSearch
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineSearchState(
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MusicSearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val search: MusicSearch,
    private val library: LibraryRepository,
    private val text: dev.pyte.ui.UiStrings,
) : ViewModel() {
    val query = savedState.getStateFlow("musicQuery", "")
    private val _online = MutableStateFlow(OnlineSearchState())
    val online = _online.asStateFlow()
    private var request: Job? = null
    val playlists = library.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selected = MutableStateFlow<Track?>(null)
    val selected = _selected.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun setQuery(value: String) {
        request?.cancel()
        savedState["musicQuery"] = value.take(200)
        _online.value = OnlineSearchState()
    }

    fun submit(loadMore: Boolean = false) {
        val q = query.value.trim()
        if (q.isBlank() || _online.value.loading) return
        val previous = if (loadMore) _online.value else OnlineSearchState()
        val token = if (loadMore) previous.continuation ?: return else null
        request?.cancel()
        _online.value = previous.copy(loading = true, searched = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = search.search(q, token)
                _online.value = OnlineSearchState(
                    tracks = (previous.tracks + page.tracks).distinctBy { it.videoId },
                    continuation = page.continuation?.takeUnless { it == token }, searched = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _online.value = previous.copy(searched = true,
                    error = text.get(dev.pyte.R.string.error_search))
            }
        }
    }

    fun select(track: Track) { _saveError.value = null; _selected.value = track }
    fun dismissSelection() { if (!_saving.value) _selected.value = null }
    fun consumeMessage() { _message.value = null }

    fun addToPlaylist(id: String? = null, newTitle: String? = null) {
        val track = _selected.value ?: return
        if (_saving.value) return
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val added = if (id != null) library.addToPlaylist(id, track) else {
                    library.createPlaylist(requireNotNull(newTitle), track)
                    true
                }
                _selected.value = null
                _message.value = text.get(if (added) dev.pyte.R.string.song_saved else dev.pyte.R.string.already_in_playlist)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveError.value = text.get(dev.pyte.R.string.error_save_song)
            } finally {
                _saving.value = false
            }
        }
    }
}
