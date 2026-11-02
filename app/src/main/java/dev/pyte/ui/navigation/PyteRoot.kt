package dev.pyte.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
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
import dev.pyte.ui.screens.FavoritesScreen
import dev.pyte.ui.screens.HomeScreen
import dev.pyte.ui.screens.NowPlayingScreen
import dev.pyte.ui.screens.PlaylistScreen
import dev.pyte.ui.screens.SearchScreen
import dev.pyte.ui.screens.SettingsScreen
import dev.pyte.ui.viewmodels.PlayerViewModel
import dev.pyte.ui.theme.AppCoverTheme
import dev.pyte.ui.theme.LocalLivingUi
import dev.pyte.ui.theme.rememberLivingUiPolicy
import androidx.compose.runtime.CompositionLocalProvider
import dev.pyte.ui.components.AppBackground
import dev.pyte.ui.components.PlayerMorphState
import dev.pyte.ui.components.LocalPlayerMorph
import dev.pyte.ui.components.PlayerMorphOverlay

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "now_playing"

    fun playlist(id: String) = "playlist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Library", Icons.Default.LibraryMusic),
    Tab(Routes.SEARCH, "Search", Icons.Default.Search),
    Tab(Routes.FAVORITES, "Favorites", Icons.Default.Favorite),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun PyteRoot(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val coverColors by playerViewModel.coverColors.collectAsStateWithLifecycle()
    val appearance by playerViewModel.appearance.collectAsStateWithLifecycle()
    val motion = rememberLivingUiPolicy(appearance)
    val report by playerViewModel.diagnosticReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onNowPlaying = route == Routes.NOW_PLAYING
    val snackbar = remember { SnackbarHostState() }
    val morph = remember { PlayerMorphState() }
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

    CompositionLocalProvider(LocalLivingUi provides motion, LocalPlayerMorph provides morph) {
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
                            videoId = current.videoId,
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route || (route == Routes.PLAYLIST && tab.route == Routes.HOME),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
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
            enterTransition = { fadeIn(tween(motion.duration(220))) },
            exitTransition = { fadeOut(tween(motion.duration(180))) },
            popEnterTransition = { fadeIn(tween(motion.duration(220))) },
            popExitTransition = { fadeOut(tween(motion.duration(180))) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
                    contentPadding = padding,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(contentPadding = padding)
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(contentPadding = padding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(contentPadding = padding)
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) {
                PlaylistScreen(
                    onBack = { nav.popBackStack() },
                    contentPadding = padding,
                )
            }
            composable(
                route = Routes.NOW_PLAYING,
                enterTransition = { fadeIn(tween(motion.duration(380))) },
                exitTransition = { fadeOut(tween(motion.duration(300))) },
                popEnterTransition = { fadeIn(tween(motion.duration(380))) },
                popExitTransition = { fadeOut(tween(motion.duration(300))) },
            ) {
                NowPlayingScreen(onClose = closePlayer)
            }
        }
    }
    PlayerMorphOverlay(morph, playerState.current?.videoId)
    BackHandler(enabled = onNowPlaying) { closePlayer() }
    }
    }
    }
    }
}
