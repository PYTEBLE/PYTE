package dev.pyte.data.remote

import org.junit.Assert.*
import org.junit.Test

class ArtworkUrlsTest {
    @Test fun `music images request actual high resolution`() {
        assertEquals("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj",
            ArtworkUrls.sized("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"))
        assertEquals("https://yt3.ggpht.com/abc=s600-c-k",
            ArtworkUrls.sized("https://yt3.ggpht.com/abc=s88-c-k", 600))
    }
    @Test fun `unknown signed urls remain unchanged`() {
        val url = "https://images.example/abc=w60-h60?signature=abc"
        assertEquals(url, ArtworkUrls.sized(url))
        assertEquals(url, ArtworkUrls.sized(url.replace("images.example", "evilggpht.com"))?.replace("evilggpht.com", "images.example"))
        assertNull(ArtworkUrls.sized(null))
    }
    @Test fun `video artwork upgrades only for large surfaces`() {
        val url = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals(url, ArtworkUrls.sized(url, 96))
        assertEquals(url.replace("hqdefault", "maxresdefault"), ArtworkUrls.sized(url, 1200))
    }
}
