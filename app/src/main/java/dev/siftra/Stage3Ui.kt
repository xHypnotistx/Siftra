@file:OptIn(ExperimentalLayoutApi::class)

package dev.siftra

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Calendar

/* ======================= shared sheet ======================= */

@Composable
fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).noRipple(onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().glass(30.dp, strong = true).noRipple { }.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(muted().copy(alpha = 0.6f)))
            content()
        }
    }
}

private fun weekAgo() = System.currentTimeMillis() - 7L * 86_400_000

/* ======================= long-press app menu ======================= */

@Composable
fun AppMenuSheet(s: Siftra, a: AppItem, onClose: () -> Unit, onMove: (AppItem) -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val shortcuts = remember(a) { s.shortcuts(a) }
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppGlyph(a, 60.dp)
            Column(Modifier.weight(1f)) {
                T(a.label, 30.sp, display = true, maxLines = 1, lineHeight = 32.sp)
                T("${(s.groupOf[a.key] ?: Group.OTHER).title} · ${s.opens(a, weekAgo())} opens this week", 12.sp, color = muted())
            }
        }
        val fav = s.isFav(a)
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Triple(if (fav) "star" else "star", if (fav) "Unfavorite" else "Favorite") { s.toggleFav(a); onClose() },
                Triple("move", "Move") { onMove(a) },
                Triple("eyeoff", "Hide") { s.hide(a); onClose() },
                Triple("info", "Info") { s.appInfo(a); onClose() },
                Triple("trash", "Uninstall") { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))); onClose() },
            ).forEachIndexed { i, (g, label, act) ->
                val accent = i == 0 && fav
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = act).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shape = if (LocalRowStyle.current in listOf(RowStyle.PILL, RowStyle.CARD, RowStyle.OFFSET)) CircleShape else RoundedCornerShape(14.dp)
                    Box(Modifier.size(48.dp).clip(shape).background(if (accent) ui.accent else LocalInk.current.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        Glyph(g, if (accent) onAccent() else LocalInk.current, 20.dp)
                    }
                    T(label, 11.5.sp)
                }
            }
        }
        if (shortcuts.isNotEmpty()) {
            SLabel("Shortcuts")
            val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 6.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                shortcuts.forEachIndexed { i, si ->
                    val icon = remember(si.id) { s.shortcutIcon(si) }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { s.startShortcut(si); onClose() }.padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (icon != null) Image(icon, null, Modifier.size(32.dp)) else IconTile("chev", 32.dp, i)
                        T((si.shortLabel ?: si.longLabel ?: "Shortcut").toString(), 15.sp, Modifier.weight(1f), maxLines = 1)
                        Glyph("chev", muted(), 16.dp)
                    }
                }
            }
        }
        T("Tap outside to close", 12.sp, Modifier.fillMaxWidth(), color = muted(), center = true)
    }
}

/* ======================= move to group (+ new group) ======================= */

@Composable
fun MoveSheet(s: Siftra, a: AppItem, onClose: () -> Unit) {
    val ui = LocalUi.current
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val current = s.groupOf[a.key]
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppGlyph(a, 40.dp)
            Column {
                T("Move", 12.sp, color = muted()); T("${a.label} to…", 24.sp, display = true, maxLines = 1)
                T(s.sourceOf(a), 11.5.sp, color = muted())
            }
        }
        val counts = remember(s.apps, s.groupOf) { s.sections(true).associate { it.first to it.second.size } }
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 6.dp)) {
            s.groupOrder.forEachIndexed { i, g ->
                val on = g == current
                SRow(i + 1, g.glyph, g.title, "${counts[g] ?: 0} apps" + if (on) " · current" else "", i, onClick = { s.move(a, g); onClose() }) {
                    if (on) Box(Modifier.size(22.dp).clip(CircleShape).background(ui.accent), contentAlignment = Alignment.Center) { Glyph("check", onAccent(), 14.dp, 2.6f) }
                    else Box(Modifier.size(20.dp).clip(CircleShape).background(Color.Transparent).then(Modifier.graphicsLayer { }), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(20.dp)) { drawCircle(Color.Gray.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx())) }
                    }
                }
            }
        }
        if (!naming) SButton("New group…", false, "plus") { naming = true }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(52.dp).glass(16.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) T("Group name, e.g. Study", 15.sp, color = muted())
                BasicTextField(name, { name = it.take(28) }, Modifier.fillMaxWidth().semantics { contentDescription = "New group name" }, singleLine = true,
                    textStyle = TextStyle(LocalInk.current, 15.sp, fontFamily = ui.body), cursorBrush = SolidColor(ui.accent))
            }
            SButton("Create", true, modifier = Modifier.width(110.dp)) {
                if (name.isNotBlank()) { val g = s.createGroup(name); s.move(a, g); onClose() }
            }
        }
    }
}

/* ======================= rename / delete a group ======================= */

@Composable
fun GroupEditSheet(s: Siftra, g: Group, onClose: () -> Unit, onOpenSorting: () -> Unit) {
    val ui = LocalUi.current
    var name by remember(g) { mutableStateOf(g.title) }
    var confirm by remember { mutableStateOf(false) }
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(g.glyph, 44.dp)
            Column { T(if (g.custom) "Your group" else "Built-in group", 12.sp, color = muted()); T(g.title, 26.sp, display = true, maxLines = 1) }
        }
        if (!g.custom) {
            T("Built-in groups can't be renamed or deleted, but you can reorder them and move apps in or out.", 13.sp, color = muted(), lineHeight = 19.sp)
            SButton("Reorder groups", false, "drag") { onClose(); onOpenSorting() }
        } else {
            Box(Modifier.fillMaxWidth().height(52.dp).glass(16.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(name, { name = it.take(28) }, Modifier.fillMaxWidth().semantics { contentDescription = "Group name" }, singleLine = true,
                    textStyle = TextStyle(LocalInk.current, 16.sp, fontFamily = ui.body), cursorBrush = SolidColor(ui.accent))
            }
            SButton("Save name", true, "check") { s.renameGroup(g, name); onClose() }
            if (!confirm) SButton("Delete group", false, "trash") { confirm = true }
            else SCard(pad = 14.dp) {
                T("Delete “${g.title}”? Its apps go back to their automatic groups.", 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SButton("Cancel", false, modifier = Modifier.weight(1f)) { confirm = false }
                    SButton("Delete", true, modifier = Modifier.weight(1f)) { s.deleteGroup(g); onClose() }
                }
            }
        }
    }
}

/* ======================= Favorites editor (Settings › Apps) ======================= */

@Composable
fun FavoritesPage(s: Siftra, back: () -> Unit) {
    val ui = LocalUi.current
    var adding by remember { mutableStateOf(false) }
    var list by remember(s.favorites) { mutableStateOf(s.favoriteApps()) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var cell by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SBack(back); Spacer(Modifier.weight(1f)) }
        STitle("Favorites")
        SCard(pad = 14.dp) { T("Swipe the home shelf to switch between these and Most used. The first 5 appear on the shelf.", 12.5.sp, color = muted(), lineHeight = 18.sp) }
        if (list.isEmpty()) SCard(pad = 16.dp) { T("No favorites yet. Add some below, or long-press any app and choose Favorite.", 13.sp, color = muted()) }
        else Column(Modifier.fillMaxWidth().glass(LocalRowStyle.current.radius).padding(vertical = 18.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            list.chunked(4).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { c, a ->
                        val dragging = dragKey == a.key
                        Box(Modifier.weight(1f).zIndex(if (dragging) 1f else 0f).onSizeChanged { cell = it }
                            .graphicsLayer { if (dragging) { translationX = drag.x; translationY = drag.y; scaleX = 1.1f; scaleY = 1.1f } }
                            .pointerInput(a.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragKey = a.key; drag = Offset.Zero },
                                    onDragEnd = { s.saveFavorites(list.map { it.key }); dragKey = null; drag = Offset.Zero },
                                    onDragCancel = { dragKey = null; drag = Offset.Zero },
                                ) { ch, d ->
                                    ch.consume(); drag += d
                                    val from = list.indexOfFirst { it.key == a.key }
                                    val col = (from % 4 + (drag.x / cell.width).let { kotlin.math.round(it).toInt() }).coerceIn(0, 3)
                                    val rowI = (from / 4 + (drag.y / (cell.height + 18.dp.toPx())).let { kotlin.math.round(it).toInt() }).coerceAtLeast(0)
                                    val to = (rowI * 4 + col).coerceIn(0, list.lastIndex)
                                    if (to != from) {
                                        list = list.toMutableList().apply { add(to, removeAt(from)) }
                                        drag -= Offset(((to % 4) - (from % 4)) * cell.width.toFloat(), ((to / 4) - (from / 4)) * (cell.height + 18.dp.toPx()))
                                    }
                                }
                            }, contentAlignment = Alignment.TopCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppGlyph(a, 52.dp); T(a.label, 11.5.sp, maxLines = 1)
                            }
                            Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).offset(y = (-6).dp).size(24.dp).clip(CircleShape).background(ui.accent)
                                .clickable { s.toggleFav(a) }.semantics { contentDescription = "Remove ${a.label}" }, contentAlignment = Alignment.Center) {
                                Glyph("x", onAccent(), 12.dp, 2.6f)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        T("Long-press and drag to reorder", 12.5.sp, Modifier.fillMaxWidth(), color = muted(), center = true)
        SButton(if (adding) "Done adding" else "Add favorites", !adding, if (adding) "check" else "plus") { adding = !adding }
        if (adding) {
            var q by remember { mutableStateOf("") }
            Box(Modifier.fillMaxWidth().height(50.dp).glass(16.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) T("Find an app", 15.sp, color = muted())
                BasicTextField(q, { q = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Find an app to favorite" }, singleLine = true,
                    textStyle = TextStyle(LocalInk.current, 15.sp, fontFamily = ui.body), cursorBrush = SolidColor(ui.accent))
            }
            val pool = (if (q.isBlank()) s.visible.sortedBy { it.label.lowercase() } else searchApps(s, q, false)).take(40)
            val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 6.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                pool.forEachIndexed { i, a ->
                    val on = s.isFav(a)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { s.toggleFav(a) }.padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppGlyph(a, 36.dp); T(a.label, 15.sp, Modifier.weight(1f), maxLines = 1)
                        Glyph("star", if (on) ui.accent else muted(), 20.dp, if (on) 2.4f else 1.6f)
                    }
                }
            }
        }
    }
}

/* ======================= Most used (Settings › Apps) ======================= */

@Composable
fun MostUsedPage(s: Siftra, back: () -> Unit) {
    val ui = LocalUi.current
    var range by remember { mutableStateOf("This week") }
    val since = when (range) {
        "Today" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        "This week" -> weekAgo()
        else -> 0L
    }
    val ranked = remember(range, s.historyTick, s.apps) { s.visible.distinctBy { it.key }.map { it to s.opens(it, since) }.filter { it.second > 0 }.sortedByDescending { it.second } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SBack(back); Spacer(Modifier.weight(1f)) }
        STitle("Most used")
        SChips(listOf("Today", "This week", "All time"), range) { range = it }
        if (ranked.isEmpty()) SCard(pad = 16.dp) {
            T(if (since > 0) "Nothing yet for ${range.lowercase()}. Dated history started with version 0.5.2 and fills up as you use your phone." else "Open a few apps and they'll show up here.",
                13.sp, color = muted(), lineHeight = 19.sp)
        } else {
            SCard(pad = 18.dp) {
                Row(Modifier.fillMaxWidth()) {
                    ranked.take(3).forEachIndexed { i, (a, n) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppGlyph(a, 56.dp); T(a.label, 12.sp, maxLines = 1)
                            T("$n", 30.sp, display = true, color = if (i == 0) ui.accent else Color.Unspecified, lineHeight = 30.sp)
                            T("opens", 10.5.sp, color = muted())
                        }
                    }
                }
            }
            if (ranked.size > 3) SCard(pad = 16.dp) {
                val max = ranked.first().second.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ranked.drop(3).take(8).forEach { (a, n) ->
                        val w by animateFloatAsState(n / max, tween(500), label = "bar")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppGlyph(a, 36.dp)
                            T(a.label, 13.sp, Modifier.width(84.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalInk.current.copy(alpha = 0.10f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(w).clip(RoundedCornerShape(4.dp)).background(ui.accent))
                            }
                            T("$n", 12.sp, Modifier.width(26.dp), FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        T("Counted only on this phone", 12.sp, Modifier.fillMaxWidth(), color = muted(), center = true)
    }
}

/* ======================= Gemini sorting progress ======================= */

@Composable
fun GeminiSortingScreen(s: Siftra, onHide: () -> Unit) {
    val pr = s.progress ?: return
    val ui = LocalUi.current
    BackHandler(onBack = onHide)
    val frac by animateFloatAsState(if (pr.total == 0) 1f else pr.done / pr.total.toFloat(), tween(500), label = "ring")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { SBack(onHide); Spacer(Modifier.weight(1f)) }
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            val track = LocalInk.current.copy(alpha = 0.10f)
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = st, topLeft = Offset(st.width / 2, st.width / 2), size = size.copy(size.width - st.width, size.height - st.width))
                drawArc(ui.accent, -90f, 360f * frac, false, style = st, topLeft = Offset(st.width / 2, st.width / 2), size = size.copy(size.width - st.width, size.height - st.width))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("${pr.done}/${pr.total}", 36.sp, display = true, lineHeight = 36.sp); T("placed", 11.sp, color = muted())
            }
        }
        T(if (pr.running) "Gemini is sorting" else if (pr.error != null) "Sorting stopped" else "All sorted", 30.sp, display = true, center = true)
        T(if (pr.error != null) pr.error!! else "${pr.total} new app${if (pr.total == 1) "" else "s"}", 22.sp, display = true, italic = true,
            color = if (pr.error != null) Color(0xFFFF6B6B) else ui.accent, center = true)
        T("Only app names are sent, once. Everything else was sorted on your phone.", 12.5.sp, color = muted(), center = true)
        SCard(pad = 16.dp) {
            pr.rows.forEachIndexed { i, (label, g) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(label, 14.sp, Modifier.weight(1f), maxLines = 1)
                    if (g != null) T("→ ${g.title}", 12.sp, weight = FontWeight.SemiBold, color = ui.accent)
                    else T(if (pr.running) "thinking…" else "—", 12.sp, color = muted())
                }
                if (i < pr.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.copy(alpha = 0.08f)))
            }
        }
        SButton(if (pr.running) "Run in background" else "Done", !pr.running) { onHide() }
    }
}
