package dev.siftra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ======================= per-layout UI style ======================= */

/** How lists, labels and shapes look in each home layout (the concepts' "theme"). */
enum class RowStyle { NUMBERED, CARD, OFFSET, CAPS, PILL }

val HomeLayout.rowStyle get() = when (this) {
    HomeLayout.INDEX -> RowStyle.NUMBERED; HomeLayout.CARDS -> RowStyle.CARD; HomeLayout.STRATA -> RowStyle.OFFSET
    HomeLayout.MONOLITH -> RowStyle.CAPS; HomeLayout.ORBIT -> RowStyle.PILL
}
val RowStyle.radius get() = when (this) { RowStyle.CAPS -> 16.dp; RowStyle.PILL -> 30.dp; RowStyle.CARD -> 24.dp; else -> 22.dp }

val LocalRowStyle = staticCompositionLocalOf { RowStyle.CARD }

@Composable fun muted() = LocalInk.current.copy(alpha = 0.62f)
@Composable fun onAccent() = if (LocalUi.current.accent.luminance() > 0.5f) Color(0xFF0A0B08) else Color.White

/* ======================= line glyphs (24-unit stroke paths) ======================= */

val GLYPHS = mapOf(
    "img" to "M4 5h16v14H4zM4 16l5-4 4 3 3-2 4 3", "play" to "M8 5.5v13l10-6.5z",
    "shield" to "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z", "heart" to "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z",
    "doc" to "M6 4h9l3 3v13H6zM9 11h6M9 15h6", "chat" to "M5 6h14v9H10l-4 3v-3H5z", "star" to "M12 4l2.4 5 5.6.8-4 3.9 1 5.5-5-2.6-5 2.6 1-5.5-4-3.9 5.6-.8z",
    "search" to "M11 5a6 6 0 1 0 0 12a6 6 0 1 0 0-12M20 20l-4.5-4.5", "back" to "M15 5l-7 7 7 7", "chev" to "M9 5l7 7-7 7",
    "x" to "M6 6l12 12M18 6L6 18", "plus" to "M12 5v14M5 12h14", "eye" to "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    "eyeoff" to "M3 3l18 18M10.6 6.2A10 10 0 0 1 12 6c6 0 10 6 10 6a17 17 0 0 1-3.2 3.8M6.2 7.6A17 17 0 0 0 2 12s4 7 10 7a9.6 9.6 0 0 0 4.4-1",
    "info" to "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M12 11v5M12 8v.5", "trash" to "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13",
    "move" to "M5 9l-3 3 3 3M19 9l3 3-3 3M2 12h20", "drag" to "M9 6h.5M14.5 6h.5M9 12h.5M14.5 12h.5M9 18h.5M14.5 18h.5",
    "palette" to "M12 4a8 8 0 0 0 0 16c1.5 0 2-1 2-2s-1-1.5-1-2.5S14 14 15 14h2a3 3 0 0 0 3-3c0-4-3.6-7-8-7zM8 11h.5M11 8h.5M15 9h.5",
    "layers" to "M12 4l9 5-9 5-9-5zM3 14l9 5 9-5", "sparkle" to "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z",
    "apps" to "M5 5h4v4H5zM15 5h4v4h-4zM5 15h4v4H5zM15 15h4v4h-4z", "home" to "M4 11l8-7 8 7v9H4z",
    "github" to "M9 19c-4 1.3-4-2-6-2.5M15 21v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1-.3-3.5 1.3a12 12 0 0 0-6.2 0C6.4 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21",
    "key" to "M15 4a5 5 0 1 0 0 10a5 5 0 1 0 0-10M11.5 12.5L4 20M7 17l2 2", "refresh" to "M20 11a8 8 0 0 0-14.8-4M4 4v4h4M4 13a8 8 0 0 0 14.8 4M20 20v-4h-4",
    "bar" to "M5 20V10M12 20V4M19 20v-7", "web" to "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M4 12h16", "note" to "M6 4h9l3 3v13H6z",
    "check" to "M5 12l5 5 9-10", "bag" to "M6 8h12l-1 11H7zM9 8a3 3 0 0 1 6 0", "globe" to "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M4 12h16M12 4c3 3 3 13 0 16M12 4c-3 3-3 13 0 16",
    "coin" to "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16M9 9h4.5a1.5 1.5 0 0 1 0 3H10a1.5 1.5 0 0 0 0 3H15", "map" to "M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2zM9 4v14M15 6v14",
    "music" to "M9 18V6l10-2v12M7 16a2 2 0 1 0 0 4a2 2 0 1 0 0-4", "cam" to "M3 7h18v12H3zM12 10a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    "pin" to "M12 21s6-6 6-11a6 6 0 0 0-12 0c0 5 6 11 6 11zM12 8a2 2 0 1 0 0 4a2 2 0 1 0 0-4", "grid" to "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
    "bolt" to "M13 3L5 14h6l-1 7 8-11h-6z", "note2" to "M6 4h9l3 3v13H6zM9 11h6M9 15h6", "folder" to "M3 7h6l2 2h10v10H3z",
)

private val pathCache = HashMap<String, Path>()
private fun glyphPath(name: String) = pathCache.getOrPut(name) { PathParser().parsePathString(GLYPHS[name] ?: GLYPHS.getValue("apps")).toPath() }

@Composable
fun Glyph(name: String, color: Color, size: Dp = 22.dp, stroke: Float = 1.7f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(glyphPath(name), color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Themed icon tile used across settings / menus. */
@Composable
fun IconTile(name: String, size: Dp = 40.dp, k: Int = 0) {
    val st = LocalRowStyle.current
    val ui = LocalUi.current
    val (bg, stroke) = when (st) {
        RowStyle.NUMBERED -> Brush.linearGradient(listOf(Color(0xFF1A1D12), Color(0xFF1A1D12))) to Lime
        RowStyle.PILL -> Brush.linearGradient(listOf(Color.White, Color.White)) to ui.accent
        RowStyle.CAPS -> listOf(listOf(Color(0xFFFF8A4C), Color(0xFFC2410C)), listOf(Color(0xFF3B5BDB), Color(0xFF13307A)), listOf(Color(0xFFE11D48), Color(0xFF7F1D1D)))[k % 3]
            .let { Brush.linearGradient(it) } to Color.White
        else -> listOf(listOf(Color(0xFF6F8CFF), Color(0xFF3B4FD8)), listOf(Color(0xFFA78BFA), Color(0xFF6D28D9)), listOf(Color(0xFF22D3EE), Color(0xFF2563EB)))[k % 3]
            .let { Brush.linearGradient(it) } to Color.White
    }
    val shape = if (st == RowStyle.PILL) CircleShape else RoundedCornerShape(size * 0.32f)
    Box(Modifier.size(size).clip(shape).background(bg).border(1.dp, stroke.copy(alpha = 0.25f), shape), contentAlignment = Alignment.Center) {
        Glyph(name, stroke, size * 0.46f)
    }
}

/* ======================= text ======================= */

@Composable
fun SLabel(text: String, modifier: Modifier = Modifier) {
    val st = LocalRowStyle.current
    if (st == RowStyle.CAPS || st == RowStyle.NUMBERED) T(text.uppercase(), 10.5.sp, modifier, FontWeight.SemiBold, muted(), tracking = 2.sp)
    else T(text, 13.sp, modifier, FontWeight.SemiBold, muted())
}

@Composable
fun STitle(text: String, size: Int = 40) {
    val lime = LocalRowStyle.current == RowStyle.NUMBERED
    T(text, size.sp, display = true, italic = lime, color = if (lime) LocalUi.current.accent else Color.Unspecified, lineHeight = (size * 1.05f).sp)
}

/* ======================= controls ======================= */

@Composable
fun SRow(index: Int, icon: String, title: String, sub: String = "", k: Int = 0, onClick: (() -> Unit)? = null,
         trailing: @Composable () -> Unit = { Glyph("chev", muted(), 18.dp) }) {
    val st = LocalRowStyle.current
    val ui = LocalUi.current
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    when (st) {
        RowStyle.NUMBERED -> Column {
            Row(Modifier.fillMaxWidth().then(click).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("%02d".format(index), 11.sp, Modifier.width(32.dp), FontWeight.SemiBold, ui.accent, tracking = 1.5.sp)
                Column(Modifier.weight(1f)) {
                    T(title, 25.sp, display = true, maxLines = 1)
                    if (sub.isNotEmpty()) T(sub, 12.sp, color = muted(), maxLines = 2)
                }
                trailing()
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.copy(alpha = 0.12f)))
        }
        else -> {
            val off = if (st == RowStyle.OFFSET) listOf(0, 10, 20, 10, 0, 14, 6)[index % 7].dp else 0.dp
            Row(Modifier.padding(start = off, end = if (st == RowStyle.OFFSET) 20.dp - off else 0.dp).fillMaxWidth()
                .glass(st.radius).then(click).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(icon, 40.dp, k)
                Column(Modifier.weight(1f)) {
                    when (st) {
                        RowStyle.CAPS -> T(title.uppercase(), 11.sp, weight = FontWeight.SemiBold, tracking = 2.sp, maxLines = 1)
                        RowStyle.PILL -> T(title, 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                        else -> T(title, 21.sp, display = true, maxLines = 1)
                    }
                    if (sub.isNotEmpty()) T(sub, 12.sp, Modifier.padding(top = 3.dp), color = muted(), maxLines = 2)
                }
                trailing()
            }
        }
    }
}

@Composable
fun SToggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val ui = LocalUi.current
    val light = LocalInk.current.luminance() < 0.5f
    val track by animateColorAsState(if (on) ui.accent else if (light) Color(0x2E231D4F) else Color(0x2EFFFFFF), label = "tt")
    val x by animateDpAsState(if (on) 23.dp else 3.dp, spring(dampingRatio = 1f, stiffness = 500f), label = "tx")
    Box(Modifier.size(48.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(track)
        .semantics { role = Role.Switch; contentDescription = label; stateDescription = if (on) "On" else "Off" }
        .clickable { onChange(!on) }) {
        Box(Modifier.offset(x = x, y = 3.dp).size(22.dp).clip(CircleShape).background(if (on) onAccent() else Color.White))
    }
}

@Composable
fun SToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14.sp, Modifier.weight(1f)); SToggle(on, label, onChange)
    }
}

@Composable
fun SChips(items: List<String>, selected: String?, onPick: (String) -> Unit) {
    val st = LocalRowStyle.current
    val ui = LocalUi.current
    val light = LocalInk.current.luminance() < 0.5f
    val shape = RoundedCornerShape(when (st) { RowStyle.PILL, RowStyle.CARD, RowStyle.OFFSET -> 50.dp; RowStyle.CAPS -> 8.dp; else -> 12.dp })
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { t ->
            val on = t == selected
            Box(Modifier.clip(shape)
                .background(if (on) ui.accent else if (light) Color(0x59FFFFFF) else Color(0x0FFFFFFF))
                .border(1.dp, if (on) ui.accent else LocalInk.current.copy(alpha = 0.18f), shape)
                .clickable { onPick(t) }.padding(horizontal = 13.dp, vertical = 8.dp)) {
                T(t, 12.5.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) onAccent() else Color.Unspecified)
            }
        }
    }
}

@Composable
fun SButton(text: String, primary: Boolean = true, icon: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val st = LocalRowStyle.current
    val ui = LocalUi.current
    val shape = RoundedCornerShape(when (st) { RowStyle.PILL, RowStyle.OFFSET, RowStyle.CARD -> 50.dp; RowStyle.CAPS -> 10.dp; else -> 16.dp })
    val base = modifier.height(52.dp).clip(shape)
    val m = if (primary) base.background(ui.accent) else base.glass(if (st == RowStyle.PILL) 26.dp else st.radius)
    Row(m.clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        val c = if (primary) onAccent() else LocalInk.current
        if (icon != null) Glyph(icon, c, 18.dp)
        T(if (st == RowStyle.CAPS) text.uppercase() else text, 15.sp, weight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = c, tracking = if (st == RowStyle.CAPS) 1.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified)
    }
}

@Composable
fun SCard(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(LocalRowStyle.current.radius).padding(pad), content = content)
}

@Composable
fun SBack(onBack: () -> Unit) {
    Box(Modifier.size(44.dp).glass(22.dp).clickable(onClick = onBack).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center) { Glyph("back", LocalInk.current, 20.dp) }
}

/* ======================= tiny home-layout thumbnails ======================= */

@Composable
fun MiniHome(layout: HomeLayout, w: Dp = 104.dp, h: Dp = 186.dp) {
    val ui = LocalUi.current
    val ink = LocalInk.current
    val fill = if (ink.luminance() < 0.5f) Color(0x80FFFFFF) else Color(0x24FFFFFF)
    Column(Modifier.size(w, h).glass(18.dp).padding(10.dp)) {
        Box(Modifier.size(26.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(fill))
        Spacer(Modifier.height(10.dp))
        when (layout) {
            HomeLayout.CARDS -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(2) { Box(Modifier.size((w - 25.dp) / 2, 34.dp).clip(RoundedCornerShape(7.dp)).background(fill)) } } }
            }
            HomeLayout.STRATA -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 5, 10, 5, 0, 7).forEach { o -> Box(Modifier.padding(start = o.dp).fillMaxWidth().height(15.dp).clip(RoundedCornerShape(6.dp)).background(fill)) }
            }
            HomeLayout.MONOLITH -> Column {
                T("12", 26.sp, display = true, lineHeight = 22.sp); T("24", 26.sp, display = true, italic = true, color = ui.accent, lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
                repeat(2) { Row(Modifier.padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(2) { Box(Modifier.size((w - 25.dp) / 2, 22.dp).clip(RoundedCornerShape(5.dp)).background(fill)) } } }
            }
            HomeLayout.ORBIT -> @OptIn(ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(38, 30, 26, 34, 22, 28).forEach { d -> Box(Modifier.size(d.dp).clip(CircleShape).background(fill)) }
            }
            HomeLayout.INDEX -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(62, 50, 70, 44, 58, 40).forEachIndexed { k, wd ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp, 3.dp).background(ui.accent))
                        Box(Modifier.size(wd.dp, 7.dp).clip(RoundedCornerShape(2.dp)).background(if (k == 0) ui.accent else ink.copy(alpha = 0.8f)))
                    }
                }
            }
        }
    }
}
