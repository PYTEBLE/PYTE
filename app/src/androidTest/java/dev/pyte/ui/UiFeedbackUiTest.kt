package dev.pyte.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pyte.domain.Track
import dev.pyte.playback.EqPoint
import dev.pyte.ui.components.*
import dev.pyte.ui.screens.SeekBar
import dev.pyte.ui.screens.SettingsSwitch
import dev.pyte.ui.theme.LivingUiPolicy
import dev.pyte.ui.theme.PyteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiFeedbackUiTest {
    @get:Rule val compose = createComposeRule()
    private val sounds = mutableListOf<UiSound>()
    private val haptics = mutableListOf<UiHaptic>()
    private val feedback = UiFeedback({ LivingUiPolicy(sounds = true) }, sounds::add, haptics::add)

    @Composable private fun Content(content: @Composable () -> Unit) {
        PyteTheme { CompositionLocalProvider(LocalUiFeedback provides feedback) { content() } }
    }

    @Test fun nestedSongButtonsAndForwardedCallbacksEmitExactlyOnce() {
        var plays = 0
        var next = 0
        val song = Track("feedback", "Feedback Song", "Artist", 100000, null)
        compose.setContent { Content {
            Column {
                TrackRow(song, false, false, feedbackClick { plays++ }, {}, onPlayNext = { next++ })
                TextButton(onClick = feedbackClick {}) { Text("Dialogaktion") }
            }
        } }
        compose.onNodeWithText(song.title).performClick()
        compose.runOnIdle { assertEquals(1, plays); assertEquals(1, sounds.size); assertEquals(1, haptics.size) }
        compose.onNodeWithContentDescription("Aktionen für „Feedback Song“").performClick()
        compose.onNodeWithText("Als Nächstes abspielen").performClick()
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, plays); assertEquals(3, sounds.size) }
        compose.onNodeWithText("Dialogaktion").performClick()
        compose.runOnIdle { assertEquals(4, sounds.size) }
    }

    @Test fun disabledAndCancelledIconPressesEmitNothing() {
        var actions = 0
        compose.setContent { Content {
            Column {
                TactileIconButton({ actions++ }, enabled = false) { Text("Gesperrt") }
                TactileIconButton({ actions++ }) { Text("Abbrechen") }
            }
        } }
        compose.onNodeWithText("Gesperrt").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Abbrechen").performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(0, actions); assertTrue(sounds.isEmpty()); assertTrue(haptics.isEmpty()) }
    }

    @Test fun settingsSwitchEmitsOneMechanicalTogglePerChange() {
        compose.setContent { Content {
            var enabled by remember { mutableStateOf(false) }
            SettingsSwitch("Testschalter", "", enabled, true) { enabled = it }
        } }
        compose.onNodeWithText("Testschalter").performClick()
        compose.onNodeWithText("Testschalter").performClick()
        compose.runOnIdle { assertEquals(listOf(UiSound.ON, UiSound.OFF), sounds); assertEquals(2, haptics.size) }
    }

    @Test fun ownDialogKeepsFeedbackEvenWhenRootWindowLosesFocus() {
        compose.setContent { PyteTheme {
            val live = rememberUiFeedback(LivingUiPolicy(sounds = true, haptics = false, foreground = false), sounds::add)
            var open by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalUiFeedback provides live) {
                TextButton(onClick = feedbackClick { open = true }) { Text("Öffnen") }
                if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Dialog") },
                    confirmButton = { TextButton(onClick = feedbackClick { open = false }) { Text("Bestätigen") } })
            }
        } }
        compose.onNodeWithText("Öffnen").performClick()
        compose.onNodeWithText("Bestätigen").performClick()
        compose.runOnIdle { assertEquals(listOf(UiSound.TAP, UiSound.TAP), sounds) }
    }

    @Test fun playbackUpdatesAreSilentButSeekingTicksAndCommits() {
        val position = mutableStateOf(10000L)
        var seeks = 0
        compose.setContent { Content { SeekBar(position.value, 100000) { seeks++ } } }
        compose.runOnIdle { position.value = 30000L }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(sounds.isEmpty()); assertTrue(haptics.isEmpty()) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performTouchInput {
            swipe(Offset(width * .3f, centerY), Offset(width * .8f, centerY), 500)
        }
        compose.runOnIdle {
            assertEquals(1, seeks)
            assertTrue(sounds.isNotEmpty())
            assertTrue(sounds.all { it == UiSound.TICK || it == UiSound.DETENT })
            assertEquals(sounds.size, haptics.size)
        }
    }

    @Test fun equalizerAccessibilityAdjustmentsTickButExternalUpdatesDoNot() {
        val point = mutableStateOf(EqPoint(1000, -100))
        var commits = 0
        compose.setContent { Content {
            BandFader(point.value, true, { point.value = point.value.copy(gainMb = it) }, { commits++ })
        } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle {
            assertEquals(1, commits)
            assertEquals(listOf(UiSound.DETENT), sounds)
            point.value = point.value.copy(gainMb = -600)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, sounds.size) }
    }

    @Test fun unknownDurationCannotSeekOrEmitFeedback() {
        var seeks = 0
        compose.setContent { Content { SeekBar(0, 0) { seeks++ } } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsNotEnabled().performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, seeks); assertTrue(sounds.isEmpty()); assertTrue(haptics.isEmpty()) }
    }

    @Test fun draggingEqualizerFaderTicksAndCommitsOnlyOnce() {
        val point = mutableStateOf(EqPoint(1000, -600))
        var commits = 0
        compose.setContent { Content {
            BandFader(point.value, true, { point.value = point.value.copy(gainMb = it) }, { commits++ })
        } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performTouchInput {
            swipe(Offset(centerX, height * .8f), Offset(centerX, height * .2f), 500)
        }
        compose.runOnIdle {
            assertEquals(1, commits)
            assertTrue(point.value.gainMb > -600)
            assertTrue(sounds.isNotEmpty())
            assertEquals(sounds.size, haptics.size)
        }
    }

    @Test fun cancellingGraphDragRestoresPreviewWithoutCommitting() {
        val original = listOf(EqPoint(1000, 0))
        val points = mutableStateOf(original)
        var commits = 0
        compose.setContent { Content {
            EqualizerGraph(points.value, emptyList(), true, { points.value = it }, { commits++ })
        } }
        compose.onNodeWithContentDescription("Equalizer-Bandkurve. Präzise Regler unter dem Diagramm.").performTouchInput {
            down(Offset(centerX, height * .8f))
            moveTo(Offset(centerX, height * .2f), 200)
            cancel()
        }
        compose.runOnIdle {
            assertEquals(original, points.value)
            assertEquals(0, commits)
            assertTrue(sounds.isNotEmpty())
        }
    }
}
