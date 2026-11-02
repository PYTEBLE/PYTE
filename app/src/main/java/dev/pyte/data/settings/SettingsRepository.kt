package dev.pyte.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val skipSilence: Boolean = false,
    val recordHistory: Boolean = true,
    val downloadLimitMb: Long = 2048,
    val downloadsPaused: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    private val silenceKey = booleanPreferencesKey("skip_silence")
    private val historyKey = booleanPreferencesKey("record_history")
    private val limitKey = longPreferencesKey("download_limit_mb")
    private val pauseKey = booleanPreferencesKey("downloads_paused")

    val settings = store.data.map { values ->
        AppSettings(values[silenceKey] ?: false, values[historyKey] ?: true,
            values[limitKey] ?: 2048, values[pauseKey] ?: false)
    }

    suspend fun setSkipSilence(enabled: Boolean) { store.edit { it[silenceKey] = enabled } }
    suspend fun setRecordHistory(enabled: Boolean) { store.edit { it[historyKey] = enabled } }
    suspend fun setDownloadLimit(mb: Long) { store.edit { it[limitKey] = mb.coerceIn(512, 16384) } }
    suspend fun pauseDownloads(paused: Boolean) { store.edit { it[pauseKey] = paused } }
}
