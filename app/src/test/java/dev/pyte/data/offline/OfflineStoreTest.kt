package dev.pyte.data.offline

import android.app.Application
import dev.pyte.data.local.*
import dev.pyte.data.lyrics.LyricsRepository
import dev.pyte.data.remote.StreamResolver
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OfflineStoreTest {
    private val id = "dQw4w9WgXcQ"
    private val context get() = RuntimeEnvironment.getApplication()
    private fun file(suffix: String) = File(context.filesDir, "offline/$id.$suffix")

    private fun fixture(block: suspend (PyteDatabase, SettingsRepository) -> Unit) = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "offline-${UUID.randomUUID()}.db"
            val db = PyteDatabase.open(context, name)
            val settings = SettingsRepository(context)
            try {
                settings.pauseDownloads(false)
                settings.setDownloadLimit(2048)
                db.playlistDao().upsertPlaylist(PlaylistEntity("local:test", "Test", null, null, "", 1, 0))
                db.trackDao().upsertTracks(listOf(TrackEntity(id, "Test", "Artist", 10000, null)))
                db.playlistDao().insertPlaylistTracks(listOf(PlaylistTrackEntity("local:test", 0, id)))
                db.offlineDao().select(OfflinePlaylistEntity("local:test"))
                file("part").parentFile!!.mkdirs()
                listOf("part", "audio", "format", "validator", "lyrics", "cover", "covercheck", "cover.tmp").forEach { file(it).delete() }
                block(db, settings)
            } finally {
                listOf("part", "audio", "format", "validator", "lyrics", "cover", "covercheck", "cover.tmp").forEach { file(it).delete() }
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    private fun response(request: Request, body: ResponseBody, code: Int = 200): Response.Builder = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body)

    private fun store(db: PyteDatabase, settings: SettingsRepository, wifi: () -> Boolean = { true },
        audio: (Request) -> Response): OfflineStore {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.host == "lrclib.net" -> response(request, "".toResponseBody(), 404).build()
                request.header("Range") == "bytes=0-0" -> response(request, "x".toResponseBody(), 206).build()
                else -> audio(request)
            }
        }.build()
        val resolver = StreamResolver({ _, _ -> Json.parseToJsonElement("""{
            "playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[
            {"itag":140,"mimeType":"audio/mp4","url":"https://audio.example/test?expire=9999999999"}]}}
        """).jsonObject }, http, { 0 })
        return OfflineStore(context, db, settings, resolver, http, LyricsRepository(context, http, Json), wifi)
    }

    @Test fun `completed audio is durable deduplicated and playable without resolution`() = fixture { db, settings ->
        var calls = 0
        val store = store(db, settings) { request -> calls++; response(request, "audio data".toResponseBody()).build() }
        assertFalse(store.sync())
        assertEquals("audio data", store.audio(id)!!.readText())
        assertFalse(file("part").exists())
        assertEquals("Downloaded", db.offlineDao().observeDownloads().first().single().state)
        val restarted = store(db, settings, { false }) { error("Must not resolve downloaded audio") }
        assertEquals("audio data", restarted.audio(id)!!.readText())
        assertFalse(restarted.sync())
        assertEquals(1, calls)
    }

    @Test fun `non wifi and paused downloads never request audio`() = fixture { db, settings ->
        assertFalse(store(db, settings, { false }) { error("No mobile data downloads") }.sync())
        settings.pauseDownloads(true)
        assertFalse(store(db, settings) { error("Paused") }.sync())
        assertFalse(file("audio").exists())
    }

    @Test fun `partial download resumes with range and validator but is never playable before completion`() = fixture { db, settings ->
        val data = ByteArray(180_000) { (it % 127).toByte() }
        var calls = 0
        val store = store(db, settings) { request ->
            calls++
            if (calls == 1) {
                val source = object : ForwardingSource(Buffer().write(data, 0, 70_000)) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val count = super.read(sink, byteCount)
                        if (count == -1L) throw IOException("Disconnected")
                        return count
                    }
                }.buffer()
                val body = object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength() = data.size.toLong()
                    override fun source() = source
                }
                response(request, body).header("ETag", "\"v1\"").build()
            } else {
                val offset = file("part").length().toInt()
                assertEquals("bytes=$offset-", request.header("Range"))
                assertEquals("\"v1\"", request.header("If-Range"))
                response(request, data.copyOfRange(offset, data.size).toResponseBody(), 206)
                    .header("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}").header("ETag", "\"v1\"").build()
            }
        }
        assertTrue(store.sync())
        assertNull(store.audio(id))
        assertEquals(70_000L, file("part").length())
        assertFalse(store.sync())
        assertArrayEquals(data, store.audio(id)!!.readBytes())
    }

    @Test fun `storage limit pauses before writing and never evicts existing downloads`() = fixture { db, settings ->
        settings.setDownloadLimit(512)
        val store = store(db, settings) { request ->
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = 600L * 1024 * 1024
                override fun source() = Buffer().writeUtf8("not read")
            }
            response(request, body).build()
        }
        assertFalse(store.sync())
        assertNull(store.audio(id))
        assertEquals("Storage limit reached", db.offlineDao().observeDownloads().first().single().state)
    }

    @Test fun `deselecting playlist cleans files without network but retains shared downloads`() = fixture { db, settings ->
        val store = store(db, settings, { false }) { error("No network") }
        file("audio").writeText("download")
        file("part").writeText("partial")
        db.playlistDao().upsertPlaylist(PlaylistEntity("local:other", "Other", null, null, "", 1, 0))
        db.playlistDao().insertPlaylistTracks(listOf(PlaylistTrackEntity("local:other", 0, id)))
        db.offlineDao().select(OfflinePlaylistEntity("local:other"))
        db.offlineDao().deselect("local:test")
        store.reconcile()
        assertNotNull(store.audio(id))
        db.offlineDao().deselect("local:other")
        store.reconcile()
        assertNull(store.audio(id))
        assertFalse(file("part").exists())
    }

    @Test fun `partial range that does not reach end of resource is never marked complete`() = fixture { db, settings ->
        file("part").writeText("0123")
        file("format").writeText("audio-v2:$id:140")
        file("validator").writeText("\"v1\"")
        val store = store(db, settings) { request ->
            response(request, "45".toResponseBody(), 206).header("Content-Range", "bytes 4-5/10").build()
        }
        assertTrue(store.sync())
        assertNull(store.audio(id))
        assertEquals("0123", file("part").readText())
    }

    @Test fun `server replacing ranged resource restarts instead of appending encodings`() = fixture { db, settings ->
        file("part").writeText("old")
        file("format").writeText("audio-v2:$id:140")
        file("validator").writeText("\"v1\"")
        val store = store(db, settings) { request ->
            assertEquals("bytes=3-", request.header("Range"))
            response(request, "replacement".toResponseBody()).header("ETag", "\"v2\"").build()
        }
        assertFalse(store.sync())
        assertEquals("replacement", store.audio(id)!!.readText())
    }

    @Test fun `different format discards partial bytes and missing file resets completed status`() = fixture { db, settings ->
        file("part").writeText("opus bytes")
        file("format").writeText("audio-v2:$id:251")
        file("validator").writeText("\"v1\"")
        db.offlineDao().update(DownloadEntity(id, "Downloaded", 100, 100))
        val store = store(db, settings) { request ->
            assertNull(request.header("Range"))
            response(request, "aac bytes".toResponseBody()).build()
        }
        store.reconcile()
        assertEquals("Waiting for Wi-Fi", db.offlineDao().observeDownloads().first().single().state)
        assertFalse(store.sync())
        assertEquals("aac bytes", store.audio(id)!!.readText())
    }

    @Test fun `small offline cover upgrades without redownloading audio`() = fixture { db, settings ->
        fun image(size: Int): ByteArray {
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            return java.io.ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                output.toByteArray()
            }
        }
        file("audio").writeText("already downloaded")
        file("cover").writeBytes(image(120))
        db.trackDao().upsertTracks(listOf(TrackEntity(id, "Test", "Artist", 10000,
            "https://yt3.googleusercontent.com/cover=w120-h120-l90-rj")))
        var covers = 0
        val store = store(db, settings) { request ->
            assertEquals("yt3.googleusercontent.com", request.url.host)
            assertTrue(request.url.toString().contains("w1200-h1200"))
            covers++
            response(request, image(1200).toResponseBody()).header("Content-Type", "image/png").build()
        }
        assertFalse(store.sync())
        assertEquals(1200, dev.pyte.data.remote.ArtworkQuality.size(file("cover"))!!.shortEdge)
        assertEquals("already downloaded", file("audio").readText())
        assertFalse(store.sync())
        assertEquals(1, covers)
    }
}
