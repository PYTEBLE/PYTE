package dev.pyte.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.pyte.playback.EqualizerSettings
import dev.pyte.playback.EqualizerCurves
import dev.pyte.playback.EqPoint
import dev.pyte.playback.SoundProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID
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
    val dynamicCoverTheme: Boolean = true,
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val effectLevel: EffectLevel = EffectLevel.LIVELY,
    val hapticFeedback: Boolean = true,
    val autoRecommendations: Boolean = true,
    val battery: BatterySettings = BatterySettings(),
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    private val silenceKey = booleanPreferencesKey("skip_silence")
    private val historyKey = booleanPreferencesKey("record_history")
    private val limitKey = longPreferencesKey("download_limit_mb")
    private val pauseKey = booleanPreferencesKey("downloads_paused")
    private val coverThemeKey = booleanPreferencesKey("dynamic_cover_theme")
    private val equalizerKey = stringPreferencesKey("equalizer_v1")
    private val effectLevelKey = stringPreferencesKey("effect_level")
    private val hapticKey = booleanPreferencesKey("haptic_feedback")
    private val recommendationsKey = booleanPreferencesKey("auto_recommendations")
    private val batteryKey = stringPreferencesKey("battery_v1")
    private val json = Json { ignoreUnknownKeys = true }

    val settings = store.data.map { values ->
        AppSettings(values[silenceKey] ?: false, values[historyKey] ?: true,
            values[limitKey] ?: 2048, values[pauseKey] ?: false, values[coverThemeKey] ?: true,
            runCatching { json.decodeFromString<EqualizerSettings>(values[equalizerKey] ?: "{}").normalized() }.getOrDefault(EqualizerSettings()),
            EffectLevel.fromStored(values[effectLevelKey]), values[hapticKey] ?: true, values[recommendationsKey] ?: true,
            runCatching { json.decodeFromString<BatterySettings>(values[batteryKey] ?: "{}").normalized() }.getOrDefault(BatterySettings()))
    }

    suspend fun setSkipSilence(enabled: Boolean) { store.edit { it[silenceKey] = enabled } }
    suspend fun setBattery(value: BatterySettings) { store.edit { it[batteryKey] = json.encodeToString(value.normalized()) } }
    suspend fun setRecordHistory(enabled: Boolean) { store.edit { it[historyKey] = enabled } }
    suspend fun setDownloadLimit(mb: Long) { store.edit { it[limitKey] = mb.coerceIn(512, 16384) } }
    suspend fun pauseDownloads(paused: Boolean) { store.edit { it[pauseKey] = paused } }
    suspend fun setDynamicCoverTheme(enabled: Boolean) { store.edit { it[coverThemeKey] = enabled } }
    suspend fun setEffectLevel(level: EffectLevel) { store.edit { it[effectLevelKey] = level.name } }
    suspend fun setHapticFeedback(enabled: Boolean) { store.edit { it[hapticKey] = enabled } }
    suspend fun setAutoRecommendations(enabled: Boolean) { store.edit { it[recommendationsKey] = enabled } }
    private suspend fun editEqualizer(change: (EqualizerSettings) -> EqualizerSettings) {
        store.edit { values ->
            val old = runCatching { json.decodeFromString<EqualizerSettings>(values[equalizerKey] ?: "{}").normalized() }
                .getOrDefault(EqualizerSettings())
            values[equalizerKey] = json.encodeToString(change(old).normalized())
        }
    }
    suspend fun enableEqualizer(enabled: Boolean) = editEqualizer { it.copy(enabled = enabled) }
    suspend fun equalizerLimiter(enabled: Boolean) = editEqualizer { it.copy(limiter = enabled) }
    suspend fun setEqualizerCurve(points: List<EqPoint>) = editEqualizer { it.copy(points = points, selectedId = "custom") }
    suspend fun selectSoundProfile(id: String) = editEqualizer { current ->
        val profile = (EqualizerCurves.presets + current.profiles).firstOrNull { it.id == id }
            ?: error("Profile no longer exists")
        current.copy(selectedId = id, points = profile.points)
    }
    suspend fun saveSoundProfile(name: String, points: List<EqPoint>) = editEqualizer { current ->
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 48) { "Use a name between 1 and 48 characters" }
        val existing = current.profiles.firstOrNull { it.name.equals(title, ignoreCase = true) }
        require(existing != null || current.profiles.size < 12) { "You can save up to 12 profiles" }
        val profile = SoundProfile(existing?.id ?: UUID.randomUUID().toString(), title, EqualizerCurves.normalize(points))
        current.copy(selectedId = profile.id, points = profile.points,
            profiles = current.profiles.filterNot { it.id == profile.id } + profile)
    }
    suspend fun deleteSoundProfile(id: String) = editEqualizer { current ->
        current.copy(selectedId = if (current.selectedId == id) "custom" else current.selectedId,
            profiles = current.profiles.filterNot { it.id == id })
    }
}
