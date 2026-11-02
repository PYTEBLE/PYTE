package dev.pyte.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val skipSilence: Boolean = false,
    val recordHistory: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    private val silenceKey = booleanPreferencesKey("skip_silence")
    private val historyKey = booleanPreferencesKey("record_history")

    val settings = store.data.map { values ->
        AppSettings(values[silenceKey] ?: false, values[historyKey] ?: true)
    }

    suspend fun setSkipSilence(enabled: Boolean) { store.edit { it[silenceKey] = enabled } }
    suspend fun setRecordHistory(enabled: Boolean) { store.edit { it[historyKey] = enabled } }
}
