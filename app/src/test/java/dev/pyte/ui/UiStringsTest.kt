package dev.pyte.ui

import dev.pyte.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class UiStringsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `core actions remain German even on an English device`() {
        val strings = UiStrings(context)
        assertEquals("Zur Playlist", strings.get(R.string.add_to_playlist))
        assertEquals("Einstellungen", strings.get(R.string.settings))
        assertEquals("Akkumodus", strings.get(R.string.battery))
        assertEquals("Als Nächstes abspielen", strings.get(R.string.play_next))
    }

    @Test fun `German labels support singular plural and user supplied titles`() {
        val resources = context.resources
        assertEquals("1 Song", resources.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("2 Songs", resources.getQuantityString(R.plurals.songs_count, 2, 2))
        assertEquals("In „Regen & Nacht“ gespeichert", context.getString(R.string.saved_playlist, "Regen & Nacht"))
        assertEquals("„Mein Song“ zur Playlist hinzufügen", context.getString(R.string.add_song_to_playlist, "Mein Song"))
    }
}
