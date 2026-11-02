package dev.pyte.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.SharedLinkInbox
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.domain.PlaylistSummary
import dev.pyte.domain.Track
import dev.pyte.playback.PlayerController
import dev.pyte.playback.PlaybackDiagnostics
import dev.pyte.playback.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SHARE_TIMEOUT_MS = 5_000L

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val inbox: SharedLinkInbox,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSummary>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    val pendingLink: StateFlow<String?> = inbox.pending

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun importPlaylist(link: String, onImported: (String) -> Unit) {
        viewModelScope.launch {
            _importState.value = ImportUiState.Loading
            try {
                val id = repo.importPlaylist(link)
                _importState.value = ImportUiState.Idle
                inbox.consume()
                onImported(id)
            } catch (e: Exception) {
                _importState.value = ImportUiState.Error(e.message ?: "Import failed. Try again.")
            }
        }
    }

    fun dismissImport() {
        _importState.value = ImportUiState.Idle
        inbox.consume()
    }
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LibraryRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedState["playlistId"])

    val detail: StateFlow<Pair<PlaylistSummary, List<Track>>?> = repo.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), null)

    val favoriteIds: StateFlow<Set<String>> = repo.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    val playerState: StateFlow<PlayerUiState> = player.state

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)

    fun shuffleAll(tracks: List<Track>) {
        player.playQueue(tracks.shuffled(), 0)
    }

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deletePlaylist(playlistId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val player: PlayerController,
) : ViewModel() {

    val favorites: StateFlow<List<Track>> = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    val favoriteIds: StateFlow<Set<String>> = repo.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Track>> = _query
        .flatMapLatest { q -> if (q.isBlank()) repo.observeFavorites() else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    private val repo: LibraryRepository,
    private val diagnostics: PlaybackDiagnostics,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = player.state
    val diagnosticReport: StateFlow<String?> = diagnostics.report

    fun dismissDiagnostics() = diagnostics.clear()

    val favoriteIds: StateFlow<Set<String>> = repo.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun playIndex(index: Int) = player.playIndex(index)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }
}
