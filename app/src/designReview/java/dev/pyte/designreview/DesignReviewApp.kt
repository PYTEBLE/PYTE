package dev.pyte.designreview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pyte.R
import dev.pyte.data.settings.AppSettings
import dev.pyte.ui.theme.ListeningDesign
import dev.pyte.ui.theme.ListeningSpace as Space
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberLivingUiPolicy
import kotlinx.coroutines.launch

/** In-memory interactions only. No PlayerController, Hilt, repositories or network clients. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DesignReviewApp(initialPage: String, initialState: String, initialLargeText: Boolean, initialMotion: Boolean) {
    var page by rememberSaveable { mutableStateOf(if (initialPage == "actions") "player" else initialPage) }
    var sheet by rememberSaveable { mutableStateOf(if (initialPage == "actions") "actions" else "") }
    var screenState by rememberSaveable { mutableStateOf(initialState) }
    var largeText by rememberSaveable { mutableStateOf(initialLargeText) }
    var animations by rememberSaveable { mutableStateOf(initialMotion) }
    var options by rememberSaveable { mutableStateOf(false) }
    var createWithSong by rememberSaveable { mutableStateOf(false) }
    var pickerFromActions by rememberSaveable { mutableStateOf(false) }
    var collectionId by rememberSaveable { mutableStateOf("night") }
    var sourceTitle by rememberSaveable { mutableStateOf("Nachtfahrt") }
    var query by rememberSaveable { mutableStateOf("") }
    var queue by remember { mutableStateOf(demoSongs.map { it.id }) }
    var queueIndex by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var progress by rememberSaveable { mutableFloatStateOf(.31f) }
    var favorites by remember { mutableStateOf(setOf(1)) }
    var playlists by remember { mutableStateOf(demoPlaylists) }
    var saved by remember { mutableStateOf(setOf<String>()) }
    val current = demoSongs[queue[queueIndex.coerceIn(queue.indices)]]
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val density = LocalDensity.current
    val inherited = rememberLivingUiPolicy(AppSettings())
    val policy = inherited.copy(systemAnimations = inherited.systemAnimations && animations)
    fun feedback(id: Int) { scope.launch { snackbar.showSnackbar(resources.getString(id)) } }
    fun pick(song: ReviewSong, destination: String) { selected = song.id; sheet = destination; pickerFromActions = false }
    fun play(song: ReviewSong) {
        val index = queue.indexOf(song.id)
        if (index < 0) { queue = queue + song.id; queueIndex = queue.lastIndex } else queueIndex = index
        playing = true; progress = 0f; page = "player"
    }
    fun advance(delta: Int) { queueIndex = (queueIndex + delta + queue.size) % queue.size; progress = 0f }
    fun saveTo(playlist: ReviewPlaylist) {
        val key = "$selected:${playlist.id}"
        sheet = ""
        if (key in saved) { feedback(R.string.review_already_saved); return }
        saved = saved + key
        playlists = playlists.map { if (it.id == playlist.id) it.copy(count = it.count + 1) else it }
        scope.launch {
            val result = snackbar.showSnackbar(resources.getString(R.string.review_saved, playlist.title),
                actionLabel = resources.getString(R.string.undo), withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed && key in saved) {
                saved = saved - key
                playlists = playlists.map { if (it.id == playlist.id) it.copy(count = (it.count - 1).coerceAtLeast(0)) else it }
            }
        }
    }

    CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else density.fontScale),
        LocalLivingUi provides policy) {
        MaterialTheme(colorScheme = ListeningDesign.colors, typography = ListeningDesign.type) {
            Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        Column(Modifier.statusBarsPadding().padding(horizontal = Space.content)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (page == "player" || page == "collection") ReviewIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { page = "library" })
                                else Image(painterResource(R.drawable.pyte_wordmark), null, Modifier.width(80.dp).height(24.dp))
                                Text(stringResource(R.string.review_badge), Modifier.weight(1f).padding(start = Space.item),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                                ReviewIcon(Icons.Default.Tune, stringResource(R.string.review_options), { options = true }, Modifier.testTag("review-options"))
                            }
                        }
                    },
                    bottomBar = {
                        if (page != "player") Column {
                            ReviewMiniPlayer(current, playing, progress, { page = "player" }, { playing = !playing },
                                { pick(current, "picker") }, { advance(1) })
                            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
                                windowInsets = WindowInsets.navigationBars) {
                                listOf(Triple("start", R.string.review_start, Icons.Default.Home),
                                    Triple("search", R.string.search, Icons.Default.Search),
                                    Triple("library", R.string.library, Icons.Default.LibraryMusic)).forEach { (route, label, icon) ->
                                    NavigationBarItem(selected = page == route || page == "collection" && route == "library", onClick = { page = route; screenState = "normal" },
                                        icon = { Icon(icon, null) }, label = { Text(stringResource(label)) },
                                        modifier = Modifier.testTag("review-nav-$route"))
                                }
                            }
                        }
                    }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        val content = Modifier.widthIn(max = 600.dp).fillMaxSize()
                        when (page) {
                            "player" -> ReviewPlayer(current, sourceTitle, playing, progress, { playing = !playing }, { progress = it },
                                { advance(-1) }, { advance(1) }, { pick(current, "picker") }, { pick(current, "actions") },
                                { sheet = "queue" }, content)
                            "search" -> ReviewSearch(query, { query = it }, demoSongs, { sourceTitle = resources.getString(R.string.review_demo_songs); play(it) },
                                { pick(it, "picker") }, { pick(it, "actions") }, content)
                            "start" -> ReviewStart(current, { page = "player" }, { page = "search" },
                                { pick(it, "picker") }, { pick(it, "actions") }, ::play, content)
                            "collection" -> {
                                val collection = playlists.firstOrNull { it.id == collectionId } ?: playlists.first()
                                ReviewPlaylistScreen(collection, { sourceTitle = collection.title; play(it) },
                                    { pick(it, "picker") }, { pick(it, "actions") }, { page = "search" }, content)
                            }
                            else -> ReviewLibrary(playlists, favorites, screenState, { screenState = "normal" },
                                { page = "search" }, { createWithSong = false; sheet = "new" }, { collectionId = it.id; page = "collection" },
                                ::play, { pick(it, "picker") }, { pick(it, "actions") }, content)
                        }
                    }
                }
                when (sheet) {
                    "actions" -> ReviewActionsSheet(demoSongs[selected], selected in favorites, { sheet = "" }, { pickerFromActions = true; sheet = "picker" },
                        onNext = {
                            queue = queue.toMutableList().apply { add(queueIndex + 1, selected) }
                            sheet = ""; feedback(R.string.review_queue_saved)
                        }, onQueue = { queue = queue + selected; sheet = ""; feedback(R.string.review_queue_saved) },
                        onFavorite = {
                            favorites = if (selected in favorites) favorites - selected else favorites + selected
                            sheet = ""; feedback(R.string.review_favorite_saved)
                        }, onDiscover = { query = ""; page = "search"; sheet = "" })
                    "picker" -> ReviewPicker(demoSongs[selected], playlists, saved.filter { it.startsWith("$selected:") }.map { it.substringAfter(':') }.toSet(),
                        { sheet = "" }, if (pickerFromActions) ({ sheet = "actions" }) else null,
                        ::saveTo, { createWithSong = true; sheet = "new" })
                    "new" -> ReviewNewPlaylist(createWithSong, { sheet = if (createWithSong) "picker" else "" }) { title ->
                        val created = ReviewPlaylist("created-${playlists.size}", title, 0, selected, false)
                        playlists = playlists + created
                        if (createWithSong) saveTo(created) else { sheet = ""; feedback(R.string.review_playlist_created) }
                        screenState = "normal"
                    }
                    "queue" -> ReviewQueue(queue.map { demoSongs[it] }, queueIndex, { sheet = "" }) { index ->
                        queueIndex = index; progress = 0f; sheet = ""
                    }
                }
                if (options) ReviewOptions(screenState, largeText, animations, { screenState = it; page = "library" }, { largeText = it },
                    { animations = it }, { options = false })
                BackHandler(enabled = sheet.isEmpty() && page != "library") { page = "library" }
            }
        }
    }
}

@Composable
private fun ReviewOptions(state: String, large: Boolean, motion: Boolean, onState: (String) -> Unit,
    onLarge: (Boolean) -> Unit, onMotion: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.review_options)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.item)) {
            Text(stringResource(R.string.review_options_help))
            Text(stringResource(R.string.review_library_state), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                listOf("normal" to R.string.review_state_normal, "loading" to R.string.review_state_loading,
                    "empty" to R.string.review_state_empty, "error" to R.string.review_state_error).forEach { (key, label) ->
                    FilterChip(selected = state == key, onClick = { onState(key) }, label = { Text(stringResource(label)) })
                }
            }
            dev.pyte.ui.screens.SettingsSwitch(stringResource(R.string.review_large_text), "", large, true, onLarge)
            dev.pyte.ui.screens.SettingsSwitch(stringResource(R.string.review_motion), "", motion, true, onMotion)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
