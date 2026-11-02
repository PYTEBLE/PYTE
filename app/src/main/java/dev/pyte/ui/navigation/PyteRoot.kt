package dev.pyte.ui.navigation

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import dev.pyte.ui.viewmodels.PlayerViewModel

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "now_playing"

    fun playlist(id: String) = "playlist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Library", Icons.Default.Home),
    Tab(Routes.SEARCH, "Search", Icons.Default.Search),
    Tab(Routes.FAVORITES, "Favorites", Icons.Default.Favorite),
)

@Composable
fun PyteRoot(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onNowPlaying = route == Routes.NOW_PLAYING
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
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
                            onOpen = { nav.navigate(Routes.NOW_PLAYING) },
                            onToggle = playerViewModel::togglePlayPause,
                            onNext = playerViewModel::next,
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
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
                enterTransition = { slideInVertically(initialOffsetY = { it }) },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) },
                popEnterTransition = { slideInVertically(initialOffsetY = { it }) },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
            ) {
                NowPlayingScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}
