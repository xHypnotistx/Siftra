package dev.siftra

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiStressTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val shots = File("build/stress-shots").apply { mkdirs() }

    private fun start(layout: HomeLayout, motion: Boolean = false, extra: (android.content.SharedPreferences.Editor) -> Unit = {}) {
        TestHooks.apps = { CATALOG.map { it.first } + filler(60) }
        TestHooks.freezeMotion = true
        val e = ctx.getSharedPreferences("siftra", 0).edit().clear()
        e.putBoolean("motion", motion).putBoolean("welcomed", true); extra(e); e.commit()
        Siftra(ctx).setHomeLayout(layout)                       // apply the layout preset the same way the UI does
        if (!motion) ctx.getSharedPreferences("siftra", 0).edit().putBoolean("motion", false).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val shown = { rule.onAllNodes(hasText("Made by Mahmut", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
        // Rarely, late in a long Robolectric run, the first frames after launch wait on the fake clock (seen only with many
        // tests sharing one JVM, never in a single test or on a phone). Advancing the main looper's clock unsticks it.
        if (runCatching { rule.waitUntil(8_000) { shown() } }.isFailure) {
            println("START NUDGE $layout")
            val end = System.currentTimeMillis() + 6_000
            while (!shown() && System.currentTimeMillis() < end) org.robolectric.shadows.ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS)
            println("START NUDGE $layout → ${if (shown()) "rescued by advancing the looper clock" else "still stuck"}")
        }
        try { rule.waitUntil(12_000) { shown() } }
        catch (t: Throwable) {
            shot("start-timeout-$layout")
            println("START TIMEOUT layout=$layout apps=${runCatching { model().apps.size }.getOrNull()} sections=${runCatching { model().sections(true).size }.getOrNull()}")
            println(runCatching { rule.onAllNodes(isRoot())[0].printToString().lines().filter { "Text" in it }.take(25).joinToString("\n") }.getOrElse { "tree: $it" })
            throw t
        }
    }

    @After fun stop() {
        try { if (::scenario.isInitialized) scenario.close() } catch (t: Throwable) { println("teardown (Robolectric surface): ${t.javaClass.simpleName}") }
        TestHooks.apps = null; TestHooks.freezeMotion = false
    }

    private fun back() { scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun model(): Siftra { var s: Siftra? = null; scenario.onActivity { a ->
        s = MainActivity::class.java.getDeclaredField("s").apply { isAccessible = true }.get(a) as Siftra }; return s!! }
    private fun shot(name: String) {
        rule.waitForIdle()
        try {
            scenario.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (t: Throwable) { println("screenshot $name skipped: ${t.message}") }
    }
    private fun exists(t: String) = rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()

    /* ---------- every layout: render, open a group, close it ---------- */

    private fun groupRoundTrip(layout: HomeLayout) {
        start(layout)
        shot("${layout.name}-home")
        rule.onAllNodes(hasText("Made by Mahmut", ignoreCase = true))[0].performClick()
        rule.waitUntil(5_000) { exists("QuickGrab") }
        shot("${layout.name}-group")
        if (layout != HomeLayout.INDEX) {
            back()
            rule.waitUntil(5_000) { !exists("QuickGrab") }
        } else {
            rule.onAllNodes(hasText("Made by Mahmut", ignoreCase = true))[0].performClick()       // Index folds back on second tap
            rule.waitUntil(5_000) { !exists("QuickGrab") }
        }
    }

    @Test fun cards() = groupRoundTrip(HomeLayout.CARDS)
    @Test fun strata() = groupRoundTrip(HomeLayout.STRATA)
    @Test fun monolith() = groupRoundTrip(HomeLayout.MONOLITH)
    @Test fun orbit() = groupRoundTrip(HomeLayout.ORBIT)
    @Test fun index() = groupRoundTrip(HomeLayout.INDEX)

    /* ---------- Index Lime: double-tap, then drag down pulls a group open ---------- */

    @Test fun indexDoubleTapDrag() {
        start(HomeLayout.INDEX)
        val row = rule.onNode(hasText("Government") and hasClickAction())
        row.performTouchInput {
            click(center); advanceEventTime(120)                 // double-tap…
            down(center)                                          // …and keep the finger down
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)); advanceEventTime(16) }
            up()
        }
        rule.waitUntil(5_000) { exists("e-Devlet") }
        val s = model()
        assertTrue("drag hint should be learned", s.prefs.getBoolean("dragLearned", false))
        // short drag snaps back (stays closed for another group)
        rule.onNode(hasText("Inspiration") and hasClickAction()).performTouchInput {
            click(center); advanceEventTime(120); down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 20f)); up()
        }
        rule.waitForIdle()
        assertFalse(exists("Pinterest"))
        // back folds the open group
        back(); rule.waitUntil(5_000) { !exists("e-Devlet") }
        shot("INDEX-lime-after-drag")
    }

    /* ---------- drawer, search, chips, long-press menu ---------- */

    @Test fun drawerSearchFavoriteHideMove() {
        try { drawerBody() } catch (t: Throwable) {
            println("DRAWER BODY FAILED: $t"); t.stackTrace.filter { it.className.startsWith("dev.siftra") }.take(4).forEach { println("   at $it") }
            throw t
        }
    }

    private fun drawerBody() {
        start(HomeLayout.CARDS)
        rule.onNodeWithText("︿  All apps").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        shot("drawer")
        val before = model().counts.values.sum()
        rule.onAllNodes(isRoot())[0].performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 12f)) }   // empty handle area
        rule.waitForIdle()
        assertEquals("tap fell through the drawer", before, model().counts.values.sum())
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())   // drawer still open
        // search
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("insta")
        rule.waitForIdle()
        assertTrue(exists("Instagram")); assertFalse(exists("Akbank"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("zzzz")
        assertTrue(exists("Nothing called") && exists("“instazzzz”"))
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        // chips jump
        listOf("Finance", "Travel", "Government", "Samsung Extras").forEach { t ->
            rule.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange))
                .performScrollToNode(hasText(t))
            rule.onAllNodesWithText(t)[0].performClick(); rule.waitForIdle()
        }
        // favorite via long-press
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Instagram")
        rule.onAllNodes(hasText("Instagram") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.onNodeWithText("Favorite").performClick()
        // hide
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Akbank")
        rule.onAllNodes(hasText("Akbank") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.onNodeWithText("Hide").performClick()
        rule.waitForIdle()
        assertTrue(exists("“Akbank”"))   // hidden → "Nothing called “Akbank”"
        // move
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Waze")
        rule.onAllNodes(hasText("Waze") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.onNodeWithText("Move").performClick()
        rule.waitUntil(5_000) { exists("Waze to…") }
        shot("move-sheet")
        runCatching { rule.onAllNodesWithText("Games").onLast().performScrollTo() }; rule.onAllNodesWithText("Games").onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        val s = model()
        assertEquals(Group.GAMES, s.groupOf[s.apps.first { it.label == "Waze" }.key])
        assertTrue(s.favorites.any { it.startsWith("com.instagram.android") })
        assertTrue("com.akbank.android.apps.akbank_direkt" in s.hidden)
        back()
        // favorite shows on the shelf's second page
        rule.onNodeWithText("Favorites").performClick()
        rule.waitUntil(5_000) { exists("Instagram") }
        println("drawer test body: all checks passed")
    }

    /* ---------- drawer: A–Z view, Enter opens top hit, recents, settings from search ---------- */

    @Test fun drawerAzEnterRecentSettings() {
        start(HomeLayout.INDEX)
        rule.onNodeWithText("︿  All apps").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        // A–Z view + rail jump
        rule.onNodeWithText("A–Z").performClick(); rule.waitForIdle()
        assertEquals("AZ", model().prefs.getString("drawerView", ""))
        rule.onNodeWithContentDescription("Letter index").performTouchInput { down(bottomCenter.copy(y = height * 0.9f)); up() }
        rule.waitForIdle()
        shot("drawer-az")
        rule.onNodeWithText("Groups").performClick(); rule.waitForIdle()
        // Enter opens the top hit and saves the query as a recent search
        val s = model()
        val field = rule.onAllNodes(hasSetTextAction())[0]
        field.performTextInput("spot")
        rule.waitUntil(3_000) { exists("Top hit") || exists("TOP HIT") }
        shot("search-results")
        val before = s.counts.filterKeys { it.startsWith("com.spotify.music") }.values.sum()
        field.performImeAction()
        rule.waitForIdle()
        assertEquals(before + 1, s.counts.filterKeys { it.startsWith("com.spotify.music") }.values.sum())
        assertEquals("spot", s.recent.firstOrNull())
        // settings match opens Settings on that page
        field.performTextClearance(); field.performTextInput("gemini")
        rule.waitUntil(3_000) { exists("Settings › Sorting & Gemini") }
        rule.onNodeWithText("Settings › Sorting & Gemini", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(5_000) { exists("Re-sort all apps") || exists("RE-SORT ALL APPS") }
        shot("search-to-settings")
    }

    /* ---------- every settings page and control, then hammer layouts ---------- */

    @Test fun everySettingAndRapidSwitching() {
        start(HomeLayout.CARDS)
        fun tap(t: String) { rule.onAllNodesWithText(t)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
        fun open(row: String) { tap(row); rule.waitForIdle() }
        fun backBtn() { rule.onAllNodesWithContentDescription("Back")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
        rule.onNodeWithText("⋯").performClick()
        rule.waitForIdle()
        if (!exists("Sorting & Gemini")) println("TREE:\n" + rule.onAllNodes(isRoot())[0].printToString(maxDepth = 6).take(3000))
        rule.waitUntil(5_000) { exists("Sorting & Gemini") }
        shot("settings-home")
        // Look
        open("Look"); rule.waitUntil(5_000) { exists("Live preview") || exists("LIVE PREVIEW") }
        Backdrop.entries.filter { it != Backdrop.PHOTO }.forEach { tap(it.title) }
        listOf("Ribbed", "Frosted").forEach { tap(it) }
        ClockStyle.entries.forEach { tap(it.title) }
        listOf("System", "Classy · Instrument").forEach { tap(it) }
        listOf("Material You colors", "Themed icons").forEach { d -> repeat(2) { rule.onNodeWithContentDescription(d).performClick(); rule.waitForIdle() } }
        shot("settings-look"); backBtn()
        // Home layout: every tile
        open("Home layout")
        HomeLayout.entries.forEach { l -> rule.onAllNodes(hasText(l.title, substring = true))[0].performClick(); rule.waitForIdle() }
        shot("settings-layout"); backBtn()
        // Sorting: extras switch, drag-reorder Google to the top area, re-sort
        open("Sorting & Gemini"); rule.waitUntil(5_000) { exists("Re-sort all apps") || exists("RE-SORT ALL APPS") }
        val s = model()
        val before = s.groupOrder.indexOf(Group.GOOGLE)
        rule.onNodeWithContentDescription("Reorder Google").performScrollTo().performTouchInput { down(center); repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, -40f)); advanceEventTime(16) }; up() }
        rule.waitForIdle()
        assertTrue("Google should move up (was $before, now ${s.groupOrder.indexOf(Group.GOOGLE)})", s.groupOrder.indexOf(Group.GOOGLE) < before)
        repeat(2) { rule.onNodeWithContentDescription("Show Samsung Extras on home").performScrollTo().performClick(); rule.waitForIdle() }
        shot("settings-sorting"); backBtn()
        // Apps & About
        open("Apps"); shot("settings-apps"); backBtn()
        open("About"); rule.waitUntil(5_000) { exists("Source on GitHub") || exists("SOURCE ON GITHUB") }; backBtn()
        back()   // system back closes settings
        rule.waitUntil(5_000) { !exists("Sorting & Gemini") }
        // rapid: 60 layout flips
        val r = java.util.Random(3)
        repeat(60) { scenario.onActivity { s.setHomeLayout(HomeLayout.entries[r.nextInt(HomeLayout.entries.size)]) }; rule.waitForIdle() }
        repeat(ClockStyle.entries.size * 2) { scenario.onActivity { s.nextClock() }; rule.waitForIdle() }
    }

    /* ---------- first run ---------- */

    @Test fun welcomeFlow() {
        start(HomeLayout.STRATA) { it.putBoolean("welcomed", false) }
        rule.waitUntil(5_000) { exists("sifted.") }
        shot("welcome-1")
        rule.onNodeWithText("Next").performClick(); rule.waitUntil(5_000) { exists("Pick your look") }
        rule.onAllNodes(hasText("Monolith"))[0].performClick(); rule.waitForIdle()
        assertEquals(HomeLayout.MONOLITH, model().layout)
        rule.onNodeWithText("NEXT").performClick(); rule.waitUntil(5_000) { exists("Make it home") }   // Monolith style = caps buttons
        rule.onNodeWithText("TRY IT FIRST").performClick()
        rule.waitUntil(5_000) { !exists("Make it home") }
        assertTrue(model().prefs.getBoolean("welcomed", false))
    }

    /* ---------- animated backdrop running with real frames ---------- */

    @Test fun animatedBackdropFrames() {
        rule.mainClock.autoAdvance = false
        TestHooks.apps = { CATALOG.map { it.first } }; TestHooks.freezeMotion = false
        ctx.getSharedPreferences("siftra", 0).edit().clear().putBoolean("motion", true).putBoolean("welcomed", true).commit()
        Siftra(ctx).setHomeLayout(HomeLayout.STRATA)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        repeat(240) { rule.mainClock.advanceTimeByFrame() }        // ~4 s of frames
        rule.mainClock.advanceTimeBy(30_000)
    }

    /* ---------- monkey: hundreds of random UI actions per layout ---------- */

    @Test fun monkey() {
        val r = java.util.Random(42)
        var done = 0; var skipped = 0; var last = ""
        HomeLayout.entries.forEach { layout ->
            start(layout)
            repeat(120) {
                try {
                    val op = r.nextInt(9); last = "op $op"
                    when (op) {
                        0 -> rule.onAllNodesWithText(Group.entries[r.nextInt(Group.entries.size)].title)[0].performClick()
                        1 -> back()
                        2 -> rule.onNodeWithText("︿  All apps").performClick()
                        3 -> rule.onNodeWithText("⋯").performClick()
                        4 -> rule.onNodeWithText("⟲").performClick()
                        5 -> rule.onAllNodesWithText(CATALOG[r.nextInt(CATALOG.size)].first.label)[0].performTouchInput { longClick() }
                        6 -> {   // any clickable on screen, except the always-animating backdrop switch (never idles by design)
                            val m = hasClickAction() and !hasText("Animated backdrop", substring = true)
                            val n = rule.onAllNodes(m).fetchSemanticsNodes()
                            val pick = r.nextInt(n.size)
                            last = "click " + (n[pick].config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString() ?: "#$pick")
                            rule.onAllNodes(m)[pick].performClick()
                        }
                        7 -> rule.onAllNodes(isRoot())[0].performTouchInput { swipeUp() }
                        8 -> rule.onAllNodes(isRoot())[0].performTouchInput { if (r.nextBoolean()) swipeLeft() else swipeRight() }
                    }
                    try { rule.waitForIdle() } catch (e: Throwable) { println("NOT IDLE after: $last (layout $layout)"); throw e }
                    done++
                } catch (e: AssertionError) { skipped++ }                 // target not on screen right now: fine
                  catch (e: IllegalArgumentException) { skipped++ }       // index out of range on an empty list
                  catch (e: IndexOutOfBoundsException) { skipped++ }
            }
            shot("monkey-${layout.name}")
            scenario.close()
        }
        println("monkey: $done actions ran, $skipped had no target")
        assertTrue(done > 200)
    }

    /* ---------- stage 3: new group from Move, rename/delete via Sorting, Favorites, Most used, Gemini progress ---------- */

    @Test fun groupsFavoritesMostUsedProgress() {
        start(HomeLayout.CARDS)
        fun click(t: String) { runCatching { rule.onAllNodesWithText(t).onLast().performScrollTo() }; rule.onAllNodesWithText(t).onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
        val s = model()
        val waze = s.apps.first { it.label == "Waze" }
        val auto = s.groupOf[waze.key]
        // long-press menu → Move → New group…
        scenario.onActivity { }; rule.onNodeWithText("︿  All apps").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Waze")
        rule.onAllNodes(hasText("Waze") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.waitUntil(5_000) { exists("opens this week", ) || rule.onAllNodes(hasText("opens this week", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("app-menu")
        click("Move"); click("New group…")
        rule.onNodeWithContentDescription("New group name").performTextInput("Study")
        click("Create")
        val g = s.groupOf[waze.key]!!
        assertTrue(g.custom); assertEquals("Study", g.title); assertTrue(g in s.groupOrder)
        back(); rule.waitForIdle()
        // Settings › Sorting: tap the custom group → rename, then delete
        rule.onNodeWithText("⋯").performClick(); rule.waitUntil(5_000) { exists("Sorting & Gemini") }
        click("Sorting & Gemini"); rule.waitUntil(5_000) { exists("Study") }
        click("Study"); rule.waitUntil(5_000) { exists("Your group") }
        shot("group-edit")
        rule.onNodeWithContentDescription("Group name").performTextClearance()
        rule.onNodeWithContentDescription("Group name").performTextInput("Uni")
        click("Save name"); assertEquals("Uni", g.title)
        click("Uni"); click("Delete group"); click("Delete")
        assertEquals(auto, s.groupOf[waze.key]); assertTrue(Group.customs.isEmpty())
        rule.onAllNodesWithContentDescription("Back")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        // Settings › Apps › Favorites and Most used
        repeat(3) { s.recordLaunch(waze) }; s.recordLaunch(s.apps.first { it.label == "Instagram" })
        s.toggleFav(waze); s.toggleFav(s.apps.first { it.label == "Instagram" })
        click("Apps"); rule.waitUntil(5_000) { exists("Most used") }
        click("Favorites"); rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Remove Waze").fetchSemanticsNodes().isNotEmpty() }
        shot("favorites-editor")
        rule.onNodeWithContentDescription("Remove Waze").performClick(); rule.waitForIdle()
        assertFalse(s.isFav(waze))
        click("Add favorites"); rule.onNodeWithContentDescription("Find an app to favorite").performTextInput("Waze")
        rule.waitForIdle(); click("Waze"); assertTrue(s.isFav(waze))
        back(); rule.waitUntil(5_000) { exists("Most used") }
        click("Most used"); rule.waitUntil(5_000) { exists("opens") }
        listOf("Today", "All time", "This week").forEach { click(it) }
        shot("most-used")
        assertTrue(exists("3"))
        back(); back(); back()
        // Gemini progress screen (driven directly; no network in tests)
        rule.runOnIdle { s.progress = Siftra.SortProgress(listOf("Alpha", "Beta")).also { it.rows[0] = "Alpha" to Group.TOOLS; it.done = 1 }; s.progressVisible = true }
        rule.waitUntil(5_000) { exists("Gemini is sorting") }
        assertTrue(exists("1/2") && exists("→ Tools") && exists("thinking…"))
        shot("gemini-progress")
        click("Run in background"); assertFalse(s.progressVisible)
    }

    /* ---------- 0.5.6: notification dots, Gestures & dots page, swipe down ---------- */

    @Test fun dotsGesturesPage() {
        start(HomeLayout.CARDS)
        val s = model()
        val insta = s.apps.first { it.label == "Instagram" }
        rule.runOnIdle { Dots.set(mapOf(Dots.keyOf(insta.pkg, insta.user.hashCode()) to 2)) }
        // swipe down on the clock: asks for the notification panel, must not open the drawer or crash
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Unread notifications in this group").fetchSemanticsNodes().isNotEmpty() }
        shot("dots-home")
        rule.onAllNodes(isRoot())[0].performTouchInput { swipeDown(startY = 250f, endY = 800f) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        // dots on the app and on its group in the drawer
        rule.onNodeWithText("︿  All apps").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Instagram")
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("New notifications").fetchSemanticsNodes().isNotEmpty() }
        shot("dots")
        // turning dots off hides them everywhere
        rule.runOnIdle { s.setDotsOn(false) }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("New notifications").fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { s.setDotsOn(true); Dots.set(emptyMap()) }
        back(); back()
        // Settings › Gestures & dots
        rule.onNodeWithText("⋯").performClick(); rule.waitUntil(5_000) { exists("Gestures & dots") }
        rule.onAllNodesWithText("Gestures & dots")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(5_000) { exists("Allow notification access") }
        assertTrue(exists("Turn on Siftra's lock service") && exists("Open App info"))
        rule.onNodeWithContentDescription("Swipe down").performClick(); rule.waitForIdle()
        assertFalse(s.swipeDown)
        shot("gestures-page")
        rule.onNodeWithContentDescription("Swipe down").performClick(); rule.waitForIdle()
        assertTrue(s.swipeDown)
    }

    /* ---------- 0.5.7: widget page left of home ---------- */

    @Test fun widgetsPage() {
        val awm = android.appwidget.AppWidgetManager.getInstance(ctx)
        val sh = org.robolectric.Shadows.shadowOf(awm)
        sh.setAllowedToBindAppWidgets(true)
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName("com.spotify.music", "com.spotify.music.Widget")
            minWidth = 250 * 3; minHeight = 110 * 3; initialLayout = android.R.layout.simple_list_item_1
            @Suppress("DEPRECATION") label = "Spotify player"
            android.appwidget.AppWidgetProviderInfo::class.java.getField("providerInfo").set(this, android.content.pm.ActivityInfo().apply {
                packageName = "com.spotify.music"; name = "com.spotify.music.Widget"; nonLocalizedLabel = "Spotify player"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.spotify.music"; uid = 10123 }
            })
        }
        sh.addInstalledProvider(info)
        start(HomeLayout.CARDS)
        val s = model()
        fun click(t: String) { rule.onAllNodesWithText(t).onLast().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
        fun tapDesc(d: String) { rule.onAllNodesWithContentDescription(d).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle() }
        // swipe right from home → widget page
        rule.onAllNodes(isRoot())[0].performTouchInput { swipeRight(startX = 60f, endX = width - 60f, durationMillis = 300) }
        rule.waitUntil(5_000) { exists("Add a widget") }
        shot("widgets-empty")
        click("Add a widget")
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Add Spotify player").fetchSemanticsNodes().isNotEmpty() }
        shot("widget-picker")
        tapDesc("Add Spotify player")
        rule.waitUntil(5_000) { s.widgets.size == 1 }
        val w = s.widgets[0]
        assertTrue("4-cell wide widget starts full width", w.full)
        assertTrue(w.height in 90..420)
        shot("widgets-one")
        click("Edit")
        tapDesc("Taller"); assertEquals(w.height + 40, s.widgets[0].height)
        tapDesc("Half width"); assertFalse(s.widgets[0].full)
        shot("widgets-edit")
        tapDesc("Remove widget"); assertTrue(s.widgets.isEmpty())
        // saved list survives a restart of the model
        s.addWidget(WidgetSlot(77, 160, false))
        assertEquals(listOf(WidgetSlot(77, 160, false)), Siftra(ctx).widgets.toList())
        s.removeWidget(77)
        // Home press returns to the home page
        scenario.onActivity { a -> MainActivity::class.java.getDeclaredMethod("onNewIntent", android.content.Intent::class.java)
            .apply { isAccessible = true }.invoke(a, android.content.Intent(android.content.Intent.ACTION_MAIN)) }
        rule.waitUntil(5_000) { exists("Search apps", ) || rule.onAllNodes(hasText("Search apps", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun lateLoadStillShowsGroups() {
        for (layout in listOf(HomeLayout.STRATA, HomeLayout.CARDS, HomeLayout.INDEX)) {
            TestHooks.apps = { Thread.sleep(700); CATALOG.map { it.first } + filler(60) }
            TestHooks.freezeMotion = true
            val e = ctx.getSharedPreferences("siftra", 0).edit().clear()
            e.putBoolean("motion", false).putBoolean("welcomed", true); e.commit()
            Siftra(ctx).setHomeLayout(layout)
            ctx.getSharedPreferences("siftra", 0).edit().putBoolean("motion", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val ok = runCatching { rule.waitUntil(8_000) { rule.onAllNodes(hasText("Made by Mahmut", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() } }.isSuccess
            assertTrue("groups missing after a late load ($layout)", ok)
            scenario.close()
        }
    }
}
