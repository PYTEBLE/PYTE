package dev.pyte.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import android.os.PowerManager
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.pyte.ui.components.MiniPlayer
import dev.pyte.ui.screens.HomeScreen
import dev.pyte.ui.screens.LibraryScreen
import dev.pyte.ui.screens.NowPlayingScreen
import dev.pyte.ui.screens.PlaylistScreen
import dev.pyte.ui.screens.SearchScreen
import dev.pyte.ui.screens.SettingsCategory
import dev.pyte.ui.screens.SettingsScreen
import dev.pyte.ui.screens.RecommendationsSheet
import dev.pyte.ui.components.AddToPlaylistSheet
import dev.pyte.ui.viewmodels.RecommendationsViewModel
import dev.pyte.ui.viewmodels.PlayerViewModel
import dev.pyte.ui.theme.AppCoverTheme
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberLivingUiPolicy
import androidx.compose.runtime.CompositionLocalProvider
import dev.pyte.ui.components.AppBackground
import dev.pyte.ui.components.PlayerMorphState
import dev.pyte.ui.components.LocalPlayerMorph
import dev.pyte.ui.components.PlayerMorphOverlay
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SnackbarResult
import dev.pyte.ui.components.SongActions
import dev.pyte.ui.components.SongActionsSheet
import dev.pyte.ui.components.LocalSongActions
import dev.pyte.ui.components.LocalSongFeedback
import dev.pyte.ui.components.SongFeedback
import dev.pyte.R
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{category}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "now_playing"

    fun playlist(id: String) = "playlist/$id"
    fun settingsPage(category: SettingsCategory) = "settings/${category.route}"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.start, Icons.Default.Home),
    Tab(Routes.SEARCH, R.string.search, Icons.Default.Search),
    Tab(Routes.LIBRARY, R.string.library, Icons.Default.LibraryMusic),
)

@Composable
fun PyteRoot(playerViewModel: PlayerViewModel = hiltViewModel(), recommendationsViewModel: RecommendationsViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val coverColors by playerViewModel.coverColors.collectAsStateWithLifecycle()
    val appearance by playerViewModel.appearance.collectAsStateWithLifecycle()
    val motion = rememberLivingUiPolicy(appearance)
    val report by playerViewModel.diagnosticReport.collectAsStateWithLifecycle()
    val playlistSave by playerViewModel.playlistSave.collectAsStateWithLifecycle()
    val favorites by playerViewModel.favoriteIds.collectAsStateWithLifecycle()
    val globalPlaylists by playerViewModel.playlists.collectAsStateWithLifecycle()
    var actionTrack by remember { mutableStateOf<dev.pyte.domain.Track?>(null) }
    var actionPlaylistId by remember { mutableStateOf<String?>(null) }
    val playbackPlaylist by playerViewModel.playbackPlaylist.collectAsStateWithLifecycle()
    val currentPlaybackPlaylist by rememberUpdatedState(playbackPlaylist)
    val currentOriginId by rememberUpdatedState(playerState.playlistOrigin?.playlistId)
    val songActions = remember(playerViewModel) {
        SongActions({ song ->
            actionPlaylistId = currentPlaybackPlaylist?.takeIf { playlist ->
                playlist.first.id == currentOriginId && playlist.second.any { it.videoId == song.videoId }
            }?.first?.id
            actionTrack = song
        }, playerViewModel::selectForPlaylist, { song, id -> actionPlaylistId = id; actionTrack = song })
    }
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onNowPlaying = route == Routes.NOW_PLAYING
    val snackbar = remember { SnackbarHostState() }
    val morph = remember { PlayerMorphState() }
    val discovery by recommendationsViewModel.state.collectAsStateWithLifecycle()
    val discoveryPlaylists by recommendationsViewModel.playlists.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(lifecycle, windowFocused, report, morph.hiddenId, playlistSave.track, actionTrack) {
        // UI eligibility is separate from the decorative animation policy.
        val foreground = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && windowFocused &&
            context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        recommendationsViewModel.visibility(foreground, report == null && morph.hiddenId == null && playlistSave.track == null && actionTrack == null)
    }
    val openPlayer = {
        if (nav.currentDestination?.route != Routes.NOW_PLAYING) {
            morph.begin(playerState.current, opening = true, animate = motion.animate)
            nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
        }
    }
    val closePlayer = {
        if (nav.currentDestination?.route == Routes.NOW_PLAYING) {
            morph.begin(playerState.current, opening = false, animate = motion.animate)
            nav.popBackStack()
        }
        Unit
    }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { snackbar.showSnackbar(it) }
    }
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(playlistSave.message, playlistSave.undo, undoLabel) {
        val event = playlistSave
        event.message?.let {
            val result = snackbar.showSnackbar(it, actionLabel = event.undo?.let { undoLabel }, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) event.undo?.let(playerViewModel::undoSave)
            playerViewModel.consumePlaylistMessage(event)
        }
    }

    val feedback = playlistSave.message?.let { message -> SongFeedback(message,
        playlistSave.undo?.let { addition -> { playerViewModel.undoSave(addition) } }, playlistSave.messageIsError) }
    CompositionLocalProvider(LocalLivingUi provides motion, LocalPlayerMorph provides morph, LocalSongActions provides songActions,
        LocalSongFeedback provides feedback) {
    AppCoverTheme(coverColors) {
    AppBackground(playerState.isPlaying, onNowPlaying) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { morph.rootBounds(it.boundsInWindow()) }) {
    report?.let { details ->
        AlertDialog(
            onDismissRequest = playerViewModel::dismissDiagnostics,
            title = { Text("Wiedergabe-Diagnose") },
            text = { SelectionContainer { Text(details) } },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("PYTE Diagnose", details))
                }) { Text("Kopieren") }
            },
            dismissButton = {
                TextButton(onClick = playerViewModel::dismissDiagnostics) { Text("Schließen") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!onNowPlaying) {
                Column {
                    val current = playerState.current
                    if (current != null) {
                        MiniPlayer(
                            title = current.title,
                            artist = current.artist,
                            artworkUrl = current.thumbnailUrl,
                            isPlaying = playerState.isPlaying,
                            progress = if (playerState.durationMs > 0) {
                                playerState.positionMs.toFloat() / playerState.durationMs
                            } else {
                                0f
                            },
                            onOpen = openPlayer,
                            onToggle = playerViewModel::togglePlayPause,
                             onNext = playerViewModel::next,
                             onSave = { playerViewModel.selectForPlaylist(current) },
                             onPrevious = playerViewModel::previous,
                            videoId = current.videoId,
                        )
                    }
                    NavigationBar(modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(28.dp)), windowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f)) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route || (route == Routes.PLAYLIST && tab.route == Routes.LIBRARY)
                            val scale by animateFloatAsState(if (selected) 1.12f else 1f, tween(motion.duration(240)), label = "tabScale")
                            NavigationBarItem(
                                selected = selected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                onClick = {
                                    if (route?.startsWith(Routes.SETTINGS) == true) nav.popBackStack(Routes.SETTINGS, inclusive = true)
                                    nav.navigate(tab.route) {
                                        popUpTo(Routes.HOME)
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) },
                                label = { Text(stringResource(tab.label), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(motion.duration(260))) + slideInHorizontally(tween(motion.duration(300))) { if (motion.animate) it / 12 else 0 } },
            exitTransition = { fadeOut(tween(motion.duration(180))) + slideOutHorizontally(tween(motion.duration(220))) { if (motion.animate) -it / 16 else 0 } },
            popEnterTransition = { fadeIn(tween(motion.duration(260))) + slideInHorizontally(tween(motion.duration(300))) { if (motion.animate) -it / 12 else 0 } },
            popExitTransition = { fadeOut(tween(motion.duration(180))) + slideOutHorizontally(tween(motion.duration(220))) { if (motion.animate) it / 16 else 0 } },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                    contentPadding = padding,
                    onSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onOpenLibrary = { nav.navigate(Routes.LIBRARY) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(contentPadding = padding,
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    contentPadding = padding,
                    onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                    onSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(contentPadding = padding, category = null,
                    onOpen = { nav.navigate(Routes.settingsPage(it)) }, onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.SETTINGS_PAGE,
                arguments = listOf(navArgument("category") { type = NavType.StringType }),
            ) { entry ->
                SettingsScreen(contentPadding = padding, category = SettingsCategory.fromRoute(entry.arguments?.getString("category")),
                    onOpen = {}, onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                PlaylistScreen(
                    onBack = { nav.popBackStack() },
                    contentPadding = padding,
                    onDiscover = { id, title -> recommendationsViewModel.openPlaylist(id, title) },
                    onSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                )
            }
            composable(
                route = Routes.NOW_PLAYING,
                enterTransition = { fadeIn(tween(motion.duration(380))) },
                exitTransition = { fadeOut(tween(motion.duration(300))) },
                popEnterTransition = { fadeIn(tween(motion.duration(380))) },
                popExitTransition = { fadeOut(tween(motion.duration(300))) },
            ) {
                NowPlayingScreen(onClose = closePlayer, viewModel = playerViewModel, onDiscover = recommendationsViewModel::openSong)
            }
        }
    }
    PlayerMorphOverlay(morph, playerState.current?.videoId)
    RecommendationsSheet(discovery, playerState, recommendationsViewModel::dismiss,
        recommendationsViewModel::retry, recommendationsViewModel::listen,
        recommendationsViewModel::returnToPlayback, playerViewModel::selectForPlaylist)
    actionTrack?.let { song ->
        SongActionsSheet(song, song.videoId in favorites, { actionTrack = null },
            onSave = { actionTrack = null; playerViewModel.selectForPlaylist(song) },
            onNext = { actionTrack = null; playerViewModel.enqueueNext(song) },
            onQueue = { actionTrack = null; playerViewModel.enqueueLater(song) },
            onFavorite = { actionTrack = null; playerViewModel.toggleFavorite(song, song.videoId !in favorites) },
            onDiscover = { actionTrack = null; recommendationsViewModel.openSong(song) },
            onRemoveFromPlaylist = actionPlaylistId?.let { id -> {
                actionTrack = null
                playerViewModel.removeFromPlaylist(id, song)
            } })
    }
    playlistSave.track?.let { song ->
        AddToPlaylistSheet(song, globalPlaylists, playlistSave.saving, playlistSave.error, playerViewModel::dismissPlaylistSave,
            onChoose = { playerViewModel.saveToPlaylist(id = it) }, onCreate = { playerViewModel.saveToPlaylist(newTitle = it) },
            recentId = appearance.recentPlaylistId)
    }
    discovery.picker?.let { song ->
        AddToPlaylistSheet(song, discoveryPlaylists, discovery.savingId != null, discovery.saveError,
            recommendationsViewModel::dismissPicker, recommendationsViewModel::choosePlaylist,
            recommendationsViewModel::createPlaylist)
    }
    PlayerBackHandler(enabled = onNowPlaying, onClose = closePlayer)
    }
    }
    }
    }
}
