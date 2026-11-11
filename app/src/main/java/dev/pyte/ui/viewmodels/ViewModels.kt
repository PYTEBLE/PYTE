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
    private val text: dev.pyte.ui.UiStrings,
    offline: OfflineStore,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSummary>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())

    val offlinePlaylists: StateFlow<Set<String>> = offline.selected.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    private val _createState = MutableStateFlow<String?>(null)
    val createMessage = _createState.asStateFlow()

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            try {
                repo.createEmptyPlaylist(title)
                _createState.value = text.get(dev.pyte.R.string.playlist_created)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _createState.value = text.get(dev.pyte.R.string.error_action)
            }
        }
    }
    fun consumeCreateMessage() { _createState.value = null }

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
                _importState.value = ImportUiState.Error(text.get(dev.pyte.R.string.error_import))
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
    private val text: dev.pyte.ui.UiStrings,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedState["playlistId"])
    val offlinePlaylists = offline.selected.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())
    val downloads = offline.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())
    fun offlineEnabled(enabled: Boolean) = perform(text.get(if (enabled) dev.pyte.R.string.download_queued else dev.pyte.R.string.download_removed)) {
        offline.setSelected(playlistId, enabled)
    }
    fun retryDownloads() { viewModelScope.launch { offline.schedule() } }

    val detail: StateFlow<PlaylistUiState> = repo.observePlaylist(playlistId)
        .map { if (it == null) PlaylistUiState.Missing else PlaylistUiState.Loaded(it.first, it.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), PlaylistUiState.Loading)

    private val _action = MutableStateFlow(PlaylistActionState())
    val action = _action.asStateFlow()

    fun consumeMessage() { _action.value = _action.value.copy(message = null) }

    fun rename(title: String) = perform(text.get(dev.pyte.R.string.playlist_renamed)) { repo.renamePlaylist(playlistId, title) }
    fun refresh() = perform(text.get(dev.pyte.R.string.playlist_updated)) { repo.refreshPlaylist(playlistId) }

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
                _action.value = PlaylistActionState(message = text.get(dev.pyte.R.string.error_action))
            }
        }
    }

    val favoriteIds: StateFlow<Set<String>> = repo.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptySet())

    val playerState: StateFlow<PlayerUiState> = player.state

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index, sourcePlaylistId = playlistId)

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isNotEmpty()) player.playQueue(tracks, tracks.indices.random(), shuffle = true, sourcePlaylistId = playlistId)
    }

    fun enqueueNext(track: Track) = player.enqueueNext(track)
    fun enqueueLater(track: Track) = player.enqueueLater(track)

    fun toggleFavorite(track: Track, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(track, favorite) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        perform(text.get(dev.pyte.R.string.playlist_removed)) {
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
    private val settings: SettingsRepository,
    palette: CoverPaletteRepository,
    private val offline: OfflineStore,
    private val text: dev.pyte.ui.UiStrings,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = player.state
    val mixState = player.mixState
    fun startMix() = player.startMix()
    fun cancelMixLoading() = player.cancelMixLoading()
    fun restoreMixQueue() = player.restoreMixQueue()
    val playlists = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), emptyList())
    val playbackPlaylist = player.state.map { it.playlistOrigin?.playlistId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observePlaylist(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), null)
    private val _playlistSave = MutableStateFlow(PlaylistSaveState())
    val playlistSave = _playlistSave.asStateFlow()
    fun selectForPlaylist(track: Track) {
        if (!_playlistSave.value.saving) _playlistSave.value = PlaylistSaveState(track = track)
    }
    fun dismissPlaylistSave() {
        if (!_playlistSave.value.saving) _playlistSave.value = PlaylistSaveState()
    }
    fun consumePlaylistMessage(expected: PlaylistSaveState = _playlistSave.value) {
        if (_playlistSave.value == expected) _playlistSave.value = expected.copy(message = null, undo = null)
    }
    fun enqueueNext(track: Track) { player.enqueueNext(track); _playlistSave.value = PlaylistSaveState(message = text.get(dev.pyte.R.string.queue_next_added)) }
    fun enqueueLater(track: Track) { player.enqueueLater(track); _playlistSave.value = PlaylistSaveState(message = text.get(dev.pyte.R.string.queue_added)) }
    fun removeFromPlaylist(playlistId: String, track: Track) {
        if (_playlistSave.value.saving) return
        _playlistSave.value = PlaylistSaveState(saving = true)
        viewModelScope.launch {
            try {
                if (!repo.removeFromPlaylist(playlistId, track.videoId)) error("Song is no longer in playlist")
                player.removePlaylistTrack(playlistId, track.videoId)
                _playlistSave.value = PlaylistSaveState(message = text.get(dev.pyte.R.string.song_removed_from_playlist))
                offline.reconcile()
                offline.schedule()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _playlistSave.value = PlaylistSaveState(message = text.get(dev.pyte.R.string.error_action), messageIsError = true) }
        }
    }
    fun undoSave(addition: PlaylistAddition) {
        if (_playlistSave.value.undo != addition) return
        _playlistSave.value = _playlistSave.value.copy(message = null, undo = null)
        val pending = _playlistSave.value
        viewModelScope.launch {
            try {
                val removed = repo.undoPlaylistAddition(addition.playlistId, addition.videoId)
                _playlistSave.compareAndSet(pending, PlaylistSaveState(message = text.get(if (removed) dev.pyte.R.string.undone else dev.pyte.R.string.error_undo), messageIsError = !removed))
                offline.schedule()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _playlistSave.compareAndSet(pending, PlaylistSaveState(message = text.get(dev.pyte.R.string.error_undo), messageIsError = true)) }
        }
    }
    fun saveToPlaylist(id: String? = null, newTitle: String? = null) {
        val selected = _playlistSave.value
        val track = selected.track ?: return
        if (selected.saving) return
        _playlistSave.value = selected.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val targetId = id ?: repo.createPlaylist(requireNotNull(newTitle).trim(), track)
                val added = if (id != null) repo.addToPlaylist(id, track) else true
                val title = playlists.value.firstOrNull { it.id == targetId }?.title ?: newTitle.orEmpty()
                _playlistSave.value = PlaylistSaveState(message = if (added) text.get(dev.pyte.R.string.saved_playlist, title)
                    else text.get(dev.pyte.R.string.already_in_playlist),
                    undo = if (added) PlaylistAddition(targetId, track.videoId) else null)
                try { settings.rememberPlaylist(targetId) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Saving succeeded; recency is optional. */ }
                try { offline.schedule() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { _playlistSave.value = _playlistSave.value.copy(message = text.get(dev.pyte.R.string.song_saved_download_later)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _playlistSave.value = selected.copy(error = text.get(dev.pyte.R.string.error_save_song))
            }
        }
    }
    val appearance = settings.settings.catch { emit(dev.pyte.data.settings.AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), dev.pyte.data.settings.AppSettings())
    val coverColors = combine(player.state.map { if (it.isPreview) it.previewTrack else it.current }.distinctUntilChanged(),
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
                message = if (result == null) text.get(dev.pyte.R.string.lyrics_missing) else null)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            _lyrics.value = LyricsUiState(trackId = track.videoId, message = text.get(dev.pyte.R.string.error_lyrics))
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

data class PlaylistAddition(val playlistId: String, val videoId: String)
data class PlaylistSaveState(val track: Track? = null, val saving: Boolean = false, val error: String? = null, val message: String? = null,
    val undo: PlaylistAddition? = null, val messageIsError: Boolean = false)
