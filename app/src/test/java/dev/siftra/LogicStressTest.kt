package dev.siftra

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogicStressTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun clean() { ctx.getSharedPreferences("siftra", 0).edit().clear().commit(); TestHooks.apps = null }
    @After fun reset() { TestHooks.apps = null }

    /* ---------- classification ---------- */

    @Test fun rulesSortTheRealCatalog() {
        val wrong = CATALOG.mapNotNull { (a, want) ->
            val got = Rules.of(a) ?: Group.OTHER
            if (got != want) "${a.label} (${a.pkg}): got $got, want $want" else null
        }
        println("Rules: ${CATALOG.size - wrong.size}/${CATALOG.size} correct")
        assertTrue("Misclassified:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun rulesNeverCrashOnWeirdInput() {
        val weird = listOf(app("", ""), app("a", "🙂🙂🙂"), app("x.y.z", " ".repeat(500)), app("gov", "GOV"),
            app("com.samsung.", ""), app("çğıöşü.ÇĞİÖŞÜ", "İıŞş"), app("a.b", "e-devlet".repeat(1000)))
        weird.forEach { Rules.of(it) }
    }

    /* ---------- state under load ---------- */

    private fun loaded(apps: List<AppItem>): Siftra {
        TestHooks.apps = { apps }
        return Siftra(ctx).also { runBlocking { it.load() } }
    }

    @Test fun twoThousandAppsLoadAndGroupFast() {
        val apps = CATALOG.map { it.first } + filler(2000)
        val t0 = System.nanoTime()
        val s = loaded(apps)
        val ms = (System.nanoTime() - t0) / 1_000_000
        repeat(20) { s.regroup() }
        val regroupMs = (System.nanoTime() - t0) / 1_000_000 - ms
        println("load+group ${apps.size} apps: ${ms}ms, 20× regroup: ${regroupMs}ms")
        assertEquals(apps.size, s.apps.size)
        // every visible app lands in exactly one section
        val inSections = s.sections(true).flatMap { it.second }
        assertEquals(apps.size, inSections.size)
        assertEquals(apps.size, inSections.map { it.key }.toSet().size)
        assertTrue("regroup too slow", regroupMs < 20_000)
    }

    @Test fun favoritesHideMoveSurviveRestart() {
        val s = loaded(CATALOG.map { it.first })
        val insta = s.apps.first { it.label == "Instagram" }
        val akbank = s.apps.first { it.label == "Akbank" }
        val bixby = s.apps.first { it.label == "Bixby" }
        s.toggleFav(insta); s.toggleFav(akbank); s.toggleFav(insta); s.toggleFav(insta)
        s.hide(bixby)
        s.move(akbank, Group.TOOLS)
        repeat(5) { s.launch(insta, null) }; s.launch(akbank, null)

        val s2 = loaded(CATALOG.map { it.first })   // fresh instance = app restart
        assertEquals(listOf(akbank.key, insta.key), s2.favorites)
        assertTrue(s2.visible.none { it.pkg == bixby.pkg })
        assertEquals(Group.TOOLS, s2.groupOf[akbank.key])
        assertEquals(insta.key, s2.mostUsed().first().key)
        s2.resetMoves(); assertEquals(Group.FINANCE, s2.groupOf[akbank.key])
        s2.unhideAll(); assertTrue(s2.visible.any { it.pkg == bixby.pkg })
    }

    @Test fun thousandsOfRandomOperationsKeepStateConsistent() {
        val apps = CATALOG.map { it.first } + filler(150)
        val s = loaded(apps)
        val r = java.util.Random(1)
        repeat(5000) {
            val a = s.apps[r.nextInt(s.apps.size)]
            when (r.nextInt(8)) {
                0 -> s.toggleFav(a)
                1 -> s.hide(a)
                2 -> s.move(a, Group.entries[r.nextInt(Group.entries.size)])
                3 -> s.launch(a, null)
                4 -> if (r.nextInt(40) == 0) s.unhideAll()
                5 -> if (r.nextInt(60) == 0) s.resetMoves()
                6 -> s.setHomeLayout(HomeLayout.entries[r.nextInt(HomeLayout.entries.size)])
                7 -> { s.setExtras(r.nextBoolean()); s.setClock(ClockStyle.entries[r.nextInt(ClockStyle.entries.size)]) }
            }
        }
        // invariants
        assertEquals(s.favorites.size, s.favorites.toSet().size)                         // no duplicate favorites
        val vis = s.visible
        assertTrue(vis.none { it.pkg in s.hidden })
        assertEquals(vis.size, s.sections(true).sumOf { it.second.size })                 // nothing lost or duplicated
        assertTrue(s.sections(false).none { it.first == Group.SAMSUNG_EXTRA })
        assertTrue(s.mostUsed(10).zipWithNext().all { (x, y) -> (s.counts[x.key] ?: 0) >= (s.counts[y.key] ?: 0) })
    }

    @Test fun layoutPresetsApplyTheirLook() {
        val s = loaded(CATALOG.map { it.first })
        val expect = mapOf(
            HomeLayout.STRATA to Triple(Backdrop.YOU, false, ClockStyle.SPLIT),
            HomeLayout.MONOLITH to Triple(Backdrop.EMBER, true, ClockStyle.MONOLITH),
            HomeLayout.ORBIT to Triple(Backdrop.PASTEL, false, ClockStyle.ANALOG),
            HomeLayout.INDEX to Triple(Backdrop.LIME, true, ClockStyle.MINIMAL),
        )
        expect.forEach { (l, t) ->
            s.setHomeLayout(l)
            assertEquals(l, s.layout); assertEquals(t.first, s.backdrop); assertEquals(t.second, s.ribbed); assertEquals(t.third, s.clockStyle)
        }
        val before = Triple(s.backdrop, s.ribbed, s.clockStyle)
        s.setHomeLayout(HomeLayout.CARDS)                                                   // Cards keeps the current look
        assertEquals(before, Triple(s.backdrop, s.ribbed, s.clockStyle))
    }

    @Test fun organizeWithoutKeyIsOfflineAndSafe() {
        val s = loaded(CATALOG.map { it.first } + filler(40))
        runBlocking { s.organize(force = true) }
        assertEquals("Sorted ✓", s.status)
    }

    @Test fun organizeWithBadKeyReportsInsteadOfCrashing() {
        val s = loaded(filler(10))
        s.saveKey("not-a-real-key")
        runBlocking { s.organize(force = true) }
        assertTrue(s.status, s.status.startsWith("Gemini:"))
        assertTrue(s.groupOf.values.all { it == Group.OTHER || it in Group.entries })
    }

    @Test fun launchHistoryAndGroupOrder() {
        val s = loaded(CATALOG.map { it.first })
        val wa = s.apps.first { it.label == "WhatsApp" }
        val now = System.currentTimeMillis(); val day = 86_400_000L
        s.recordLaunch(wa, now - 70 * day)           // older than 60 days → pruned
        s.recordLaunch(wa, now - 5 * day)
        s.recordLaunch(wa, now - 3_600_000)
        s.recordLaunch(wa, now)
        assertEquals(2, s.opens(wa, now - day))      // today
        assertEquals(3, s.opens(wa, now - 7 * day))  // this week
        assertEquals(4, s.opens(wa, 0))              // all time counter
        val s2 = loaded(CATALOG.map { it.first })
        assertEquals("history persists", 3, s2.opens(s2.apps.first { it.label == "WhatsApp" }, now - 7 * day))
        // group order drives section order everywhere
        val order = listOf(Group.GOOGLE, Group.FINANCE) + Group.entries.filter { it != Group.GOOGLE && it != Group.FINANCE }
        s2.saveGroupOrder(order)
        assertEquals(Group.GOOGLE, s2.sections(true).first().first)
        assertEquals(Group.GOOGLE, loaded(CATALOG.map { it.first }).groupOrder.first())
        // hidden dates
        val ak = s2.apps.first { it.label == "Akbank" }
        s2.hide(ak); assertTrue(s2.hiddenAt(ak.pkg) > 0); assertEquals(ak.pkg, s2.hiddenApps().first().pkg)
        s2.unhide(ak.pkg); assertTrue(s2.hiddenApps().none { it.pkg == ak.pkg })
    }

    /* ---------- Gemini answer parsing ---------- */

    @Test fun geminiParsingIsTolerant() {
        val apps = listOf(app("a.one", "One"), app("b.two", "Two"), app("c.three", "Three"))
        val fenced = "Sure!\n```json\n{\"a.one\": \"finance\", \"b.two\": \"NOPE\"}\n```"
        val m = Gemini.parse(fenced, apps)
        assertEquals(Group.FINANCE, m["a.one"]); assertEquals(Group.OTHER, m["b.two"]); assertEquals(Group.OTHER, m["c.three"])
        assertEquals(Group.TOOLS, Gemini.parse("{\"a.one\":\" tools \"}", apps)["a.one"])
        assertThrows(java.io.IOException::class.java) { Gemini.parse("no json here", apps) }
        assertThrows(Exception::class.java) { Gemini.parse("{broken", apps) }
    }

    @Test fun customGroupsCreateRenameDeletePersist() {
        val s = loaded(CATALOG.map { it.first })
        val wa = s.apps.first { it.label == "WhatsApp" }
        val auto = s.groupOf[wa.key]
        val g = s.createGroup("  Study ")
        s.move(wa, g)
        assertEquals(g, s.groupOf[wa.key]); assertEquals(g, s.groupOrder.last())
        val s2 = loaded(CATALOG.map { it.first })
        val g2 = Group.byName(g.name)!!
        assertEquals("Study", g2.title); assertEquals(g2, s2.groupOf[s2.apps.first { it.label == "WhatsApp" }.key])
        s2.renameGroup(g2, "Uni")
        assertEquals("Uni", loaded(CATALOG.map { it.first }).let { Group.byName(g.name)!!.title })
        val s3 = loaded(CATALOG.map { it.first })
        s3.deleteGroup(Group.byName(g.name)!!)
        assertEquals(auto, s3.groupOf[s3.apps.first { it.label == "WhatsApp" }.key])
        val s4 = loaded(CATALOG.map { it.first })
        assertNull(Group.byName(g.name)); assertTrue(s4.groupOrder.none { it.custom })
        // Gemini may answer with a custom group id
        val g5 = s4.createGroup("Study")
        assertEquals(g5, Gemini.parse("{\"a.one\":\"${g5.name}\"}", listOf(app("a.one", "One")))["a.one"])
        // favorites order persists
        val keys = s4.apps.take(3).map { it.key }
        s4.saveFavorites(keys.reversed())
        assertEquals(keys.reversed(), loaded(CATALOG.map { it.first }).favoriteApps().map { it.key })
    }
}
