@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package dev.siftra

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ======================= look ======================= */

private val txtShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 8f)

private fun Modifier.noRipple(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}

private val springy = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)

class MainActivity : ComponentActivity() {
    private lateinit var s: Siftra
    private val homePressed = mutableStateOf(0)

    override fun onCreate(b: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(b)
        s = Siftra(this)
        setContent { Root(s, homePressed.value, ::setBlur, ::askDefaultHome) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homePressed.value++   // Home pressed while on Siftra → close overlays
    }

    private fun setBlur(r: Int) {
        if (windowManager.isCrossWindowBlurEnabled) window.setBackgroundBlurRadius(r)
    }

    private fun askDefaultHome() {
        val rm = getSystemService(RoleManager::class.java)
        if (!rm.isRoleHeld(RoleManager.ROLE_HOME)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
        else startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
    }

    fun isDefaultHome() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
}

/* ======================= root ======================= */

@Composable
private fun Root(s: Siftra, homePressed: Int, setBlur: (Int) -> Unit, askHome: () -> Unit) {
    val scope = rememberCoroutineScope()
    var openGroup by remember { mutableStateOf<Group?>(null) }
    var lastGroup by remember { mutableStateOf(Group.OTHER) }
    var drawer by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppItem?>(null) }
    var settings by remember { mutableStateOf(false) }
    var searchFocus by remember { mutableStateOf(false) }
    val act = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var isHome by remember { mutableStateOf(act.isDefaultHome()) }

    LaunchedEffect(s.reloadTick) { s.load() }
    LaunchedEffect(homePressed) { openGroup = null; drawer = false; menuFor = null; settings = false }
    LaunchedEffect(s.status) { if (s.status.endsWith("✓")) { delay(1800); s.status = "" } }

    // Real frosted glass: blur the wallpaper behind the whole window while a panel is open.
    val overlay = openGroup != null || drawer || settings
    val blur = remember { Animatable(0f) }
    LaunchedEffect(overlay, s.backdrop) {
        blur.animateTo(if (overlay && s.backdrop == Backdrop.WALLPAPER) 36f else 0f, tween(if (overlay) 320 else 220)) { setBlur(value.toInt()) }
    }

    fun open(a: AppItem, r: Rect?) { s.launch(a, r) }
    val onLong: (AppItem) -> Unit = { menuFor = it }

    BackHandler(overlay || menuFor != null) {
        when {
            menuFor != null -> menuFor = null
            openGroup != null -> openGroup = null
            settings -> settings = false
            else -> drawer = false
        }
    }

    val time = remember { mutableFloatStateOf(0f) }
    val screen = remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val animated = s.motion && s.backdrop in setOf(Backdrop.AURORA, Backdrop.YOU, Backdrop.EMBER, Backdrop.MONO)
    if (animated) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val youCtx = androidx.compose.ui.platform.LocalContext.current
    val env = remember(s.backdrop, s.photo, s.ribbed) {
        fun c(id: Int) = Color(youCtx.getColor(id))
        GlassEnv(s.backdrop, s.photo, s.ribbed, time, screen,
            you = listOf(c(android.R.color.system_accent1_500), c(android.R.color.system_accent3_400),
                c(android.R.color.system_accent2_400), c(android.R.color.system_accent1_800)),
            youBase = c(android.R.color.system_neutral1_1000))
    }

    val ctxT = androidx.compose.ui.platform.LocalContext.current
    val ui = remember(s.you, s.classy, s.themed) { youTheme(ctxT, s.you, s.classy, s.themed) }
    CompositionLocalProvider(LocalGlass provides env, LocalUi provides ui) {
    SharedTransitionLayout(Modifier.fillMaxSize().onSizeChanged { screen.value = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }) {
        val sts = this
        Box(Modifier.fillMaxSize()) {
            if (s.backdrop != Backdrop.WALLPAPER) Canvas(Modifier.fillMaxSize()) { drawBackdrop(env) }
            // dim layer
            val dim by animateFloatAsState(if (overlay) 0.42f else 0f, tween(260), label = "dim")
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))

            val homeAlpha by animateFloatAsState(if (drawer || settings) 0f else if (openGroup != null) 0.3f else 1f, tween(240), label = "ha")
            val homeScale by animateFloatAsState(if (overlay) 0.95f else 1f, springy, label = "hs")
            val homeBlur by animateDpAsState(if (openGroup != null) 14.dp else 0.dp, tween(260), label = "hb")
            Home(
                s, sts, openGroup, Modifier.graphicsLayer { alpha = homeAlpha; scaleX = homeScale; scaleY = homeScale }.blur(homeBlur),
                isHome = isHome,
                onGroup = { lastGroup = it; openGroup = it },
                onLaunch = ::open, onLong = onLong,
                onDrawer = { searchFocus = it; drawer = true },
                onSettings = { settings = true },
                onOrganize = { scope.launch { s.organize(force = true) } },
                onSetHome = { askHome(); isHome = true },
            )

            // group panel (container-transforms out of its card)
            with(sts) {
                AnimatedVisibility(openGroup != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(220))) {
                    val g = lastGroup
                    val list = s.sections(true).firstOrNull { it.first == g }?.second.orEmpty()
                    Box(Modifier.fillMaxSize().noRipple { openGroup = null }, contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.fillMaxWidth(0.9f)
                                .sharedBounds(rememberSharedContentState("g_${g.name}"), this@AnimatedVisibility,
                                    boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds())
                                .glass(32.dp, dark = true)
                                .noRipple { }
                                .padding(20.dp)
                        ) {
                            Text(g.title, 30.sp, display = true)
                            Text("${list.size} apps", 13.sp, alpha = 0.7f)
                            Spacer(Modifier.height(14.dp))
                            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(list, key = { it.key }) { a -> AppIcon(a, onLaunch = ::open, onLong = onLong) }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(drawer, enter = slideInVertically(spring(0.86f, 380f)) { it / 4 } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180))) {
                Drawer(s, searchFocus, onClose = { drawer = false }, onLaunch = { a, r -> open(a, r) }, onLong = onLong,
                    onOrganize = { scope.launch { s.organize(force = true) } })
            }

            AnimatedVisibility(settings, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
                SettingsPanel(s, onClose = { settings = false }, onSetHome = askHome,
                    onReset = { s.resetMoves() })
            }

            menuFor?.let { a -> AppMenu(s, a, onClose = { menuFor = null }) }

            // status toast
            AnimatedVisibility(s.status.isNotEmpty(), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
                enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
                Box(Modifier.glass(18.dp, dark = true).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(s.status, 13.sp) }
            }
        }
    }
    }
}

/* ======================= home ======================= */

@Composable
private fun Home(
    s: Siftra, sts: SharedTransitionScope, openGroup: Group?, modifier: Modifier, isHome: Boolean,
    onGroup: (Group) -> Unit, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit,
    onDrawer: (Boolean) -> Unit, onSettings: () -> Unit, onOrganize: () -> Unit, onSetHome: () -> Unit,
) {
    val swipeUp = Modifier.pointerInput(Unit) {
        var acc = 0f
        detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc < -120f) onDrawer(false) }) { _, dy -> acc += dy }
    }
    val pullToDrawer = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) { acc += available.y; if (acc < -160f) { acc = 0f; onDrawer(false) } } else acc = 0f
                return Offset.Zero
            }
        }
    }
    val sections = s.sections(s.showExtras)

    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        // header: clock + actions
        Row(swipeUp.padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ClockView(s.clockStyle, Modifier.weight(1f).noRipple { s.nextClock() })
            CircleBtn("⟲", onOrganize); Spacer(Modifier.width(8.dp)); CircleBtn("⋯", onSettings)
        }
        Row(Modifier.fillMaxWidth().glass(22.dp).clickable { onDrawer(true) }.padding(horizontal = 18.dp, vertical = 13.dp)) {
            Text("⌕  Search apps", 15.sp, alpha = 0.75f)
        }
        if (!isHome) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().glass(18.dp, true).clickable(onClick = onSetHome).padding(14.dp)) {
                Text("Make Siftra your home screen  →", 14.sp, FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(14.dp))

        // group cards
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).nestedScroll(pullToDrawer),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(sections, key = { it.first.name }) { (g, list) ->
                CardSlot(sts, openGroup != g, g, list, Modifier.aspectRatio(1.12f).animateItem()) { onGroup(g) }
            }
        }

        // shelf: Most used ⇄ Favorites
        Shelf(s, swipeUp, onLaunch, onLong)
        Box(swipeUp.fillMaxWidth().clickable { onDrawer(false) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text("︿  All apps", 12.sp, alpha = 0.7f)
        }
    }
}

@Composable
private fun CardSlot(sts: SharedTransitionScope, visible: Boolean, g: Group, list: List<AppItem>, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        with(sts) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
                GroupCard(g, list,
                    Modifier.sharedBounds(rememberSharedContentState("g_${g.name}"), this@AnimatedVisibility,
                        boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()),
                    onClick = onClick)
            }
        }
    }
}

@Composable
private fun GroupCard(g: Group, list: List<AppItem>, modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, springy, label = "p")
    Column(
        modifier.fillMaxSize().scale(sc).glass(26.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, 19.sp, display = true, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${list.size}", 12.sp, FontWeight.SemiBold, color = LocalUi.current.accent)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { AppGlyph(it, 44.dp) }
                }
            }
        }
    }
}

@Composable
private fun Shelf(s: Siftra, swipeUp: Modifier, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(swipeUp.fillMaxWidth().glass(28.dp).padding(top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Most used", "Favorites").forEachIndexed { i, t ->
                val sel = 1f - kotlin.math.abs(pager.currentPage + pager.currentPageOffsetFraction - i).coerceIn(0f, 1f)
                Text(t, 13.sp, FontWeight.SemiBold, alpha = 0.45f + 0.55f * sel,
                    modifier = Modifier.noRipple { scope.launch { pager.animateScrollToPage(i) } }.padding(end = 14.dp))
            }
            Spacer(Modifier.weight(1f))
            repeat(2) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 16.dp else 6.dp, label = "dot")
                Box(Modifier.padding(start = 4.dp).size(w, 6.dp).clip(CircleShapeLike).background(if (pager.currentPage == i) LocalUi.current.accent else Color.White.copy(alpha = 0.35f)))
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth().height(92.dp).padding(top = 8.dp)) { page ->
            val list = if (page == 0) s.mostUsed(5) else s.favoriteApps().take(5)
            if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (page == 0) "Apps you open most will appear here" else "Long-press any app → ★ Favorite", 13.sp, alpha = 0.65f)
            } else Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                list.forEach { a -> Box(Modifier.width(64.dp)) { AppIcon(a, onLaunch, onLong) } }
            }
        }
    }
}

/* ======================= drawer ======================= */

@Composable
private fun Drawer(s: Siftra, focusSearch: Boolean, onClose: () -> Unit, onLaunch: (AppItem, Rect?) -> Unit,
                   onLong: (AppItem) -> Unit, onOrganize: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val sections = s.sections(true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (focusSearch) { delay(150); fr.requestFocus() } }

    // index of each section header inside the LazyColumn
    val headerIndex = remember(sections) {
        var i = 0
        sections.associate { (g, l) -> (g to i).also { i += 1 + (l.size + 3) / 4 } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.padding(horizontal = 18.dp).padding(top = 14.dp).pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc > 120f) onClose() }) { _, dy -> acc += dy }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).glass(22.dp, true).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (q.isEmpty()) Text("⌕  Search apps", 15.sp, alpha = 0.6f)
                BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true,
                    textStyle = TextStyle(Color.White, 15.sp), cursorBrush = SolidColor(Color.White))
            }
            Spacer(Modifier.width(8.dp)); CircleBtn("⟲", onOrganize)
        }
        if (q.isBlank()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections, key = { it.first.name }) { (g, _) ->
                    Box(Modifier.glass(16.dp).clickable { scope.launch { listState.animateScrollToItem(headerIndex[g] ?: 0) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp)) { Text(g.title, 13.sp) }
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp)) {
                sections.forEach { (g, list) ->
                    item(key = "h_${g.name}") {
                        Row(Modifier.padding(start = 6.dp, top = 16.dp, bottom = 10.dp).animateItem(), verticalAlignment = Alignment.Bottom) {
                            Text(g.title, 24.sp, display = true)
                            Spacer(Modifier.width(8.dp)); Text("${list.size}", 12.sp, alpha = 0.55f)
                        }
                    }
                    items(list.chunked(4), key = { row -> "r_${g.name}_${row.first().key}" }) { row ->
                        Row(Modifier.fillMaxWidth().glass(20.dp).padding(vertical = 10.dp).animateItem()) {
                            row.forEach { a -> Box(Modifier.weight(1f)) { AppIcon(a, onLaunch, onLong) } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            val res = s.visible.filter { it.label.contains(q.trim(), ignoreCase = true) }
            LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(res, key = { it.key }) { a -> AppIcon(a, onLaunch, onLong) }
            }
            if (res.isEmpty()) Text("No apps match “$q”", 14.sp, alpha = 0.6f, modifier = Modifier.padding(24.dp))
        }
    }
}

/* ======================= pieces ======================= */

private val CircleShapeLike = RoundedCornerShape(50)

@Composable
private fun Text(t: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal,
                 alpha: Float = 1f, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, center: Boolean = false,
                 display: Boolean = false, color: Color = Color.White) =
    T(t, size, modifier, weight, color, alpha, display = display, maxLines = maxLines, center = center)

@Composable
private fun CircleBtn(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).glass(21.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(glyph, 18.sp, color = LocalUi.current.soft) }
}

/** App icon: original, or Material You themed (monochrome glyph on an accent disc). */
@Composable
private fun AppGlyph(a: AppItem, size: Dp) {
    val ui = LocalUi.current
    val mono = a.mono
    if (ui.themedIcons && mono != null) {
        Box(Modifier.size(size).clip(CircleShapeLike).background(ui.container), contentAlignment = Alignment.Center) {
            Image(mono, a.label, Modifier.size(size), colorFilter = ColorFilter.tint(ui.onContainer))
        }
    } else Image(a.icon, a.label, Modifier.size(size))
}

@Composable
private fun AppIcon(a: AppItem, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { c -> val r = c.boundsInWindow(); bounds = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()) }
            .combinedClickable(remember { MutableInteractionSource() }, null,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong(a) },
                onClick = { onLaunch(a, bounds) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppGlyph(a, 52.dp)
        Spacer(Modifier.height(5.dp))
        Text(a.label, 11.5.sp, maxLines = 1, center = true, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun AppMenu(s: Siftra, a: AppItem, onClose: () -> Unit) {
    var moving by remember { mutableStateOf(false) }
    val act = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).noRipple(onClose), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.82f).glass(28.dp, dark = true).noRipple { }.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(a, 44.dp); Spacer(Modifier.width(12.dp))
                Column {
                    Text(a.label, 17.sp, FontWeight.SemiBold, maxLines = 1)
                    Text(s.groupOf[a.key]?.title ?: "", 12.sp, alpha = 0.65f)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!moving) {
                MenuRow(if (s.isFav(a)) "★  Remove from favorites" else "☆  Add to favorites") { s.toggleFav(a); onClose() }
                MenuRow("⇄  Move to group…") { moving = true }
                MenuRow("◌  Hide") { s.hide(a); onClose() }
                MenuRow("ⓘ  App info") { s.appInfo(a); onClose() }
                MenuRow("✕  Uninstall") {
                    act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))); onClose()
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(Group.entries.toList()) { g -> MenuRow((if (s.groupOf[a.key] == g) "●  " else "○  ") + g.title) { s.move(a, g); onClose() } }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { t ->
            val on = t == selected
            val ui = LocalUi.current
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) ui.container else Color(0x14FFFFFF))
                .border(1.dp, if (on) ui.accent else Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .clickable { onPick(t) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(t, 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) ui.onContainer else Color.White)
            }
        }
    }
}

@Composable
private fun MenuRow(t: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)) { Text(t, 15.sp) }
}

@Composable
private fun SettingsPanel(s: Siftra, onClose: () -> Unit, onSetHome: () -> Unit, onReset: () -> Unit) {
    var key by remember { mutableStateOf(s.geminiKey) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().noRipple { s.saveKey(key); onClose() }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.88f).heightIn(max = 640.dp).glass(30.dp, dark = true).noRipple { }
            .verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Siftra", 32.sp, display = true)
            Text("Open-source launcher that sifts your apps into strata", 12.5.sp, alpha = 0.7f)
            Spacer(Modifier.height(16.dp))
            Text("Gemini key (sorts apps the rules can't place)", 13.sp, alpha = 0.8f)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().glass(14.dp).padding(12.dp)) {
                if (key.isEmpty()) Text("Paste API key — optional", 14.sp, alpha = 0.5f)
                BasicTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(Color.White, 14.sp), cursorBrush = SolidColor(Color.White))
            }
            Text("Get a free key at aistudio.google.com/apikey ↗", 12.sp, alpha = 0.75f,
                modifier = Modifier.padding(top = 6.dp).clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                })
            Spacer(Modifier.height(10.dp))
            Text("Backdrop", 13.sp, alpha = 0.8f)
            val pick = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { s.setPhoto(it) } }
            ChipRow(Backdrop.entries.map { it.title }, s.backdrop.title) { t ->
                val b = Backdrop.entries.first { it.title == t }
                if (b == Backdrop.PHOTO) pick.launch(androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                else s.setBackdropMode(b)
            }
            Text("Glass", 13.sp, alpha = 0.8f, modifier = Modifier.padding(top = 10.dp))
            ChipRow(listOf("Frosted", "Ribbed"), if (s.ribbed) "Ribbed" else "Frosted") { s.setRibbedGlass(it == "Ribbed") }
            Spacer(Modifier.height(6.dp))
            MenuRow((if (s.motion) "☑" else "☐") + "  Animated backdrop") { s.setMotionOn(!s.motion) }
            Text("Clock  ·  tip: tap the clock to switch", 13.sp, alpha = 0.8f, modifier = Modifier.padding(top = 8.dp))
            ChipRow(ClockStyle.entries.map { it.title }, s.clockStyle.title) { t -> s.setClock(ClockStyle.entries.first { it.title == t }) }
            Text("Font", 13.sp, alpha = 0.8f, modifier = Modifier.padding(top = 10.dp))
            ChipRow(listOf("Classy", "System"), if (s.classy) "Classy" else "System") { s.setClassyFont(it == "Classy") }
            Spacer(Modifier.height(6.dp))
            MenuRow((if (s.you) "☑" else "☐") + "  Material You colors") { s.setYouColors(!s.you) }
            MenuRow((if (s.themed) "☑" else "☐") + "  Themed icons (Material You)") { s.setThemedIcons(!s.themed) }
            MenuRow((if (s.showExtras) "☑" else "☐") + "  Show Samsung Extras on home") { s.setExtras(!s.showExtras) }
            MenuRow("↺  Reset my manual moves") { onReset() }
            MenuRow("◌  Unhide all apps (${s.hidden.size})") { s.unhideAll() }
            MenuRow("⌂  Set as default home") { onSetHome() }
        }
    }
}
