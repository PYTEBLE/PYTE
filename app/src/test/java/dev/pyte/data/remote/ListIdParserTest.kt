package dev.pyte.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListIdParserTest {

    private fun valid(input: String) = (ListIdParser.parse(input) as? ListIdResult.Valid)?.listId

    private fun invalidReason(input: String) = (ListIdParser.parse(input) as? ListIdResult.Invalid)?.reason

    @Test
    fun `music youtube playlist link`() {
        assertEquals("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", valid("https://music.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))
    }

    @Test
    fun `youtube playlist link with extra params`() {
        assertEquals("PL123abc", valid("https://www.youtube.com/playlist?si=xyz&list=PL123abc"))
    }

    @Test
    fun `watch link that carries a list`() {
        assertEquals("PL123abc", valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123abc&index=2"))
    }

    @Test
    fun `short youtu be link with list`() {
        assertEquals("OLAbc-_9", valid("https://youtu.be/dQw4w9WgXcQ?list=OLAbc-_9"))
    }

    @Test
    fun `bare playlist id is accepted`() {
        assertEquals("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", valid("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("PL123abc", valid("  https://music.youtube.com/playlist?list=PL123abc \n"))
    }

    @Test
    fun `radio mixes are rejected`() {
        assertTrue(invalidReason("https://music.youtube.com/playlist?list=RDdQw4w9WgXcQ")!!.contains("Radio"))
    }

    @Test
    fun `liked music is rejected because it needs an account`() {
        assertTrue(invalidReason("https://music.youtube.com/playlist?list=LM")!!.contains("account"))
    }

    @Test
    fun `non youtube host is rejected`() {
        assertTrue(invalidReason("https://evil.example.com/playlist?list=PL123abc")!!.contains("YouTube"))
    }

    @Test
    fun `plain text is rejected`() {
        assertTrue(invalidReason("hello world")!!.contains("playlist link"))
    }

    @Test
    fun `empty input is rejected`() {
        assertTrue(invalidReason("   ")!!.isNotBlank())
    }
}
