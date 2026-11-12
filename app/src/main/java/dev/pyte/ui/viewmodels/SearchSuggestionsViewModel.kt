package dev.pyte.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.remote.CoverPaletteRepository
import dev.pyte.data.remote.RecommendationsRepository
import dev.pyte.data.remote.SongRecommendation
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.data.settings.SettingsRepository
import dev.pyte.domain.Track
import dev.pyte.ui.theme.CoverColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchSuggestionsState(
    val songs: List<SongRecommendation> = emptyList(),
    val recent: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val partial: Boolean = false,
    val historyEnabled: Boolean = true,
)

/** Recent first, but choose different artists before taking another song by the same artist. */
internal fun recentSuggestionSeeds(history: List<Track>): List<Track> {
    val recent = history.distinctBy { it.videoId }.take(12)
    val diverse = recent.distinctBy { it.artist.trim().lowercase(java.util.Locale.ROOT) }.take(4)
    return (diverse + recent).distinctBy { it.videoId }.take(4)
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchSuggestionsViewModel @Inject constructor(
    library: LibraryRepository,
    settings: SettingsRepository,
    private val recommendations: RecommendationsRepository,
    private val palette: CoverPaletteRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private var cachedKey: List<String>? = null
    private var cachedAt = 0L
    private var cached: SearchSuggestionsState? = null

    val state = combine(library.observeHistory(), settings.settings.map { it.recordHistory }.distinctUntilChanged(), refresh) { history, enabled, retry ->
        Triple(history.take(12), enabled, retry)
    }.distinctUntilChanged().transformLatest { (recent, enabled, _) ->
        if (!enabled || recent.isEmpty()) {
            cached = null
            cachedKey = null
            emit(SearchSuggestionsState(recent = if (enabled) recent else emptyList(), historyEnabled = enabled))
            return@transformLatest
        }
        val key = recent.map { it.videoId }
        cached?.takeIf { key == cachedKey && System.currentTimeMillis() - cachedAt in 0 until 10 * 60_000L }?.let {
            emit(it); return@transformLatest
        }
        emit(SearchSuggestionsState(recent = recent, loading = true))
        try {
            val batch = recommendations.discover(recentSuggestionSeeds(recent), recent.map { it.videoId }.toSet())
            val result = SearchSuggestionsState(songs = batch.songs, recent = recent, partial = batch.partial)
            cached = result
            cachedKey = key
            cachedAt = System.currentTimeMillis()
            emit(result)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emit(SearchSuggestionsState(recent = recent, error = true)) }
    }.catch { emit(SearchSuggestionsState(error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchSuggestionsState(loading = true))

    val featuredColors: StateFlow<CoverColors?> = state.map { it.songs.firstOrNull()?.track }.distinctUntilChanged()
        .transformLatest { track -> emit(track?.let { palette.colors(it) }) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry() { cached = null; refresh.value++ }
}
