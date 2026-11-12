package dev.pyte.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.pyte.playback.EqPoint
import dev.pyte.playback.EqualizerCurves
import dev.pyte.ui.theme.LocalLivingUi
import kotlin.math.*
import dev.pyte.R
import androidx.compose.ui.res.stringResource

internal fun graphGain(y: Float, height: Float): Int =
    (((1 - (y / height.coerceAtLeast(1f)).coerceIn(0f, 1f)) * 1800 - 1200) / 50).roundToInt().times(50).coerceIn(-1200, 600)

/** The line shows editable band gains; the bars underneath are measured PCM, never decorative. */
@Composable
fun EqualizerGraph(points: List<EqPoint>, spectrum: List<Float>, enabled: Boolean,
    onPreview: (List<EqPoint>) -> Unit, onCommit: (List<EqPoint>) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(points)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val tactile by rememberUpdatedState(rememberSliderFeedback(36, setOf(0, 24, 36)))
    var activeBand by remember { mutableStateOf<Int?>(null) }
    val policy = LocalLivingUi.current
    val focus by animateFloatAsState(if (activeBand != null && policy.animate) 1f else 0f,
        tween(policy.duration(110)), label = "eqGraphTouch")
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)
    val graphLabel = stringResource(R.string.eq_graph_accessibility)
    fun x(hz: Int) = (ln(hz / 20.0) / ln(1000.0)).toFloat()
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.eq_band_curve), style = MaterialTheme.typography.labelSmall, color = accent)
            Text(if (spectrum.isEmpty()) stringResource(R.string.eq_spectrum_idle) else "LIVE · PCM", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 12.dp)) {
            Column(Modifier.height(200.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("+6", "0", "−6", "−12").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            Canvas(Modifier.weight(1f).height(200.dp).semantics { contentDescription = graphLabel }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var selected = 0
                    var original = emptyList<EqPoint>()
                    var dragged = emptyList<EqPoint>()
                    fun update(y: Float) {
                        val next = current.mapIndexed { i, p -> if (i == selected) p.copy(gainMb = graphGain(y, size.height.toFloat())) else p }
                        next.getOrNull(selected)?.let { tactile.move((it.gainMb + 1200) / 1800f) }
                        dragged = next
                        preview(next)
                    }
                    detectDragGestures(onDragStart = { down ->
                        original = current
                        selected = current.indices.minByOrNull { abs(x(current[it].hz) * size.width - down.x) } ?: 0
                        activeBand = selected
                        tactile.start((current.getOrNull(selected)?.gainMb?.plus(1200) ?: 1200) / 1800f)
                        update(down.y)
                    }, onDrag = { change, _ -> change.consume(); update(change.position.y) },
                        onDragEnd = { activeBand = null; commit(dragged) },
                        onDragCancel = { activeBand = null; preview(original) })
                }) {
                repeat(4) { line ->
                    val y = size.height * line / 3
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                listOf(25,100,400,1000,4000,16000).forEach { hz ->
                    drawLine(grid, Offset(x(hz) * size.width, 0f), Offset(x(hz) * size.width, size.height), 1.dp.toPx())
                }
                spectrum.forEachIndexed { i, level ->
                    val width = size.width / spectrum.size
                    val height = size.height * level
                    drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = .5f), secondary.copy(alpha = .12f))),
                        Offset(i * width + 1, size.height - height), androidx.compose.ui.geometry.Size((width - 2).coerceAtLeast(1f), height))
                }
                val path = Path()
                points.forEachIndexed { index, p ->
                    val px = x(p.hz) * size.width
                    val py = (600 - p.gainMb) / 1800f * size.height
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, accent.copy(alpha = .14f), style = Stroke(10.dp.toPx()))
                drawPath(path, accent, style = Stroke(2.5.dp.toPx()))
                points.forEachIndexed { index, p ->
                    val center = Offset(x(p.hz) * size.width, (600 - p.gainMb) / 1800f * size.height)
                    if (index == activeBand) drawCircle(accent.copy(alpha = .16f * focus), 18.dp.toPx(), center)
                    drawCircle(accent, (4 + if (index == activeBand) 2 * focus else 0f).dp.toPx(), center)
                    drawCircle(Color.Black.copy(alpha = .65f), 1.5.dp.toPx(), center)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("25 Hz", "100", "400", "1k", "4k", "16k").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun BandFader(point: EqPoint, enabled: Boolean, onPreview: (Int) -> Unit, onCommit: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)
    val current by rememberUpdatedState(point)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val tactile by rememberUpdatedState(rememberSliderFeedback(36, setOf(0, 24, 36)))
    var dragging by remember { mutableStateOf(false) }
    val policy = LocalLivingUi.current
    val focus by animateFloatAsState(if (dragging && policy.animate) 1f else 0f,
        tween(policy.duration(110)), label = "eqFaderTouch")
    fun adjust(value: Int) {
        tactile.start((current.gainMb + 1200) / 1800f)
        tactile.move((value + 1200) / 1800f)
        preview(value)
        commit()
    }
    Column(Modifier.width(64.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(if (point.hz < 1000) "${point.hz}" else "${point.hz / 1000f}k", style = MaterialTheme.typography.labelMedium)
        Text(String.format(java.util.Locale.ROOT, "%+.1f", point.gainMb / 100f), color = accent, style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.width(56.dp).height(160.dp)
            .semantics {
                contentDescription = "Equalizer ${if (point.hz < 1000) "${point.hz} Hz" else "${point.hz / 1000f} kHz"}"
                progressBarRangeInfo = ProgressBarRangeInfo(point.gainMb / 100f, -12f..6f, 35)
                if (!enabled) disabled()
                setProgress { value ->
                    if (enabled) adjust((value * 2).roundToInt().times(50).coerceIn(-1200, 600))
                    enabled
                }
            }.onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown || event.key !in listOf(Key.DirectionUp, Key.DirectionDown)) false
                else { adjust((point.gainMb + if (event.key == Key.DirectionUp) 50 else -50).coerceIn(-1200, 600)); true }
            }.focusable(enabled).pointerInput(enabled) {
                if (enabled) detectTapGestures { adjust(graphGain(it.y, size.height.toFloat())) }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var original = 0
                detectDragGestures(onDragStart = {
                    original = current.gainMb
                    dragging = true
                    tactile.start((original + 1200) / 1800f)
                }, onDrag = { change, _ ->
                    change.consume()
                    val gain = graphGain(change.position.y, size.height.toFloat())
                    tactile.move((gain + 1200) / 1800f)
                    preview(gain)
                }, onDragEnd = { dragging = false; commit() },
                    onDragCancel = { dragging = false; preview(original) })
            }) {
            val center = size.width / 2
            drawLine(track, Offset(center, 8.dp.toPx()), Offset(center, size.height - 8.dp.toPx()), 4.dp.toPx())
            val y = (600 - point.gainMb) / 1800f * size.height
            val zero = size.height / 3
            drawCircle(accent.copy(alpha = .14f * focus), 22.dp.toPx(), Offset(center, y))
            drawLine(accent.copy(alpha = if (enabled) .8f else .25f), Offset(center, zero), Offset(center, y), 4.dp.toPx())
            drawLine(track, Offset(center - 12.dp.toPx(), zero), Offset(center + 12.dp.toPx(), zero), 1.dp.toPx())
            val halfWidth = (13 + 2 * focus).dp.toPx()
            drawRoundRect(if (enabled) accent else track, Offset(center - halfWidth, y.coerceIn(8.dp.toPx(), size.height - 8.dp.toPx()) - 6.dp.toPx()),
                androidx.compose.ui.geometry.Size(halfWidth * 2, 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        }
    }
}
