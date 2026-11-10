package dev.pyte.ui.components

import dev.pyte.domain.PlaylistSummary
import org.junit.Assert.*
import org.junit.Test

class PlaylistChoicesTest {
    private fun playlist(id: String, title: String) = PlaylistSummary(id, title, null, null, 0, "", 0)
    private val items = listOf(playlist("a", "Abend"), playlist("b", "Unterwegs"), playlist("c", "Morgen"))

    @Test fun `recent playlist comes first without bypassing the search filter`() {
        assertEquals(listOf("b", "a", "c"), playlistChoices(items, "", "b").map { it.id })
        assertEquals(listOf("a"), playlistChoices(items, " ABEND ", "b").map { it.id })
        assertTrue(playlistChoices(items, "nicht vorhanden", "b").isEmpty())
    }

    @Test fun `deleted recency is harmless and source list remains untouched`() {
        assertEquals(listOf("a", "c", "b"), playlistChoices(items, "", "deleted").map { it.id })
        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }
}
