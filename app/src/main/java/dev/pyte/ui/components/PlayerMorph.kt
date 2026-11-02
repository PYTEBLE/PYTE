package dev.pyte.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pyte.domain.Track
import dev.pyte.ui.theme.LocalLivingUi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

internal data class MorphRequest(val serial: Int, val track: Track, val opening: Boolean, val from: Rect)

@Stable
class PlayerMorphState {
    var root by mutableStateOf(Rect.Zero)
        private set
    private var mini by mutableStateOf<Pair<String, Rect>?>(null)
    private var hero by mutableStateOf<Pair<String, Rect>?>(null)
    internal var request by mutableStateOf<MorphRequest?>(null)
        private set
    internal var destination by mutableStateOf<Rect?>(null)
    internal var fraction by mutableFloatStateOf(0f)
    private var serial = 0
    val hiddenId: String? get() = request?.track?.videoId
    internal val endpoint: Rect? get() = request?.let { r ->
        (if (r.opening) hero else mini)?.takeIf { it.first == r.track.videoId && usable(it.second, root) }?.second
    }
    internal val currentRect: Rect? get() = request?.let { interpolateBounds(it.from, destination ?: it.from, fraction) }
    fun rootBounds(rect: Rect) {
        if (root != Rect.Zero && (root.width != rect.width || root.height != rect.height)) cancel()
        root = rect
    }
    fun miniBounds(id: String, rect: Rect) { mini = id to rect }
    fun heroBounds(id: String, rect: Rect) { hero = id to rect }
    fun clearHero(id: String) { if (hero?.first == id) hero = null }
    fun begin(track: Track?, opening: Boolean, animate: Boolean) {
        val source = currentRect?.takeIf { request?.track?.videoId == track?.videoId }
            ?: (if (opening) mini else hero)?.takeIf { it.first == track?.videoId }?.second
        if (!animate || track == null || source == null || !usable(source, root)) { cancel(); return }
        request = MorphRequest(++serial, track, opening, source)
        destination = null
        fraction = 0f
        if (opening) hero = null else mini = null
    }
    fun cancel() { request = null; destination = null; fraction = 0f }
}

internal fun usable(rect: Rect, root: Rect): Boolean =
    listOf(rect.left, rect.top, rect.right, rect.bottom).all { it.isFinite() } &&
        rect.width > 1 && rect.height > 1 && rect.left >= root.left - 1 && rect.top >= root.top - 1 &&
        rect.right <= root.right + 1 && rect.bottom <= root.bottom + 1

internal fun interpolateBounds(from: Rect, to: Rect, fraction: Float): Rect {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * f
    return Rect(mix(from.left, to.left), mix(from.top, to.top), mix(from.right, to.right), mix(from.bottom, to.bottom))
}

val LocalPlayerMorph = staticCompositionLocalOf<PlayerMorphState?> { null }

@Composable
internal fun PlayerMorphOverlay(state: PlayerMorphState, currentId: String?) {
    val policy = LocalLivingUi.current
    val request = state.request
    LaunchedEffect(currentId, policy.animate) {
        if (!policy.animate || state.request?.track?.videoId?.let { it != currentId } == true) state.cancel()
    }
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val target = withTimeoutOrNull(400) { snapshotFlow { state.endpoint }.filterNotNull().first() }
        if (state.request != request) return@LaunchedEffect
        if (target == null) { state.cancel(); return@LaunchedEffect }
        state.destination = target
        val animation = Animatable(0f)
        animation.animateTo(1f, tween(policy.duration(520), easing = FastOutSlowInEasing)) {
            if (state.request == request) state.fraction = value
        }
        if (state.request == request) state.cancel()
    }
    if (request != null) {
        val rect = state.currentRect ?: return
        val density = LocalDensity.current
        val radius = if (request.opening) 10f + 16f * state.fraction else 26f - 16f * state.fraction
        Artwork(request.track.thumbnailUrl,
            Modifier.offset { IntOffset((rect.left - state.root.left).roundToInt(), (rect.top - state.root.top).roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
            shape = RoundedCornerShape(radius.dp), videoId = request.track.videoId, requestedPixels = 1200,
            placeholderUrl = dev.pyte.data.remote.ArtworkUrls.sized(request.track.thumbnailUrl, with(density) { 44.dp.roundToPx() }))
    }
}

/** Register only the outer bounds: animated song artwork must not change the morph's endpoint. */
@Composable
internal fun HeroCoverFrame(id: String, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val morph = LocalPlayerMorph.current
    DisposableEffect(morph, id) { onDispose { morph?.clearHero(id) } }
    Box(modifier.onGloballyPositioned { morph?.heroBounds(id, it.boundsInWindow()) }) {
        content(Modifier.fillMaxSize().graphicsLayer { alpha = if (morph?.hiddenId == id) 0f else 1f })
    }
}
