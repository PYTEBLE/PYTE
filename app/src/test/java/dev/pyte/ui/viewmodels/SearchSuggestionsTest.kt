package dev.pyte.ui.viewmodels

import dev.pyte.domain.Track
import org.junit.Assert.*
import org.junit.Test

class SearchSuggestionsTest {
    private fun song(id: String, artist: String) = Track(id, id, artist, 1000, null)

    @Test fun `recent seeds prioritize different artists without discarding recency`() {
        val history = listOf(song("a", "Artist"), song("b", " artist "), song("c", "Other"), song("d", "Third"), song("e", "Fourth"))
        assertEquals(listOf("a", "c", "d", "e"), recentSuggestionSeeds(history).map { it.videoId })
    }

    @Test fun `few artists still produce distinct song seeds and empty history stays empty`() {
        val songs = (0..5).map { song("$it", "Artist") }
        assertEquals(songs.take(4), recentSuggestionSeeds(songs + songs))
        assertTrue(recentSuggestionSeeds(emptyList()).isEmpty())
    }
}
