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
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Made by Mahmut", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
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
        rule.onNodeWithText("☆  Add to favorites").performClick()
        // hide
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Akbank")
        rule.onAllNodes(hasText("Akbank") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.onNodeWithText("◌  Hide").performClick()
        rule.waitForIdle()
        assertTrue(exists("“Akbank”"))   // hidden → "Nothing called “Akbank”"
        // move
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Waze")
        rule.onAllNodes(hasText("Waze") and !hasSetTextAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))[0].performTouchInput { longClick() }
        rule.onNodeWithText("⇄  Move to group…").performClick()
        rule.onNodeWithText("○  Games").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
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
}
