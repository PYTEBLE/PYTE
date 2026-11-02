package dev.pyte.data.local

import android.database.sqlite.SQLiteDatabase
import dev.pyte.data.remote.InnerTubeClient
import dev.pyte.data.remote.ImportedPlaylist
import dev.pyte.data.remote.PlaylistImporter
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val song = Track("YGr2G2fc48U", "schönhauser", "Gustav", 155_000, null)
    private fun repo(db: PyteDatabase) = LibraryRepository(db,
        PlaylistImporter(InnerTubeClient(OkHttpClient(), Json)), dev.pyte.data.settings.SettingsRepository(context))
    private fun playlist(vararg songs: Track, title: String = "Source title") =
        ImportedPlaylist("PLtest", title, "Owner", null, songs.toList())

    private fun databaseTest(block: suspend (PyteDatabase) -> Unit) = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "test-${UUID.randomUUID()}.db"
            val db = PyteDatabase.open(context, name)
            try { block(db) } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test
    fun `refresh preserves renamed title favorites import date and duplicate positions`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        val importedAt = db.playlistDao().getPlaylist("PLtest")!!.importedAt
        library.renamePlaylist("PLtest", " My name ")
        library.setFavorite(song, true)
        library.saveImported(playlist(song, song, title = "Changed upstream"))
        val detail = library.observePlaylist("PLtest").first()!!
        assertEquals("My name", detail.first.title)
        assertEquals(importedAt, detail.first.importedAt)
        assertEquals(2, detail.second.size)
        assertTrue(song.videoId in library.observeFavoriteIds().first())
    }

    @Test
    fun `search uses safe token prefixes handles accents and updates index`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        assertEquals(song.videoId, library.search("schon gust").first().single().videoId)
        assertTrue(library.search("\"* :()").first().isEmpty())
        library.saveImported(playlist(song.copy(title = "New title")))
        assertTrue(library.search("schönhauser").first().isEmpty())
        assertEquals(song.videoId, library.search("new").first().single().videoId)
    }

    @Test
    fun `deleting playlist retains favorites and history but hides orphaned tracks from search`() = databaseTest { db ->
        val library = repo(db)
        val orphan = song.copy(videoId = "dQw4w9WgXcQ", title = "Orphan")
        library.saveImported(playlist(song, orphan))
        library.setFavorite(song, true)
        library.recordPlayed(song)
        library.deletePlaylist("PLtest")
        assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
        assertEquals(song.videoId, library.observeHistory().first().single().videoId)
        assertTrue(library.search("orphan").first().isEmpty())
    }

    @Test
    fun `disabled history does not record and clearing preserves library and favorites`() = databaseTest { db ->
        val library = repo(db)
        library.setHistoryEnabled(true)
        try {
            library.saveImported(playlist(song))
            library.setFavorite(song, true)
            library.recordPlayed(song)
            assertEquals(1, library.observeHistory().first().size)
            library.setHistoryEnabled(false)
            library.recordPlayed(song.copy(videoId = "newSongId01"))
            assertEquals(1, library.observeHistory().first().size)
            library.clearHistory()
            assertTrue(library.observeHistory().first().isEmpty())
            assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
            assertEquals(song.videoId, library.observePlaylist("PLtest").first()!!.second.single().videoId)
            library.setHistoryEnabled(true)
            library.recordPlayed(song)
            assertEquals(1, library.observeHistory().first().size)
        } finally {
            library.setHistoryEnabled(true)
        }
    }

    @Test
    fun `empty refresh leaves saved playlist intact`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        try { library.saveImported(playlist()); fail("Expected rejection") } catch (_: Exception) { }
        assertEquals(song.videoId, library.observePlaylist("PLtest").first()!!.second.single().videoId)
    }

    @Test
    fun `version one data migrates without losing playlists favorites or order`() = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "migration-${UUID.randomUUID()}.db"
            val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("CREATE TABLE playlists(id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, owner TEXT, thumbnailUrl TEXT, sourceUrl TEXT NOT NULL, trackCount INTEGER NOT NULL, importedAt INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE tracks(videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, durationMs INTEGER NOT NULL, thumbnailUrl TEXT)")
                old.execSQL("CREATE TABLE playlist_tracks(playlistId TEXT NOT NULL, position INTEGER NOT NULL, videoId TEXT NOT NULL, PRIMARY KEY(playlistId, position))")
                old.execSQL("CREATE INDEX index_playlist_tracks_videoId ON playlist_tracks(videoId)")
                old.execSQL("CREATE TABLE favorites(videoId TEXT NOT NULL PRIMARY KEY, addedAt INTEGER NOT NULL)")
                old.execSQL("INSERT INTO playlists VALUES('PLtest', 'Old library', NULL, NULL, 'https://music.youtube.com/playlist?list=PLtest', 1, 100)")
                old.execSQL("INSERT INTO tracks VALUES('YGr2G2fc48U', 'schönhauser', 'Gustav', 155000, NULL)")
                old.execSQL("INSERT INTO playlist_tracks VALUES('PLtest', 0, 'YGr2G2fc48U')")
                old.execSQL("INSERT INTO favorites VALUES('YGr2G2fc48U', 200)")
                old.version = 1
            }
            val db = PyteDatabase.open(context, name)
            try {
                val library = repo(db)
                assertEquals("Old library", library.observePlaylist("PLtest").first()!!.first.title)
                assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
                assertEquals(song.videoId, library.search("schon").first().single().videoId)
                library.saveImported(playlist(song.copy(title = "Updated")))
                assertEquals(song.videoId, library.search("updated").first().single().videoId)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
}
