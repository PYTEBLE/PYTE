package dev.pyte.ui.components

import androidx.compose.ui.geometry.Rect
import dev.pyte.domain.Track
import org.junit.Assert.*
import org.junit.Test

class PlayerMorphTest {
    private val song = Track("dQw4w9WgXcQ", "Song", "Artist", 1000, null)
    private val root = Rect(0f, 0f, 400f, 900f)
    private val mini = Rect(24f, 740f, 68f, 784f)
    private val hero = Rect(24f, 110f, 376f, 462f)

    @Test fun `open and close use measured endpoints and interrupted motion keeps its current bounds`() {
        val state = PlayerMorphState()
        state.rootBounds(root)
        state.miniBounds(song.videoId, mini)
        state.begin(song, true, true)
        assertEquals(song.videoId, state.hiddenId)
        assertNull(state.endpoint)
        state.heroBounds(song.videoId, hero)
        assertEquals(hero, state.endpoint)
        state.destination = hero
        state.fraction = .4f
        val intermediate = state.currentRect
        state.begin(song, false, true)
        assertEquals(intermediate, state.request!!.from)
        state.miniBounds(song.videoId, mini)
        assertEquals(mini, state.endpoint)
        state.cancel()
        assertNull(state.hiddenId)
    }
    @Test fun `missing offscreen mismatched and reduced motion anchors fall back safely`() {
        val state = PlayerMorphState()
        state.rootBounds(root)
        state.begin(song, true, true)
        assertNull(state.request)
        state.miniBounds("other", mini)
        state.begin(song, true, true)
        assertNull(state.request)
        state.miniBounds(song.videoId, mini)
        state.begin(song, true, false)
        assertNull(state.request)
        state.heroBounds(song.videoId, Rect(0f, -100f, 300f, 200f))
        state.begin(song, false, true)
        assertNull(state.request)
    }
    @Test fun `window resizing clears stale geometry`() {
        val state = PlayerMorphState()
        state.rootBounds(root)
        state.miniBounds(song.videoId, mini)
        state.begin(song, true, true)
        state.rootBounds(Rect(0f, 0f, 900f, 400f))
        assertNull(state.request)
    }
    @Test fun `interpolation is reversible and does not overshoot`() {
        assertEquals(mini, interpolateBounds(mini, hero, -1f))
        assertEquals(hero, interpolateBounds(mini, hero, 2f))
        assertEquals(interpolateBounds(hero, mini, .5f), interpolateBounds(mini, hero, .5f))
        assertFalse(usable(Rect(Float.NaN, 0f, 100f, 100f), root))
    }
}
