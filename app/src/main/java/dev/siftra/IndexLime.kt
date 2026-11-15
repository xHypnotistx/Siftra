@file:OptIn(ExperimentalFoundationApi::class)

package dev.siftra

import android.graphics.Rect
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.*

/* ======================= Index Lime palette ======================= */

val Lime = Color(0xFFC6FF3D)
val LimeInk = Color(0xFFEDEFE6)
private val Muted = Color(0xFF8B907E)
private val Faint = Color(0xFF6F7463)
private val TrRed = Color(0xFFE30A17)
private val G4 = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))

fun limeTheme(b: UiTheme) = UiTheme(Lime, Color(0xFFE4FF9A), Color(0xFF1A1D12), Lime, Color(0xFF9AE6FF),
    Color(0xFFB8BDAA), Color(0xFF0A0B08), true, b.body, b.display, b.themedIcons)

/* ======================= motion clock ======================= */

/** Seconds of animation time. Runs only while the activity is resumed (home visible) — paused otherwise. */
@Composable
fun rememberMotionTime(enabled: Boolean, fps: Int = 0): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    val owner = LocalContext.current as? LifecycleOwner
    var resumed by remember { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> resumed = e.targetState.isAtLeast(Lifecycle.State.RESUMED) }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    if (enabled && resumed && !TestHooks.freezeMotion) LaunchedEffect(fps) {
        val base = t.floatValue
        val start = withFrameNanos { it }
        val gap = if (fps > 0) 1000L / fps else 0L        // capped: the display can drop to a low refresh rate between steps
        while (true) {
            withFrameNanos { t.floatValue = base + (it - start) / 1e9f }
            if (gap > 0) delay(gap)
        }
    }
    return t
}

/* ======================= per-group living text themes ======================= */

private fun frac(x: Float) = x - floor(x)
private fun hash(i: Int, k: Int) = frac(sin(i * 12.9898f + k * 78.233f) * 43758.547f)
private fun bell(x: Float, c: Float, w: Float) = max(0f, 1f - abs(x - c) / w)
private fun glitch(t: Float) = frac(t / 4f + 0.5f) < 0.04f   // brief flicker every 4 s, never at start

/** Calm, still colours used while a group is not being interacted with. */
private fun staticColor(g: Group, i: Int, n: Int): Color {
    val k = if (n > 1) i / (n - 1f) else 0f
    return when (g) {
        Group.MINE -> Lime
        Group.GOV -> TrRed
        Group.INSPIRE -> Color.hsv((260f + k * 120f) % 360f, 0.28f, 1f)
        Group.SAMSUNG -> Color(0xFF5B83FF)
        Group.GOOGLE -> G4[i % 4]
        Group.SOCIAL -> lerp(Color(0xFFFF4F8B), Color(0xFFFF9A3D), k)
        Group.MEDIA -> lerp(Color(0xFFE040FB), Color(0xFF8E7CFF), k)
        Group.GAMES -> Color(0xFF3DF5FF)
        Group.FINANCE -> Color(0xFF2ED38A)
        Group.SHOPPING -> Color(0xFFFF9A3D)
        Group.TRAVEL -> lerp(Color(0xFF6EC8FF), Color(0xFFFF7A59), k)
        Group.PRODUCTIVITY -> LimeInk
        Group.TOOLS -> Color(0xFFA9B1BC)
        Group.OTHER, Group.SAMSUNG_EXTRA -> Muted
        else -> LimeInk            // custom groups
    }
}

/** Living colours — only while the group is unfolded, armed or being dragged. */
private fun themeColor(g: Group, i: Int, n: Int, t: Float, lead: Boolean): Color = if (!lead) staticColor(g, i, n) else when (g) {
    Group.MINE -> Lime
    Group.GOV -> lerp(TrRed, Color.White, bell(frac(t / 3.6f - i * 0.025f), 0.75f, 0.09f))
    Group.INSPIRE -> Color.hsv((t * 24f + i * 22f) % 360f, 0.30f, 1f)
    Group.SAMSUNG -> lerp(Color(0xFF5B83FF), Color(0xFFE3EAFF), bell(frac(t / 4.2f - i * 0.03f), 0.7f, 0.12f))
    Group.GOOGLE -> { val x = frac(t / 8f + i * 0.16f) * 4f; val a = x.toInt() % 4; lerp(G4[a], G4[(a + 1) % 4], x - floor(x)) }
    Group.SOCIAL -> lerp(Color(0xFFFF4F8B), Color(0xFFFF9A3D), if (n > 1) i / (n - 1f) else 0f)
    Group.MEDIA -> lerp(Color(0xFFE040FB), Color(0xFF8E7CFF), 0.5f + 0.5f * sin(t * 1.5f + i * 0.6f))
    Group.GAMES -> if (glitch(t) && hash(i, (t * 20).toInt()) > 0.6f) Color(0xFFFF3DCB) else Color(0xFF3DF5FF)
    Group.FINANCE -> lerp(Color(0xFF2ED38A), Color(0xFFFFD45C), bell(frac(t / 4.5f - i * 0.03f), 0.72f, 0.1f))
    Group.SHOPPING -> Color(0xFFFF9A3D)
    Group.TRAVEL -> {
        val x = 1f - abs(2f * frac(i / max(n, 1).toFloat() * 0.6f + t * 0.06f) - 1f)          // 0→1→0 loop
        if (x < 0.5f) lerp(Color(0xFF6EC8FF), Color(0xFFFFC7A1), x * 2f) else lerp(Color(0xFFFFC7A1), Color(0xFFFF7A59), (x - 0.5f) * 2f)
    }
    Group.PRODUCTIVITY -> LimeInk
    Group.TOOLS -> lerp(Color(0xFFA9B1BC), Color.White, bell(frac(t / 2.8f - i * 0.04f), 0.6f, 0.08f))
    else -> Lime
}

private fun themeDx(g: Group, i: Int, t: Float, lead: Boolean) = if (lead && g == Group.GAMES && glitch(t)) (hash(i, (t * 30).toInt()) - 0.5f) * 6f else 0f
private fun themeAlpha(g: Group, i: Int, t: Float, lead: Boolean) = if (lead && g == Group.MINE && hash(i, (t * 6).toInt()) > 0.93f) 0.35f else 1f

/** Group name, letter by letter: rises in on first show, then keeps living in its group's theme. */
@Composable
private fun LiveTitle(g: Group, rowIndex: Int, lead: Boolean, t: State<Float>) {
    val ui = LocalUi.current
    val size by animateFloatAsState(if (lead) 40f else 30f, spring(dampingRatio = 1f, stiffness = 300f), label = "ts")
    val style = TextStyle(fontFamily = ui.display, fontSize = size.sp, lineHeight = (size * 1.12f).sp,
        fontStyle = if (lead) FontStyle.Italic else FontStyle.Normal)
    val letters = remember(g) { g.title.map { if (it == ' ') " " else it.toString() } }
    val n = letters.size
    Row(verticalAlignment = Alignment.Bottom) {
        letters.forEachIndexed { i, ch ->
            val rise = remember { Animatable(if (TestHooks.freezeMotion) 1f else 0f) }
            if (!TestHooks.freezeMotion) LaunchedEffect(Unit) {
                delay((140 + rowIndex * 70 + i * 28).toLong())
                rise.animateTo(1f, tween(600, easing = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)))
            }
            BasicText(ch, Modifier.graphicsLayer {
                val p = rise.value; val tt = t.value
                alpha = p * themeAlpha(g, i, tt, lead)
                translationY = ((1f - p) * 12f).dp.toPx()                       // soft entrance lift, no bounce
                translationX = themeDx(g, i, tt, lead).dp.toPx()
                renderEffect = if (p < 0.99f) BlurEffect((1f - p) * 4.dp.toPx() + 0.01f, (1f - p) * 4.dp.toPx() + 0.01f) else null
            }, style = style, color = { themeColor(g, i, n, t.value, lead) })
        }
        if (g == Group.PRODUCTIVITY && lead) BasicText("▍", Modifier.graphicsLayer { alpha = if (frac(t.value / 1.06f) < 0.5f) 1f else 0f },
            style = style, color = { Lime })
    }
}

/** Crescent and star in Turkish red. */
@Composable
private fun Crescent() = Canvas(Modifier.size(18.dp)) {
    val u = size.width / 24f
    drawCircle(TrRed, 7f * u, Offset(10f * u, 12f * u))
    drawCircle(Color(0xFF0A0B08), 5.6f * u, Offset(11.8f * u, 12f * u))
    val p = Path().apply {
        val cx = 17f * u; val cy = 12f * u; val r1 = 2.6f * u; val r2 = 1.05f * u
        for (k in 0 until 10) {
            val a = -PI / 2 + k * PI / 5; val r = if (k % 2 == 0) r1 else r2
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    rotate(18f, Offset(17f * u, 12f * u)) { drawPath(p, TrRed) }
}

/* ======================= the layout ======================= */

private class TapMemo { var row: Group? = null; var at = 0L; var prevOpen: Group? = null }

@Composable
fun IndexLime(
    s: Siftra, sections: List<Pair<Group, List<AppItem>>>, overlay: Boolean,
    onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit, conn: NestedScrollConnection, modifier: Modifier,
    onGroupLong: (Group) -> Unit = {},
) {
    var open by remember { mutableStateOf<Group?>(null) }
    var armed by remember { mutableStateOf<Group?>(null) }
    var dragging by remember { mutableStateOf<Group?>(null) }
    val t = rememberMotionTime(open != null || armed != null || dragging != null, fps = if (dragging != null) 0 else 60)
    var dragPx by remember { mutableFloatStateOf(0f) }
    var learned by remember { mutableStateOf(s.prefs.getBoolean("dragLearned", false)) }
    val fulls = remember { mutableStateMapOf<Group, Float>() }
    val memo = remember { TapMemo() }
    val view = LocalView.current
    val density = LocalDensity.current

    BackHandler(open != null && !overlay) { open = null }
    LaunchedEffect(armed) { if (armed != null) { delay(4000); if (dragging == null) armed = null } }   // disarm if no drag follows

    Column(modifier) {
        if (!learned) Row(Modifier.padding(bottom = 6.dp)) {
            T("Tap to unfold · ", 11.5.sp, color = Muted)
            T("double-tap, then drag ↓", 11.5.sp, color = Lime)
            T(" to pull a group open", 11.5.sp, color = Muted)
        }
        LazyColumn(Modifier.weight(1f).nestedScroll(conn), contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(sections, key = { _, it -> it.first.name }) { i, (g, list) ->
                val lead = open == g || armed == g || dragging == g
                val rowBg by animateColorAsState(if (armed == g || dragging == g) Lime.copy(alpha = 0.08f) else Color.Transparent, tween(300), label = "rb")
                fun toggle() { memo.prevOpen = open; open = if (open == g) null else g }

                Column(Modifier.animateItem()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(rowBg)
                            .pointerInput(g) {
                                val slop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val isDouble = memo.row == g && down.uptimeMillis - memo.at < 320
                                    if (armed == g || isDouble) {
                                        if (isDouble && armed != g) {
                                            open = memo.prevOpen                       // undo the first tap's toggle
                                            armed = g
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        memo.at = 0L
                                        val startY = down.position.y
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed) break
                                            val dy = ch.position.y - startY
                                            if (dragging != g && dy > slop) dragging = g
                                            if (dragging == g) dragPx = max(0f, dy - slop)
                                            ch.consume()                               // armed row owns the gesture
                                        }
                                        if (dragging == g) {
                                            val full = fulls[g] ?: with(density) { 120.dp.toPx() }
                                            val pass = dragPx > min(with(density) { 110.dp.toPx() }, full * 0.35f)
                                            if (pass) {
                                                open = g
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                if (!learned) { learned = true; s.prefs.edit().putBoolean("dragLearned", true).apply() }
                                            }
                                            dragging = null; armed = null; dragPx = 0f
                                        }
                                    } else {
                                        var timedOut = true
                                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation().also { timedOut = false } }
                                        if (timedOut) {                                   // held still → long-press: rename / delete
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onGroupLong(g)
                                            do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                                        } else if (up != null) { toggle(); memo.row = g; memo.at = up.uptimeMillis }
                                    }
                                }
                            }
                            .clearAndSetSemantics { text = AnnotatedString(g.title); contentDescription = g.title; onClick { toggle(); true } }
                            .padding(top = 7.dp, bottom = 7.dp, end = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        T("%02d".format(i + 1), 11.sp, Modifier.width(30.dp).padding(bottom = 8.dp), FontWeight.SemiBold,
                            color = if (lead) Lime else Faint, tracking = 1.5.sp)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveTitle(g, i, lead, t)
                            if (g == Group.GOV) Crescent()
                            if (armed == g && dragging != g) Box(Modifier.graphicsLayer { alpha = 0.55f + 0.45f * (0.5f + 0.5f * sin(t.value * 5.7f)) }
                                .clip(RoundedCornerShape(10.dp)).background(Lime).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                T("DRAG ↓", 10.5.sp, weight = FontWeight.SemiBold, color = Color(0xFF0A0B08), tracking = 1.sp)
                            }
                        }
                        GroupDot(list, Modifier.padding(bottom = 12.dp))
                        T("${list.size}", 12.sp, Modifier.padding(bottom = 8.dp), FontWeight.SemiBold, color = if (lead) Lime else Color(0xFFB8BDAA))
                    }
                    LimeDrawer(list, open == g, dragging == g, dragPx, onLaunch, onLong) { fulls[g] = it }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LimeInk.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

/** The unfolding drawer: springs open/closed, or follows the finger (rubber-band past full) while dragging. */
@Composable
private fun LimeDrawer(list: List<AppItem>, isOpen: Boolean, isDragging: Boolean, dragPx: Float,
                       onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit, reportFull: (Float) -> Unit) {
    val density = LocalDensity.current
    var fullPx by remember { mutableFloatStateOf(0f) }
    val h = remember { Animatable(0f) }
    LaunchedEffect(isOpen, isDragging, fullPx) {
        if (!isDragging) h.animateTo(if (isOpen) fullPx else 0f, spring(dampingRatio = 0.72f, stiffness = 320f))
    }
    LaunchedEffect(dragPx, isDragging) {
        if (isDragging) h.snapTo(if (dragPx <= fullPx || fullPx == 0f) dragPx else fullPx + (dragPx - fullPx) * 0.3f)
    }
    if (!(isOpen || isDragging || h.value > 0.5f)) return
    val cur = h.value
    Box(Modifier.fillMaxWidth().height(with(density) { cur.coerceAtLeast(0f).toDp() }).clipToBounds()) {
        Column(
            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .onSizeChanged { fullPx = it.height.toFloat(); reportFull(fullPx) }
                .padding(top = 6.dp, bottom = 12.dp).glass(22.dp).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val rowPx = with(density) { 82.dp.toPx() }
            val topPx = with(density) { 20.dp.toPx() }
            @Composable fun Pop(j: Int, rowIdx: Int, content: @Composable () -> Unit) {
                val shown = cur > topPx + rowIdx * rowPx + with(density) { 30.dp.toPx() }
                val stagger = if (isOpen && !isDragging) 60 + min(j, 10) * 38 else 0
                val a by animateFloatAsState(if (shown) 1f else 0f, tween(320, delayMillis = stagger), label = "pa")
                val sc by animateFloatAsState(if (shown) 1f else 0.82f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "ps")
                Box(Modifier.graphicsLayer { alpha = a; scaleX = sc; scaleY = sc; translationY = (1f - a) * -12.dp.toPx() }) { content() }
            }
            if (list.size > 8) {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(list, key = { _, a -> a.key }) { j, a -> Box(Modifier.width(72.dp)) { Pop(j, 0) { AppIcon(a, onLaunch, onLong) } } }
                }
            } else list.chunked(4).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { k, a -> Box(Modifier.weight(1f)) { Pop(r * 4 + k, r) { AppIcon(a, onLaunch, onLong) } } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
