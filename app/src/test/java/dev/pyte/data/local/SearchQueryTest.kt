package dev.pyte.data.local

import org.junit.Assert.*
import org.junit.Test

class SearchQueryTest {
    @Test fun `punctuation cannot become MATCH operators`() {
        assertEquals("\"hello*\" \"world*\"", searchQuery("hello* -world\""))
        assertNull(searchQuery("\"():*"))
    }
    @Test fun `unicode and numbers are preserved`() {
        assertEquals("\"schön*\" \"01099*\"", searchQuery("schön 01099"))
    }
}
