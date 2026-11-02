package dev.pyte.ui.theme

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.pyte.data.settings.AppSettings
import dev.pyte.data.settings.EffectLevel

data class LivingUiPolicy(
    val level: EffectLevel = EffectLevel.LIVELY,
    val foreground: Boolean = true,
    val systemAnimations: Boolean = true,
    val powerSaver: Boolean = false,
    val haptics: Boolean = true,
) {
    val animate: Boolean get() = foreground && systemAnimations && !powerSaver
    val ambient: Boolean get() = animate && level != EffectLevel.SUBTLE
    val particles: Boolean get() = animate && level != EffectLevel.SUBTLE
    val strength: Float get() = when (level) {
        EffectLevel.SUBTLE -> .45f
        EffectLevel.LIVELY -> .7f
        EffectLevel.INTENSE -> 1f
    }
    fun duration(ms: Int): Int = if (!animate) 0 else when (level) {
        EffectLevel.SUBTLE -> (ms * .65f).toInt()
        EffectLevel.LIVELY -> ms
        EffectLevel.INTENSE -> (ms * 1.1f).toInt()
    }
}

val LocalLivingUi = staticCompositionLocalOf { LivingUiPolicy() }

@Composable
fun rememberLivingUiPolicy(settings: AppSettings): LivingUiPolicy {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSaver by remember { mutableStateOf(power.isPowerSaveMode) }
    var interactive by remember { mutableStateOf(power.isInteractive) }
    var focused by remember { mutableStateOf(view.hasWindowFocus()) }
    var animations by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context, view) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSaver = power.isPowerSaveMode
                interactive = power.isInteractive
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { animations = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose {
            context.unregisterReceiver(receiver)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    // Refresh after resume too, in case settings changed while no broadcast was delivered.
    LaunchedEffect(lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
            powerSaver = power.isPowerSaveMode; interactive = power.isInteractive
            animations = ValueAnimator.areAnimatorsEnabled(); focused = view.hasWindowFocus()
        }
    }
    return LivingUiPolicy(settings.effectLevel, lifecycle.isAtLeast(Lifecycle.State.STARTED) && focused && interactive,
        animations, powerSaver, settings.hapticFeedback)
}
