package dev.siftra

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

enum class SPage { HOME, LOOK, LAYOUT, SORTING, APPS, ABOUT }

val Group.glyph get() = when (this) {
    Group.MINE -> "sparkle"; Group.GOV -> "shield"; Group.INSPIRE -> "star"; Group.SAMSUNG -> "apps"; Group.GOOGLE -> "globe"
    Group.SOCIAL -> "chat"; Group.MEDIA -> "play"; Group.GAMES -> "bolt"; Group.FINANCE -> "coin"; Group.SHOPPING -> "bag"
    Group.TRAVEL -> "map"; Group.PRODUCTIVITY -> "note2"; Group.TOOLS -> "grid"; Group.OTHER -> "apps"; Group.SAMSUNG_EXTRA -> "layers"
}

private fun ago(ms: Long): String {
    if (ms <= 0) return "Hidden"
    val d = (System.currentTimeMillis() - ms) / 86_400_000L
    return when { d < 1 -> "Hidden today"; d < 2 -> "Hidden yesterday"; d < 7 -> "Hidden $d days ago"; d < 14 -> "Hidden last week"; else -> "Hidden ${d / 7} weeks ago" }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) SBack(onBack)
        Spacer(Modifier.weight(1f)); right()
    }
    STitle(title)
}

@Composable
fun SettingsScreen(s: Siftra, isHome: Boolean, start: SPage = SPage.HOME, onClose: () -> Unit, onSetHome: () -> Unit) {
    var page by remember { mutableStateOf(start) }
    BackHandler { if (page == SPage.HOME) onClose() else page = SPage.HOME }
    AnimatedContent(page, transitionSpec = {
        val fwd = targetState.ordinal > initialState.ordinal
        (slideInHorizontally(spring(dampingRatio = 1f, stiffness = 420f)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(220)) { if (fwd) -it / 4 else it / 4 } + fadeOut(tween(160)))
    }, label = "settings") { p ->
        when (p) {
            SPage.HOME -> SettingsHome(s, isHome, onClose, onSetHome) { page = it }
            SPage.LOOK -> LookPage(s) { page = SPage.HOME }
            SPage.LAYOUT -> LayoutPage(s) { page = SPage.HOME }
            SPage.SORTING -> SortingPage(s) { page = SPage.HOME }
            SPage.APPS -> AppsPage(s) { page = SPage.HOME }
            SPage.ABOUT -> AboutPage { page = SPage.HOME }
        }
    }
}

@Composable
private fun SettingsHome(s: Siftra, isHome: Boolean, onClose: () -> Unit, onSetHome: () -> Unit, go: (SPage) -> Unit) = Page {
    Header("Settings", null) {
        Box(Modifier.size(44.dp).glass(22.dp).clickable(onClick = onClose).semantics { contentDescription = "Close settings" },
            contentAlignment = Alignment.Center) { Glyph("x", LocalInk.current, 18.dp) }
    }
    SCard(Modifier.clickable(onClick = onSetHome), 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("home", 40.dp)
            Column(Modifier.weight(1f)) {
                T(if (isHome) "Siftra is your home screen" else "Make Siftra your home screen", 14.sp, weight = FontWeight.SemiBold)
                T(if (isHome) "Change default home" else "Opens Android's home app picker", 12.sp, color = muted())
            }
            Glyph("chev", muted(), 18.dp)
        }
    }
    val (rules, ai, manual) = remember(s.apps, s.groupOf) { s.sortStats() }
    val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 9.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        SRow(1, "palette", "Look", "${s.backdrop.title} · ${if (s.ribbed) "Ribbed" else "Frosted"} · ${s.clockStyle.title} clock", 0, onClick = { go(SPage.LOOK) })
        SRow(2, "layers", "Home layout", "${s.layout.title} · 5 layouts", 1, onClick = { go(SPage.LAYOUT) })
        SRow(3, "sparkle", "Sorting & Gemini", "${rules + ai + manual} apps · $ai sorted by Gemini", 2, onClick = { go(SPage.SORTING) })
        SRow(4, "apps", "Apps", "${s.hidden.size} hidden · ${s.manualMoves()} manual moves", 0, onClick = { go(SPage.APPS) })
        SRow(5, "info", "About", "Version ${BuildConfigLite.VERSION} · open source", 1, onClick = { go(SPage.ABOUT) })
    }
}

object BuildConfigLite { const val VERSION = "0.5.3" }

@Composable
private fun LookPage(s: Siftra, back: () -> Unit) = Page {
    Header("Look", back)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MiniHome(s.layout)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SLabel("Live preview")
            T("${s.layout.title} on ${s.backdrop.title}", 22.sp, display = true, lineHeight = 24.sp)
            T("Changes apply instantly. Each layout keeps its own look.", 12.sp, color = muted())
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { s.setPhoto(it) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SLabel("Backdrop")
        SChips(Backdrop.entries.map { it.title }, s.backdrop.title) { t ->
            val b = Backdrop.entries.first { it.title == t }
            if (b == Backdrop.PHOTO) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else s.setBackdropMode(b)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SLabel("Glass"); SChips(listOf("Frosted", "Ribbed"), if (s.ribbed) "Ribbed" else "Frosted") { s.setRibbedGlass(it == "Ribbed") } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SLabel("Clock · tap the clock on home to switch")
        SChips(ClockStyle.entries.map { it.title }, s.clockStyle.title) { t -> s.setClock(ClockStyle.entries.first { it.title == t }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SLabel("Font"); SChips(listOf("Classy · Instrument", "System"), if (s.classy) "Classy · Instrument" else "System") { s.setClassyFont(it != "System") } }
    SCard(pad = 14.dp) {
        SToggleRow("Material You colors", s.you) { s.setYouColors(it) }
        SToggleRow("Themed icons", s.themed) { s.setThemedIcons(it) }
        SToggleRow("Animated backdrop", s.motion) { s.setMotionOn(it) }
    }
}

@Composable
private fun LayoutPage(s: Siftra, back: () -> Unit) = Page {
    Header("Home layout", back)
    val ui = LocalUi.current
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(18.dp), maxItemsInEachRow = 3) {
        HomeLayout.entries.sortedBy { if (it == HomeLayout.INDEX) -1 else it.ordinal }.forEach { l ->
            val on = l == s.layout
            Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { s.setHomeLayout(l) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(if (on) Modifier.border(2.dp, ui.accent, RoundedCornerShape(20.dp)).padding(3.dp) else Modifier.padding(5.dp)) { MiniHome(l, 98.dp, 172.dp) }
                T(l.title + if (on) " ✓" else "", 13.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) ui.accent else Color.Unspecified)
            }
        }
    }
    SCard(pad = 14.dp) { T("Picking a layout also applies its matching backdrop, glass and clock. You can still change them in Look.", 12.5.sp, color = muted(), lineHeight = 18.sp) }
}

@Composable
private fun SortingPage(s: Siftra, back: () -> Unit) = Page {
    val scope = rememberCoroutineScope()
    val ui = LocalUi.current
    Header("Sorting", back)
    val (rules, ai, manual) = remember(s.apps, s.groupOf) { s.sortStats() }
    SCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { T("$rules", 46.sp, display = true, color = ui.accent, lineHeight = 44.sp); T("sorted on your phone by rules", 12.sp, color = muted()) }
            Column(horizontalAlignment = Alignment.End) { T("$ai", 46.sp, display = true, lineHeight = 44.sp); T("placed by Gemini", 12.sp, color = muted()) }
        }
        if (manual > 0) T("+ $manual you moved yourself", 12.sp, Modifier.padding(top = 8.dp), color = muted())
    }
    // Gemini key
    var key by remember { mutableStateOf(s.geminiKey) }
    LaunchedEffect(Unit) { if (s.keyState == null) s.validateKey() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T("Gemini API key · optional", 13.sp, color = muted())
        Row(Modifier.fillMaxWidth().height(52.dp).glass(14.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glyph("key", ui.accent, 18.dp)
            Box(Modifier.weight(1f)) {
                if (key.isEmpty()) T("Paste your key", 15.sp, color = muted())
                BasicTextField(key, { key = it; s.saveKey(it); s.keyState = null }, Modifier.fillMaxWidth().semantics { contentDescription = "Gemini API key" },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(LocalInk.current, 15.sp), cursorBrush = SolidColor(ui.accent))
            }
            when (s.keyState) {
                true -> T("Valid ✓", 12.sp, weight = FontWeight.SemiBold, color = ui.accent)
                false -> T("Invalid", 12.sp, weight = FontWeight.SemiBold, color = Color(0xFFFF6B6B))
                null -> if (key.isNotBlank()) T("Check", 12.sp, Modifier.clickable { scope.launch { s.validateKey() } }, FontWeight.SemiBold, ui.accent)
            }
        }
        val ctx = LocalContext.current
        T("Get a free key at aistudio.google.com ↗", 12.sp, Modifier.clickable {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }, color = ui.accent)
    }
    // group order
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SLabel("Group order · drag ⠿ to reorder")
        GroupOrderList(s)
    }
    SToggleRow("Show Samsung Extras on home", s.showExtras) { s.setExtras(it) }
    SButton("Re-sort all apps", true, "refresh") { scope.launch { s.organize(force = true) } }
}

/** Drag-to-reorder list of groups; commits the new order on release. */
@Composable
private fun GroupOrderList(s: Siftra) {
    var list by remember(s.groupOrder) { mutableStateOf(s.groupOrder) }
    var dragIdx by remember { mutableStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var itemH by remember { mutableFloatStateOf(1f) }
    val counts = remember(s.apps, s.groupOf, s.hidden) { s.sections(true).associate { it.first to it.second.size } }
    val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 8.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        list.forEachIndexed { i, g ->
            val dragging = i == dragIdx
            Box(Modifier.zIndex(if (dragging) 1f else 0f).onSizeChanged { if (i == 0) itemH = it.height.toFloat() }
                .graphicsLayer { translationY = if (dragging) dragDy else 0f; val k = if (dragging) 1.03f else 1f; scaleX = k; scaleY = k; shadowElevation = if (dragging) 18f else 0f }) {
                SRow(i + 1, g.glyph, g.title, "${counts[g] ?: 0} apps", i) {
                    Box(Modifier.size(40.dp).semantics { contentDescription = "Reorder ${g.title}" }.pointerInput(g) {
                        detectDragGestures(
                            onDragStart = { dragIdx = list.indexOf(g); dragDy = 0f },
                            onDragEnd = { s.saveGroupOrder(list); dragIdx = -1; dragDy = 0f },
                            onDragCancel = { dragIdx = -1; dragDy = 0f },
                        ) { ch, d ->
                            ch.consume()
                            dragDy += d.y
                            val step = itemH + gap.toPx()
                            while (dragDy > step / 2 && dragIdx < list.lastIndex) {
                                list = list.toMutableList().apply { add(dragIdx + 1, removeAt(dragIdx)) }; dragIdx++; dragDy -= step
                            }
                            while (dragDy < -step / 2 && dragIdx > 0) {
                                list = list.toMutableList().apply { add(dragIdx - 1, removeAt(dragIdx)) }; dragIdx--; dragDy += step
                            }
                        }
                    }, contentAlignment = Alignment.Center) { Glyph("drag", muted(), 20.dp, 2.4f) }
                }
            }
        }
    }
}

@Composable
private fun AppsPage(s: Siftra, back: () -> Unit) = Page {
    val ui = LocalUi.current
    Header("Apps", back)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SLabel("Hidden apps")
        val hid = s.hiddenApps()
        if (hid.isEmpty()) SCard(pad = 14.dp) { T("No hidden apps. Long-press any app and choose Hide.", 13.sp, color = muted()) }
        else {
            val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 8.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                hid.forEachIndexed { i, a ->
                    SRow(i + 1, (s.groupOf[a.key] ?: Group.OTHER).glyph, a.label, ago(s.hiddenAt(a.pkg)), i) {
                        T("Unhide", 13.sp, Modifier.clip(RoundedCornerShape(10.dp)).clickable { s.unhide(a.pkg) }.padding(8.dp), FontWeight.SemiBold, ui.accent)
                    }
                }
            }
        }
    }
    SCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("${s.manualMoves()} manual moves", 14.sp, weight = FontWeight.SemiBold)
                T("Apps you moved to another group", 12.sp, color = muted())
            }
            T("Reset", 13.sp, Modifier.clip(RoundedCornerShape(10.dp)).clickable { s.resetMoves() }.padding(8.dp), FontWeight.SemiBold, ui.accent)
        }
    }
}

@Composable
private fun AboutPage(back: () -> Unit) = Page {
    val ctx = LocalContext.current
    Header("About", back)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(92.dp).glass(28.dp), contentAlignment = Alignment.Center) { Glyph("layers", LocalUi.current.accent, 46.dp, 1.6f) }
        T("Siftra", 44.sp, display = true)
        T("Sift + Strata · version ${BuildConfigLite.VERSION}", 13.sp, color = muted())
    }
    val gap = if (LocalRowStyle.current == RowStyle.NUMBERED) 0.dp else 9.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        SRow(1, "github", "Source on GitHub", "github.com/xHypnotistx/Siftra ↗", 0, onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xHypnotistx/Siftra")))
        })
        SRow(2, "doc", "License", "MIT · free forever", 1)
        SRow(3, "note", "Fonts", "Instrument Serif & Sans · SIL OFL", 2)
        SRow(4, "heart", "Made by Mahmut", "Istanbul", 0)
    }
    T("No tracking. Most used is counted only on your phone.", 12.sp, Modifier.fillMaxWidth(), color = muted(), center = true)
}

/* ======================= first run ======================= */

@Composable
fun WelcomeScreen(s: Siftra, onSetHome: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val ui = LocalUi.current
    BackHandler { if (step > 0) step-- else onDone() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp).padding(top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i -> Box(Modifier.size(if (i == step) 28.dp else 10.dp, 10.dp).clip(RoundedCornerShape(5.dp))
                .background(if (i == step) ui.accent else LocalInk.current.copy(alpha = 0.18f))) }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
            (slideInHorizontally(spring(dampingRatio = 1f, stiffness = 380f)) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
        }, label = "welcome") { st ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (st) {
                    0 -> {
                        Column { T("Your apps,", 64.sp, display = true, lineHeight = 60.sp); T("sifted.", 64.sp, display = true, italic = true, color = ui.accent, lineHeight = 60.sp) }
                        T("Siftra sorts every app into calm layers (Government, Inspiration, Google, Social and more) automatically and privately on your phone.",
                            15.sp, color = muted(), lineHeight = 22.sp)
                        s.sections(false).take(4).forEachIndexed { i, (g, l) ->
                            val o = listOf(0, 10, 20, 10)[i].dp
                            Row(Modifier.padding(start = o, end = 20.dp - o).fillMaxWidth().height(46.dp).glass(16.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                T(g.title, 19.sp, Modifier.weight(1f), display = true); T("${l.size}", 12.sp, weight = FontWeight.SemiBold, color = ui.accent)
                            }
                        }
                    }
                    1 -> {
                        T("Pick your look", 44.sp, display = true)
                        T("You can change this anytime in Settings › Home layout.", 14.sp, color = muted())
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 3) {
                            HomeLayout.entries.sortedBy { if (it == HomeLayout.INDEX) -1 else it.ordinal }.forEach { l ->
                                val on = l == s.layout
                                Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { s.setHomeLayout(l) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(if (on) Modifier.border(2.dp, ui.accent, RoundedCornerShape(20.dp)).padding(3.dp) else Modifier.padding(5.dp)) { MiniHome(l, 92.dp, 160.dp) }
                                    T(l.title, 13.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) ui.accent else Color.Unspecified)
                                }
                            }
                        }
                    }
                    else -> {
                        T("Make it home", 52.sp, display = true)
                        T("Set Siftra as your default home screen to unlock app shortcuts and the Home button. Your Samsung home stays installed: switch back anytime in Settings › Apps › Default apps.",
                            15.sp, color = muted(), lineHeight = 22.sp)
                    }
                }
            }
        }
        if (step < 2) SButton("Next", true) { step++ }
        else { SButton("Make Siftra my home screen", true, "home") { onSetHome(); onDone() }; SButton("Try it first", false) { onDone() } }
    }
}
