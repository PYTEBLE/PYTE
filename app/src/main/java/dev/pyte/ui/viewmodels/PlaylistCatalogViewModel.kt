package dev.pyte.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pyte.data.remote.CatalogPlaylist
import dev.pyte.data.remote.PlaylistCatalog
import dev.pyte.data.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogState(val playlists: List<CatalogPlaylist> = emptyList(), val loading: Boolean = false,
    val error: Boolean = false, val partial: Boolean = false)
data class CatalogImportState(val selected: CatalogPlaylist? = null, val importing: Boolean = false, val error: Boolean = false)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistCatalogViewModel @Inject constructor(
    private val catalog: PlaylistCatalog,
    private val library: LibraryRepository,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val state = refresh.transformLatest { retry ->
        emit(CatalogState(loading = true))
        try {
            val batch = catalog.load(refresh = retry > 0)
            emit(CatalogState(playlists = batch.playlists, partial = batch.partial))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emit(CatalogState(error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogState(loading = true))
    private val _import = MutableStateFlow(CatalogImportState())
    val import = _import.asStateFlow()

    fun retry() { refresh.value++ }
    fun select(playlist: CatalogPlaylist) { if (!_import.value.importing) _import.value = CatalogImportState(selected = playlist) }
    fun dismiss() { if (!_import.value.importing) _import.value = CatalogImportState() }
    fun confirm(onImported: (String) -> Unit) {
        val playlist = _import.value.selected ?: return
        if (_import.value.importing) return
        _import.value = _import.value.copy(importing = true, error = false)
        viewModelScope.launch {
            try {
                // Already-imported cards open directly; never refresh/overwrite on a second tap.
                val existing = library.observePlaylist(playlist.id).first()
                val id = if (existing != null) playlist.id else library.importPlaylist(playlist.url)
                _import.value = CatalogImportState()
                onImported(id)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _import.value = CatalogImportState(selected = playlist, error = true) }
        }
    }
}
