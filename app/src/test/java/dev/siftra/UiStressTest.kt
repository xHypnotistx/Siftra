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
        e.putBoolean("motion", motion); extra(e); e.commit()
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
        rule.onAllNodes(isRoot())[0].performTouchInput { click(center.copy(y = height * 0.92f)); click(center) }
        rule.waitForIdle()
        assertEquals("tap fell through the drawer", before, model().counts.values.sum())
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())   // drawer still open
        // search
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("insta")
        rule.waitForIdle()
        assertTrue(exists("Instagram")); assertFalse(exists("Akbank"))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("zzzz")
        assertTrue(exists("No apps match “instazzzz”"))
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        // chips jump
        listOf("Finance", "Travel", "Government", "Samsung Extras").forEach { t ->
            rule.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange))
                .performScrollToNode(hasText(t))
            rule.onAllNodesWithText(t)[0].performClick(); rule.waitForIdle()
        }
        // favorite via long-press
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Instagram")
        rule.onAllNodes(hasText("Instagram") and !hasSetTextAction())[0].performTouchInput { longClick() }
        rule.onNodeWithText("☆  Add to favorites").performClick()
        // hide
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Akbank")
        rule.onAllNodes(hasText("Akbank") and !hasSetTextAction())[0].performTouchInput { longClick() }
        rule.onNodeWithText("◌  Hide").performClick()
        rule.waitForIdle()
        assertFalse(exists("Akbank") && !exists("No apps match “Akbank”"))
        // move
        rule.onAllNodes(hasSetTextAction())[0].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Waze")
        rule.onAllNodes(hasText("Waze") and !hasSetTextAction())[0].performTouchInput { longClick() }
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

    /* ---------- every settings control, then hammer them ---------- */

    @Test fun everySettingAndRapidSwitching() {
        start(HomeLayout.CARDS)
        fun openSettings() { rule.onNodeWithText("⋯").performClick(); rule.waitUntil(5_000) { exists("Home layout") } }
        fun tap(t: String, last: Boolean = false) {
            val nodes = rule.onAllNodesWithText(t)
            val n = if (last) nodes.fetchSemanticsNodes().size - 1 else 0
            nodes[n].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        }
        openSettings()
        HomeLayout.entries.forEach { tap(it.title) }
        Backdrop.entries.filter { it != Backdrop.PHOTO }.forEach { tap(it.title) }
        listOf("Ribbed", "Frosted").forEach { tap(it) }
        ClockStyle.entries.forEach { tap(it.title, last = true) }
        listOf("System", "Classy").forEach { tap(it) }
        listOf("Show Samsung Extras on home", "Material You colors", "Themed icons (Material You)").forEach { label ->
            repeat(2) { tap(rule.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes()[0].config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text) }
        }
        tap("↺  Reset my manual moves"); tap("◌  Unhide all apps (0)")
        back()
        // rapid: 60 layout flips with a group opened in between
        val s = model()
        val r = java.util.Random(3)
        repeat(60) { i ->
            scenario.onActivity { s.setHomeLayout(HomeLayout.entries[r.nextInt(HomeLayout.entries.size)]) }
            rule.waitForIdle()
            if (i % 10 == 0) shot("rapid-$i-${s.layout.name}")
        }
        // tap-to-cycle the clock through every style twice
        repeat(ClockStyle.entries.size * 2) { scenario.onActivity { s.nextClock() }; rule.waitForIdle() }
    }

    /* ---------- animated backdrop running with real frames ---------- */

    @Test fun animatedBackdropFrames() {
        rule.mainClock.autoAdvance = false
        TestHooks.apps = { CATALOG.map { it.first } }; TestHooks.freezeMotion = false
        ctx.getSharedPreferences("siftra", 0).edit().clear().putBoolean("motion", true).commit()
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
        assertTrue(done > 300)
    }
}
