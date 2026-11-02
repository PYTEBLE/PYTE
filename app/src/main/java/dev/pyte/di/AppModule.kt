package dev.pyte.di

import android.content.Context
import androidx.room.Room
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pyte.data.local.FavoriteDao
import dev.pyte.data.local.PlaylistDao
import dev.pyte.data.local.PyteDatabase
import dev.pyte.data.local.TrackDao
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val STREAM_CACHE_BYTES = 1_500L * 1024 * 1024

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PyteDatabase =
        Room.databaseBuilder(context, PyteDatabase::class.java, "pyte.db").build()

    @Provides
    fun providePlaylistDao(db: PyteDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideFavoriteDao(db: PyteDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideTrackDao(db: PyteDatabase): TrackDao = db.trackDao()

    /** One cache per process: Media3 forbids two SimpleCache instances on the same directory. */
    @Provides
    @Singleton
    fun provideStreamCache(@ApplicationContext context: Context): SimpleCache = SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(STREAM_CACHE_BYTES),
        StandaloneDatabaseProvider(context),
    )
}
