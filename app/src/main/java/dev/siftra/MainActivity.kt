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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
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

fun Modifier.noRipple(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}

val springy = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)

class MainActivity : ComponentActivity(), WidgetConfigurator {
    private lateinit var s: Siftra
    private var configDone: ((Boolean) -> Unit)? = null

    /** Runs a widget's own setup screen (only Android may start it for us) and reports whether it was finished. */
    override fun configureWidget(id: Int, done: (Boolean) -> Unit) {
        configDone = done
        try { WidgetHost.get(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_WIDGET_SETUP, null) }
        catch (_: Exception) { configDone = null; done(true) }
    }

    @Deprecated("Widget setup results only arrive here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_WIDGET_SETUP) { configDone?.invoke(resultCode == RESULT_OK); configDone = null; return }
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
    }

    private companion object { const val REQ_WIDGET_SETUP = 0x5175 }
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
        try {
            val rm = getSystemService(RoleManager::class.java)
            if (!rm.isRoleHeld(RoleManager.ROLE_HOME)) startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
            else startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
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
    var settingsStart by remember { mutableStateOf(SPage.HOME) }
    var moveFor by remember { mutableStateOf<AppItem?>(null) }
    var editGroup by remember { mutableStateOf<Group?>(null) }
    var welcomed by remember { mutableStateOf(s.prefs.getBoolean("welcomed", false)) }
    var searchFocus by remember { mutableStateOf(false) }
    val act = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var isHome by remember { mutableStateOf(act.isDefaultHome()) }

    LaunchedEffect(s.reloadTick) { s.load() }
    val homePager = rememberPagerState(initialPage = 1) { 2 }          // page 0 = widgets, page 1 = home
    LaunchedEffect(homePressed) { homePager.animateScrollToPage(1) }
    WidgetHostLifecycle()
    val widgetCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { WidgetHost.cleanOrphans(widgetCtx, s.widgets.map { it.id }.toSet()) }
    LaunchedEffect(homePressed) { openGroup = null; drawer = false; menuFor = null; settings = false; moveFor = null; editGroup = null; s.progressVisible = false }
    LaunchedEffect(s.status) { if (s.status.endsWith("✓")) { delay(1800); s.status = "" } }

    // Real frosted glass: blur the wallpaper behind the whole window while a panel is open.
    val overlay = openGroup != null || drawer || settings || !welcomed
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

    val screen = remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val animated = s.motion && s.backdrop in setOf(Backdrop.AURORA, Backdrop.YOU, Backdrop.EMBER, Backdrop.MONO, Backdrop.LIME, Backdrop.PASTEL)
    val time = rememberMotionTime(animated, fps = 30)          // the blobs drift over ~48 s; 30 steps a second is plenty
    val sharedGlass = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val youCtx = androidx.compose.ui.platform.LocalContext.current
    val env = remember(s.backdrop, s.photo, s.ribbed) {
        fun c(id: Int) = Color(youCtx.getColor(id))
        GlassEnv(s.backdrop, s.photo, s.ribbed, time, screen, shared = sharedGlass,
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
            if (s.backdrop != Backdrop.WALLPAPER) Canvas(Modifier.fillMaxSize()) { drawBackdrop(env); recordSharedBackdrop(env) }
            // dim layer
            val sorting = s.progressVisible && s.progress != null
            val dim by animateFloatAsState(if (settings || !welcomed || drawer || sorting) 0.14f else if (overlay) 0.42f else 0f, tween(260), label = "dim")
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))

            val homeAlpha by animateFloatAsState(if (drawer || settings || !welcomed || sorting) 0f else if (openGroup != null) 0.3f else 1f, tween(240), label = "ha")
            val homeScale by animateFloatAsState(if (overlay) 0.95f else 1f, springy, label = "hs")
            val homeBlur by animateDpAsState(if (openGroup != null) 14.dp else 0.dp, tween(260), label = "hb")
            val light = s.backdrop == Backdrop.PASTEL
            val lime = s.layout == HomeLayout.INDEX
            val homeUi = remember(ui, light, lime) { if (lime) limeTheme(ui) else if (light) lightVariant(ctxT, ui) else ui }
            val ink = if (lime) LimeInk else if (light) Color(0xFF231D4F) else Color.White
            CompositionLocalProvider(LocalInk provides ink, LocalUi provides homeUi) {
            HorizontalPager(homePager, Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha; scaleX = homeScale; scaleY = homeScale }.blur(homeBlur)
                    .then(if (drawer || settings || !welcomed) Modifier.clearAndSetSemantics { } else Modifier),   // hidden home: inert
                beyondViewportPageCount = 1, userScrollEnabled = !overlay && menuFor == null) { page ->
            if (page == 0) WidgetsPage(s) { scope.launch { homePager.animateScrollToPage(1) } }
            else Home(
                s, sts, openGroup, Modifier,
                isHome = isHome,
                onGroup = { lastGroup = it; openGroup = it },
                onGroupLong = { editGroup = it },
                onLaunch = ::open, onLong = onLong,
                onDrawer = { searchFocus = it; drawer = true },
                onSettings = { settingsStart = SPage.HOME; settings = true },
                onOrganize = { scope.launch { s.organize(force = true, show = true) } },
                onSetHome = { askHome(); isHome = true },
                overlay = overlay,
            )
            }
            }
            BackHandler(homePager.currentPage == 0 && !overlay && menuFor == null) { scope.launch { homePager.animateScrollToPage(1) } }

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
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("${list.size} apps", 13.sp, alpha = 0.7f); GroupDot(list) }
                            Spacer(Modifier.height(14.dp))
                            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(list, key = { it.key }) { a -> AppIcon(a, onLaunch = ::open, onLong = onLong) }
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalInk provides ink, LocalUi provides homeUi, LocalRowStyle provides s.layout.rowStyle) {
                AnimatedVisibility(drawer, enter = slideInVertically(spring(dampingRatio = 1f, stiffness = 380f)) { it / 4 } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180))) {
                    AppDrawer(s, searchFocus, onClose = { drawer = false }, onLaunch = { a, r -> open(a, r) }, onLong = onLong,
                        onOrganize = { scope.launch { s.organize(force = true, show = true) } },
                        onOpenSettings = { p -> settingsStart = p; drawer = false; settings = true }, onGroupLong = { editGroup = it })
                }
                AnimatedVisibility(settings, enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 1f, stiffness = 380f)) { it / 10 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 12 }) {
                    SettingsScreen(s, isHome, settingsStart, onClose = { settings = false; settingsStart = SPage.HOME }, onSetHome = { askHome(); isHome = true },
                        onEditGroup = { editGroup = it })
                }
                AnimatedVisibility(!welcomed, enter = fadeIn(), exit = fadeOut(tween(260))) {
                    WelcomeScreen(s, onSetHome = { askHome(); isHome = true }) {
                        welcomed = true; s.prefs.edit().putBoolean("welcomed", true).apply()
                    }
                }
            }

            CompositionLocalProvider(LocalInk provides ink, LocalUi provides homeUi, LocalRowStyle provides s.layout.rowStyle) {
                AnimatedVisibility(s.progressVisible && s.progress != null, enter = fadeIn(), exit = fadeOut()) {
                    GeminiSortingScreen(s) { s.progressVisible = false }
                }
                AnimatedVisibility(menuFor != null, enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 1f, stiffness = 420f)) { it / 6 },
                    exit = fadeOut(tween(140))) {
                    var last by remember { mutableStateOf(menuFor) }; if (menuFor != null) last = menuFor
                    last?.let { a -> AppMenuSheet(s, a, onClose = { menuFor = null }) { app -> menuFor = null; moveFor = app } }
                }
                moveFor?.let { a -> MoveSheet(s, a) { moveFor = null } }
                editGroup?.let { g -> GroupEditSheet(s, g, onClose = { editGroup = null }) { settingsStart = SPage.SORTING; settings = true } }
            }

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
    overlay: Boolean = false, onGroupLong: (Group) -> Unit = {},
) {
    val ctx = LocalContext.current
    val swipeDownOn by rememberUpdatedState(s.swipeDown && !overlay)
    val swipeUp = Modifier.pointerInput(Unit) {
        var acc = 0f
        detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = {
            if (acc < -120f) onDrawer(false) else if (acc > 120f && swipeDownOn) Gestures.openNotifications(ctx)
        }) { _, dy -> acc += dy }
    }
    val pullToDrawer = remember {
        object : NestedScrollConnection {
            var up = 0f; var down = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) { down = 0f; up += available.y; if (up < -160f) { up = 0f; onDrawer(false) } }
                else if (available.y > 0 && source == NestedScrollSource.UserInput && swipeDownOn) {   // pulled down past the top
                    up = 0f; down += available.y; if (down > 160f) { down = 0f; Gestures.openNotifications(ctx) }
                } else { up = 0f; down = 0f }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity { down = 0f; return androidx.compose.ui.unit.Velocity.Zero }
        }
    }
    // double-tap empty space → lock (only once Siftra's lock service is on; children consume their own taps)
    val lockTap = if (s.doubleTapLock && !overlay) Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { LockService.lock() }) } else Modifier
    val sections = s.sections(s.showExtras)

    Column(modifier.fillMaxSize().then(lockTap).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
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

        // groups, in the chosen layout
        if (s.layout == HomeLayout.CARDS) {
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).nestedScroll(pullToDrawer),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(sections, key = { it.first.name }) { (g, list) ->
                CardSlot(sts, openGroup != g, g, list, Modifier.aspectRatio(1.12f).animateItem(), { onGroupLong(g) }) { onGroup(g) }
            }
        }
        } else GroupsArea(s, sts, openGroup, sections, onGroup, onLaunch, onLong, pullToDrawer, Modifier.weight(1f), overlay, onGroupLong)

        // shelf: Most used ⇄ Favorites
        Shelf(s, swipeUp, onLaunch, onLong)
        Box(swipeUp.fillMaxWidth().clickable { onDrawer(false) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text("︿  All apps", 12.sp, alpha = 0.7f)
        }
    }
}

@Composable
private fun CardSlot(sts: SharedTransitionScope, visible: Boolean, g: Group, list: List<AppItem>, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    Box(modifier) {
        with(sts) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
                GroupCard(g, list,
                    Modifier.sharedBounds(rememberSharedContentState("g_${g.name}"), this@AnimatedVisibility,
                        boundsTransform = { _, _ -> spring(dampingRatio = 0.84f, stiffness = 360f) },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()),
                    onClick = onClick, onLongClick = onLongClick)
            }
        }
    }
}

@Composable
private fun GroupCard(g: Group, list: List<AppItem>, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, springy, label = "p")
    Column(
        modifier.fillMaxSize().scale(sc).glass(26.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.title, 19.sp, display = true, modifier = Modifier.weight(1f), maxLines = 1)
            GroupDot(list)
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

/* ======================= pieces ======================= */

val CircleShapeLike = RoundedCornerShape(50)

@Composable
fun Text(t: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal,
                 alpha: Float = 1f, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, center: Boolean = false,
                 display: Boolean = false, color: Color = Color.Unspecified) =
    T(t, size, modifier, weight, color, alpha, display = display, maxLines = maxLines, center = center)

@Composable
private fun CircleBtn(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).glass(21.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(glyph, 18.sp, color = LocalUi.current.soft) }
}

/** App icon: original, or Material You themed (monochrome glyph on an accent disc). */
@Composable
fun AppGlyph(a: AppItem, size: Dp, dot: Boolean = true) {
    val ui = LocalUi.current
    val mono = a.mono
    Box(contentAlignment = Alignment.TopEnd) {
        if (ui.themedIcons && mono != null) {
            Box(Modifier.size(size).clip(CircleShapeLike).background(ui.container), contentAlignment = Alignment.Center) {
                Image(mono, a.label, Modifier.size(size), colorFilter = ColorFilter.tint(ui.onContainer))
            }
        } else Image(a.icon, a.label, Modifier.size(size))
        if (dot && Dots.show) {
            val has by remember(a.key) { derivedStateOf { Dots.has(a) } }
            if (has) AppDot(size, Modifier.offset(x = 2.dp, y = (-2).dp).semantics { contentDescription = "New notifications" })
        }
    }
}

@Composable
fun AppIcon(a: AppItem, onLaunch: (AppItem, Rect?) -> Unit, onLong: (AppItem) -> Unit) {
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
private fun MenuRow(t: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)) { Text(t, 15.sp) }
}
