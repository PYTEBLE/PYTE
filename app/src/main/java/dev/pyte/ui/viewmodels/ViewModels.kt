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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.pyte.data.offline.OfflineStore
import dev.pyte.data.lyrics.Lyrics
import dev.pyte.data.lyrics.LyricsRepository
import dev.pyte.data.remote.CoverPaletteRepository
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transformLatest

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
    private val player: PlayerController,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSummary>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    val pendingLink: StateFlow<String?> = inbox.pending
    val recentTracks = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    fun playRecent(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)

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
            } catch (e: CancellationException) {
                throw e
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

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    data object Missing : PlaylistUiState
    data class Loaded(val summary: PlaylistSummary, val tracks: List<Track>) : PlaylistUiState
}

data class PlaylistActionState(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LibraryRepository,
    private val player: PlayerController,
    private val offline: OfflineStore,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedState["playlistId"])
    val offlinePlaylists = offline.selected.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())
    val downloads = offline.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())
    fun offlineEnabled(enabled: Boolean) = perform(if (enabled) "Download queued · Wi-Fi only" else "Offline downloads removed") {
        offline.setSelected(playlistId, enabled)
    }
    fun retryDownloads() { viewModelScope.launch { offline.schedule() } }

    val detail: StateFlow<PlaylistUiState> = repo.observePlaylist(playlistId)
        .map { if (it == null) PlaylistUiState.Missing else PlaylistUiState.Loaded(it.first, it.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), PlaylistUiState.Loading)

    private val _action = MutableStateFlow(PlaylistActionState())
    val action = _action.asStateFlow()

    fun consumeMessage() { _action.value = _action.value.copy(message = null) }

    fun rename(title: String) = perform("Playlist renamed") { repo.renamePlaylist(playlistId, title) }
    fun refresh() = perform("Playlist updated") { repo.refreshPlaylist(playlistId) }

    private fun perform(message: String, block: suspend () -> Unit) {
        if (_action.value.busy) return
        _action.value = PlaylistActionState(busy = true)
        viewModelScope.launch {
            try {
                block()
                _action.value = PlaylistActionState(message = message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _action.value = PlaylistActionState(message = e.message ?: "Action failed. Try again.")
            }
        }
    }

    val favoriteIds: StateFlow<Set<String>> = repo.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    val playerState: StateFlow<PlayerUiState> = player.state

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isNotEmpty()) player.playQueue(tracks, tracks.indices.random(), shuffle = true)
    }

    fun enqueueNext(track: Track) = player.enqueueNext(track)
    fun enqueueLater(track: Track) = player.enqueueLater(track)

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        perform("Playlist removed") {
            repo.deletePlaylist(playlistId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
        .debounce(200).distinctUntilChanged()
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun enqueueNext(track: Track) = player.enqueueNext(track)
    fun enqueueLater(track: Track) = player.enqueueLater(track)
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    private val repo: LibraryRepository,
    private val diagnostics: PlaybackDiagnostics,
    private val lyricsRepository: LyricsRepository,
    settings: SettingsRepository,
    palette: CoverPaletteRepository,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = player.state
    val appearance = settings.settings.catch { emit(dev.pyte.data.settings.AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), dev.pyte.data.settings.AppSettings())
    val coverColors = combine(player.state.map { it.current }.distinctUntilChanged(),
        settings.settings.map { it.dynamicCoverTheme }.distinctUntilChanged()) { track, enabled -> track.takeIf { enabled } }
        .transformLatest { track ->
            if (track == null) emit(null) else emit(palette.colors(track))
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), null)
    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics = _lyrics.asStateFlow()
    suspend fun loadLyrics(track: Track) {
        _lyrics.value = LyricsUiState(trackId = track.videoId, loading = true)
        try {
            val result = lyricsRepository.get(track)
            _lyrics.value = LyricsUiState(trackId = track.videoId, lyrics = result,
                message = if (result == null) "No lyrics available for this song." else null)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            _lyrics.value = LyricsUiState(trackId = track.videoId, message = "Lyrics could not be loaded. Check your connection and try again.")
        }
    }
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
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = player.removeQueueItem(index)

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }
}

data class LyricsUiState(val trackId: String? = null, val loading: Boolean = false, val lyrics: Lyrics? = null, val message: String? = null)
