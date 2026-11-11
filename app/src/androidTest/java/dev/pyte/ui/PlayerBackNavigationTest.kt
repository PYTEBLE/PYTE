package dev.pyte.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.pyte.domain.Track
import dev.pyte.ui.components.PlayerMorphState
import dev.pyte.ui.navigation.PlayerBackHandler
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerBackNavigationTest {
    @get:Rule val compose = createComposeRule()

    private class Input : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress() = dispatchOnBackProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = .6f))
        fun cancel() = dispatchOnBackCancelled()
        fun complete() = dispatchOnBackCompleted()
    }

    @Test fun gestureAndButtonShareMorphAndCancelledGestureDoesNotClose() {
        val dispatcher = NavigationEventDispatcher()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val input = Input()
        val morph = PlayerMorphState()
        val song = Track("YGr2G2fc48U", "Song", "Artist", 1000, null)
        var open: () -> Unit = {}
        var close: () -> Unit = {}
        var route: String? = null
        var closes = 0
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                route = entry?.destination?.route
                open = { nav.navigate("player") }
                close = {
                    morph.begin(song, opening = false, animate = true)
                    closes++
                    nav.popBackStack()
                }
                NavHost(nav, startDestination = "home") {
                    composable("home") { Text("Home") }
                    composable("player") { Text("Player") }
                }
                PlayerBackHandler(enabled = route == "player", onClose = close)
            }
        }
        compose.runOnIdle {
            dispatcher.addInput(input)
            morph.rootBounds(Rect(0f, 0f, 400f, 900f))
            morph.heroBounds(song.videoId, Rect(24f, 110f, 376f, 462f))
            open()
        }
        compose.waitForIdle()
        compose.runOnIdle { input.start(); input.progress(); input.cancel() }
        compose.runOnIdle {
            assertEquals("player", route)
            assertEquals(0, closes)
            assertNull(morph.hiddenId)
            input.start(); input.progress(); input.complete()
        }
        compose.runOnIdle {
            assertEquals("home", route)
            assertEquals(1, closes)
            assertEquals(song.videoId, morph.hiddenId)
            morph.cancel()
            open()
        }
        compose.waitForIdle()
        compose.runOnIdle { close() }
        compose.runOnIdle {
            assertEquals("home", route)
            assertEquals(2, closes)
            assertEquals(song.videoId, morph.hiddenId)
            morph.cancel()
            open()
        }
        compose.waitForIdle()
        // Three-button/system Back has no predictive progress events.
        compose.runOnIdle { input.complete() }
        compose.runOnIdle {
            assertEquals("home", route)
            assertEquals(3, closes)
            assertEquals(song.videoId, morph.hiddenId)
            dispatcher.dispose()
        }
    }
}
