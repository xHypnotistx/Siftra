@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.siftra

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/* ======================= search ranking ======================= */

private val trLocale = Locale.forLanguageTag("tr")
private fun norm(s: String) = s.lowercase(trLocale)
    .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

/** Lower is better: 0 prefix, 1 word-prefix, 2 contains, 3 letters-in-order. null = no match. */
private fun score(label: String, q: String): Int? {
    val l = norm(label); val n = norm(q.trim())
    if (n.isEmpty()) return null
    return when {
        l.startsWith(n) -> 0
        l.split(' ', '-', '.', '_').any { it.startsWith(n) } -> 1
        l.contains(n) -> 2
        n.length >= 3 && run { var i = 0; for (c in l) if (i < n.length && c == n[i]) i++; i == n.length } -> 3
        else -> null
    }
}

fun searchApps(s: Siftra, q: String, includeHidden: Boolean): List<AppItem> =
    (if (includeHidden) s.apps else s.visible).mapNotNull { a -> score(a.label, q)?.let { a to it } }
        .sortedWith(compareBy<Pair<AppItem, Int>> { it.second }.thenByDescending { s.counts[it.first.key] ?: 0 }.thenBy { it.first.label })
        .map { it.first }.distinctBy { it.key }

private data class SettingHit(val page: SPage, val title: String, val words: String)
private val SETTINGS_INDEX = listOf(
    SettingHit(SPage.LOOK, "Look", "look theme backdrop glass ribbed frosted clock font wallpaper colors material you icons themed photo animated"),
    SettingHit(SPage.LAYOUT, "Home layout", "layout home index cards strata monolith orbit"),
    SettingHit(SPage.SORTING, "Sorting & Gemini", "sort sorting gemini key api groups order reorder organize extras samsung"),
    SettingHit(SPage.APPS, "Apps", "apps hidden hide unhide reset moves manual"),
    SettingHit(SPage.ABOUT, "About", "about version github license fonts source"),
)
private fun settingHits(q: String): List<SettingHit> {
    val n = norm(q.trim()); if (n.length < 2) return emptyList()
    return SETTINGS_INDEX.filter { h -> norm(h.title).startsWith(n) || h.words.split(' ').any { it.startsWith(n) } }
}

private fun startOfToday() = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

/* ======================= the drawer ======================= */

enum class DrawerView { GROUPS, AZ }

@Composable
fun AppDrawer(
    s: Siftra, focusSearch: Boolean, onClose: () -> Unit, onLaunch: (AppItem, Rect?) -> Unit,
    onLong: (AppItem) -> Unit, onOrganize: () -> Unit, onOpenSettings: (SPage) -> Unit, onGroupLong: (Group) -> Unit = {},
) {
    var q by remember { mutableStateOf("") }
    var view by remember { mutableStateOf(DrawerView.entries.firstOrNull { it.name == s.prefs.getString("drawerView", "GROUPS") } ?: DrawerView.GROUPS) }
    val fr = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { if (focusSearch) { delay(150); try { fr.requestFocus() } catch (_: Exception) {} } }

    val launchFromSearch: (AppItem, Rect?) -> Unit = { a, r -> s.addRecent(q); onLaunch(a, r) }

    Column(Modifier.fillMaxSize().noRipple { }.statusBarsPadding().navigationBarsPadding().imePadding()) {
        // grab handle + swipe-down-to-close on the header
        Column(Modifier.fillMaxWidth().pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc > 120f) onClose() }) { _, dy -> acc += dy }
        }.padding(horizontal = 18.dp).padding(top = 8.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(muted().copy(alpha = 0.5f)))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(q, { q = it }, fr, Modifier.weight(1f)) {
                    val top = searchApps(s, q, false).firstOrNull()
                    if (top != null) launchFromSearch(top, null) else focus.clearFocus()
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).glass(24.dp).clickable(onClick = onOrganize).semantics { contentDescription = "Re-sort apps" },
                    contentAlignment = Alignment.Center) { Glyph("refresh", LocalInk.current, 20.dp) }
            }
        }
        AnimatedContent(q.isBlank(), transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "dq") { browsing ->
            if (browsing) Browse(s, view, { view = it; s.prefs.edit().putString("drawerView", it.name).apply() }, onLaunch, onLong, onGroupLong) { q = it }
            else SearchResults(s, q, launchFromSearch, onLong, onOpenSettings) { q = it }
        }
    }
}

@Composable
private fun SearchField(q: String, onQ: (String) -> Unit, fr: FocusRequester, modifier: Modifier, onGo: () -> Unit) {
    val ui = LocalUi.current
    val st = LocalRowStyle.current
    val r = if (st == RowStyle.PILL || st == RowStyle.CARD || st == RowStyle.OFFSET) 26.dp else st.radius
    Row(modifier.height(52.dp).glass(r).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Glyph("search", ui.accent, 18.dp)
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) T("Search apps", 15.sp, color = muted())
            BasicTextField(q, onQ, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                textStyle = TextStyle(LocalInk.current, 16.sp, fontFamily = ui.body), cursorBrush = SolidColor(ui.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { onGo() }))
        }
        if (q.isNotEmpty()) Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable { onQ("") }.semantics { contentDescription = "Clear search" },
            contentAlignment = Alignment.Center) { Glyph("x", muted(), 16.dp) }
    }
}

/* ---------- browsing: Groups or A–Z ---------- */

@Composable
private fun Browse(s: Siftra, view: DrawerView, setView: (DrawerView) -> Unit, onLaunch: (AppItem, Rect?) -> Unit,
                   onLong: (AppItem) -> Unit, onGroupLong: (Group) -> Unit, onRecent: (String) -> Unit) {
    val ui = LocalUi.current
    val st = LocalRowStyle.current
    val sections = s.sections(true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.padding(horizontal = 18.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Groups | A–Z switch
            Row(Modifier.glass(20.dp).padding(3.dp)) {
                listOf(DrawerView.GROUPS to "Groups", DrawerView.AZ to "A–Z").forEach { (v, t) ->
                    val on = v == view
                    Box(Modifier.clip(RoundedCornerShape(17.dp)).background(if (on) ui.accent else Color.Transparent).clickable { setView(v) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)) { T(t, 13.sp, weight = FontWeight.SemiBold, color = if (on) onAccent() else Color.Unspecified) }
                }
            }
            Spacer(Modifier.weight(1f))
            T("${s.visible.size} apps", 12.sp, color = muted())
        }
        val recent = s.recent
        if (recent.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("refresh", muted(), 14.dp)
            recent.forEach { r -> Box(Modifier.clip(RoundedCornerShape(14.dp)).background(LocalInk.current.copy(alpha = 0.07f)).clickable { onRecent(r) }
                .padding(horizontal = 11.dp, vertical = 6.dp)) { T(r, 12.5.sp) } }
        }
        if (view == DrawerView.GROUPS) {
            val headerIndex = remember(sections) {
                var i = 0
                sections.associate { (g, l) -> (g to i).also { i += 1 + 1 } }   // header + one grid card per group
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sections, key = { it.first.name }) { (g, _) ->
                    Box(Modifier.clip(RoundedCornerShape(if (st == RowStyle.CAPS) 8.dp else 16.dp)).glass(if (st == RowStyle.CAPS) 8.dp else 16.dp)
                        .clickable { scope.launch { listState.animateScrollToItem(headerIndex[g] ?: 0) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp)) { T(if (st == RowStyle.CAPS) g.title.uppercase() else g.title, 13.sp) }
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
                sections.forEachIndexed { gi, (g, list) ->
                    item(key = "h_${g.name}") { SectionHeader(gi + 1, g.title, list, Modifier.animateItem().groupClick({}, { onGroupLong(g) })) }
                    item(key = "g_${g.name}") {
                        Column(Modifier.animateItem().fillMaxWidth().glass(st.radius).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            list.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { a -> Box(Modifier.weight(1f)) { AppIcon(a, onLaunch, onLong) } }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        } else AzList(s, onLaunch, onLong)
    }
}

@Composable
private fun SectionHeader(n: Int, title: String, list: List<AppItem>, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val st = LocalRowStyle.current
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        when (st) {
            RowStyle.NUMBERED -> { T("%02d".format(n), 11.sp, Modifier.padding(end = 8.dp, bottom = 4.dp), FontWeight.SemiBold, ui.accent, tracking = 1.5.sp); T(title, 24.sp, display = true) }
            RowStyle.CAPS -> T(title.uppercase(), 11.sp, weight = FontWeight.SemiBold, tracking = 2.2.sp)
            RowStyle.PILL -> T(title, 17.sp, weight = FontWeight.SemiBold)
            else -> T(title, 24.sp, display = true)
        }
        Spacer(Modifier.weight(1f))
        GroupDot(list, Modifier.padding(bottom = 5.dp))
        T("${list.size}", 12.sp, weight = FontWeight.SemiBold, color = ui.accent)
    }
}

/** Alphabetical grid with a letter rail; drag or tap the rail to jump. */
@Composable
private fun AzList(s: Siftra, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit) {
    val ui = LocalUi.current
    val st = LocalRowStyle.current
    val apps = s.visible.distinctBy { it.key }.sortedBy { norm(it.label) }
    val groups = remember(apps) {
        apps.groupBy { a -> a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#" }.toSortedMap(compareBy { if (it == "#") "￿" else it })
    }
    val letters = groups.keys.toList()
    val index = remember(groups) { var i = 0; groups.entries.associate { (k, v) -> (k to i).also { i += 1 + (v.size + 3) / 4 } } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var bubble by remember { mutableStateOf<String?>(null) }
    var railH by remember { mutableFloatStateOf(1f) }
    fun jump(y: Float) {
        val i = (y / railH * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (bubble != l) { bubble = l; scope.launch { listState.scrollToItem(index[l] ?: 0) } }
    }
    Box(Modifier.fillMaxSize().padding(top = 10.dp)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 34.dp, bottom = 24.dp)) {
            groups.forEach { (l, list) ->
                item(key = "L_$l") {
                    T(l, if (st == RowStyle.CAPS) 13.sp else 26.sp, Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp),
                        if (st == RowStyle.CAPS) FontWeight.SemiBold else FontWeight.Normal, ui.accent, display = st != RowStyle.CAPS)
                }
                items(list.chunked(4), key = { r -> "R_${l}_${r.first().key}" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        row.forEach { a -> Box(Modifier.weight(1f)) { AppIcon(a, onLaunch, onLong) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // rail
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).width(24.dp).onSizeChanged { railH = it.height.toFloat() }
            .semantics { contentDescription = "Letter index" }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragStart = { jump(it.y) }, onDragEnd = { bubble = null }, onDragCancel = { bubble = null }) { ch, _ -> jump(ch.position.y) }
            }
            .pointerInput(letters) { detectTapGestures(onPress = { jump(it.y); tryAwaitRelease(); bubble = null }) },
            horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEach { l -> T(l, 10.sp, Modifier.padding(vertical = 1.dp), FontWeight.SemiBold, if (l == bubble) ui.accent else muted()) }
        }
        bubble?.let { l ->
            Box(Modifier.align(Alignment.Center).size(84.dp).glass(42.dp), contentAlignment = Alignment.Center) { T(l, 44.sp, display = true, color = ui.accent) }
        }
    }
}

/* ---------- search results ---------- */

@Composable
private fun SearchResults(s: Siftra, q: String, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit, onOpenSettings: (SPage) -> Unit, onRecent: (String) -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    var withHidden by remember { mutableStateOf(false) }
    val res = searchApps(s, q, withHidden)
    val hits = settingHits(q)
    val qq = q.trim()
    val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 8.dp
    fun web() = try { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, qq)) }
        catch (_: Exception) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(qq)))) }
    fun store() = try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(qq)))) }
        catch (_: Exception) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(qq) + "&c=apps"))) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (res.isNotEmpty()) {
            val top = res.first()
            SLabel("Top hit")
            SCard(pad = 16.dp) {
                Row(Modifier.combinedClickable(onClick = { onLaunch(top, null) }, onLongClick = { onLong(top) }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppGlyph(top, 60.dp)
                    Column(Modifier.weight(1f)) {
                        T(top.label, 28.sp, display = true, maxLines = 1, lineHeight = 30.sp)
                        val today = s.opens(top, startOfToday())
                        T("${(s.groupOf[top.key] ?: Group.OTHER).title} · " + if (today > 0) "opened $today time${if (today > 1) "s" else ""} today" else "not opened today",
                            12.sp, color = muted())
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SButton("Open", true, modifier = Modifier.weight(1f)) { onLaunch(top, null) }
                    SButton("App info", false, modifier = Modifier.weight(1f)) { s.appInfo(top) }
                }
            }
            if (res.size > 1) {
                SLabel("Apps")
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    res.drop(1).take(16).chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { a -> Box(Modifier.weight(1f)) { AppIcon(a, onLaunch, onLong) } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph("search", ui.accent, 40.dp, 1.4f)
                T("Nothing called", 30.sp, display = true, center = true)
                T("“$qq”", 30.sp, display = true, italic = true, center = true, color = ui.accent)
                T(if (withHidden) "No app matches, hidden ones included." else "Hidden apps aren't searched. Check spelling, or look somewhere else.",
                    13.sp, color = muted(), center = true)
            }
        }
        if (hits.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SLabel("Settings")
            hits.forEachIndexed { i, h -> SRow(i + 1, "palette", "Settings › ${h.title}", "Matches “$qq”", i, onClick = { onOpenSettings(h.page) }) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SRow(1, "web", "Search the web for “$qq”", "Opens your browser", 0, onClick = { s.addRecent(qq); web() })
            SRow(2, "bag", "Search Play Store for “$qq”", "Find and install it", 1, onClick = { s.addRecent(qq); store() })
            if (res.isEmpty() && !withHidden && s.hidden.isNotEmpty())
                SRow(3, "eye", "Show hidden apps (${s.hidden.size})", "Search them too", 2, onClick = { withHidden = true })
        }
        if (s.recent.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SLabel("Recent")
            SChips(s.recent, null) { onRecent(it) }
        }
    }
}
