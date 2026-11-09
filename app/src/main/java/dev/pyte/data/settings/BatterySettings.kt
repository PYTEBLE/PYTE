package dev.pyte.data.settings

import kotlinx.serialization.Serializable

/** Saved choices are inert unless the master switch is enabled. */
@Serializable
data class BatterySettings(
    val enabled: Boolean = false,
    val lowData: Boolean = true,
    val burstBuffer: Boolean = true,
    val bypassDisabledEq: Boolean = true,
    val reduceEffects: Boolean = true,
    val reduceBackgroundUpdates: Boolean = true,
    val pauseDownloads: Boolean = true,
    val disablePrefetch: Boolean = true,
    val screenOffTimerMinutes: Int = 0,
) {
    fun normalized() = copy(screenOffTimerMinutes = screenOffTimerMinutes.takeIf { it in listOf(0, 15, 30, 60) } ?: 0)
    fun active(choice: Boolean) = enabled && choice
}
