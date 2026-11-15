package dev.siftra

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/* ======================= Groups ======================= */

/** A group: one of the 15 built-ins, or a custom group the user created ("C_<id>"). */
class Group private constructor(val name: String, title: String, val custom: Boolean) {
    var title by mutableStateOf(title)
    override fun equals(other: Any?) = other is Group && other.name == name
    override fun hashCode() = name.hashCode()
    override fun toString() = name

    companion object {
        val MINE = Group("MINE", "Made by Mahmut", false)
        val GOV = Group("GOV", "Government", false)
        val INSPIRE = Group("INSPIRE", "Inspiration", false)
        val SAMSUNG = Group("SAMSUNG", "Samsung Essentials", false)
        val GOOGLE = Group("GOOGLE", "Google", false)
        val SOCIAL = Group("SOCIAL", "Social", false)
        val MEDIA = Group("MEDIA", "Media", false)
        val GAMES = Group("GAMES", "Games", false)
        val FINANCE = Group("FINANCE", "Finance", false)
        val SHOPPING = Group("SHOPPING", "Shopping", false)
        val TRAVEL = Group("TRAVEL", "Travel", false)
        val PRODUCTIVITY = Group("PRODUCTIVITY", "Productivity", false)
        val TOOLS = Group("TOOLS", "Tools", false)
        val OTHER = Group("OTHER", "Other", false)
        val SAMSUNG_EXTRA = Group("SAMSUNG_EXTRA", "Samsung Extras", false)
        val builtIns = listOf(MINE, GOV, INSPIRE, SAMSUNG, GOOGLE, SOCIAL, MEDIA, GAMES, FINANCE, SHOPPING, TRAVEL, PRODUCTIVITY, TOOLS, OTHER, SAMSUNG_EXTRA)

        internal val customs = androidx.compose.runtime.mutableStateListOf<Group>()
        /** Built-ins followed by custom groups. */
        val entries: List<Group> get() = builtIns + customs
        fun byName(n: String?) = if (n == null) null else entries.firstOrNull { it.name == n }
        internal fun newCustom(id: String, title: String) = Group(id, title, true)
    }
}

class AppItem(
    val key: String, val pkg: String, val label: String, val icon: ImageBitmap,
    val component: ComponentName, val user: UserHandle, val info: ApplicationInfo,
    val mono: ImageBitmap? = null, val stamp: String = "",
)

/* ======================= Rules ======================= */

object Rules {
    private val samsungEssentials = setOf(
        "com.samsung.android.dialer", "com.samsung.android.app.contacts", "com.samsung.android.messaging",
        "com.sec.android.app.myfiles", "com.samsung.android.app.notes", "com.sec.android.app.camera",
        "com.sec.android.gallery3d", "com.sec.android.app.clockpackage", "com.samsung.android.calendar",
        "com.android.settings", "com.sec.android.app.popupcalculator", "com.sec.android.app.voicenote",
        "com.sec.android.app.sbrowser", "com.samsung.android.email.provider", "com.samsung.android.app.reminder",
        "com.samsung.android.weather", "com.samsung.android.lool", "com.samsung.android.app.galaxyfinder",
    )

    private val keywords: List<Pair<Group, List<String>>> = listOf(
        Group.GOV to listOf("e-devlet", "edevlet", "e-nabız", "enabiz", "mhrs", "e-okul", "eokul", "hayat eve sığar",
            "hayateve", "uyap", "sgk", "e-imza", "turkiye.gov", "gib", "gov", "ösym", "osym", "meb", "ptt", "ibb", "belediye",
            "nvi", "emniyet", "egm", "jandarma", "işkur", "iskur", "tkgm", "e-tapu", "tcmb", "hes kodu", "e-nabiz"),
        Group.INSPIRE to listOf("pinterest", "savee", "are.na", "arena", "cosmos", "mymind", "behance", "artstation",
            "dribbble", "unsplash", "vsco", "muzli", "mobbin", "weheartit", "flickr", "500px", "designspiration"),
        Group.SOCIAL to listOf("instagram", "whatsapp", "telegram", "twitter", "facebook", "snapchat", "discord", "reddit",
            "musically", "trill", "tiktok", "barcelona", "threads", "linkedin", "bluesky", "signal", "messenger",
            "bereal", "mastodon", "tumblr", "bip", "viber", "naver.line", "kakao", "wechat", "skype", "tinder", "bumble",
            "hinge", "vkontakte", "clubhouse", "quora", "nextdoor", "yubo"),
        Group.FINANCE to listOf("bank", "banka", "garanti", "akbank", "isbank", "ziraat", "yapikredi", "ykb", "papara",
            "enpara", "qnb", "vakif", "halkbank", "denizbank", "paypal", "binance", "revolut", "btcturk", "paribu",
            "ininal", "tosla", "cüzdan", "wallet", "borsa", "kripto", "crypto", "wise", "iscep", "işcep", "kuveyt",
            "finans", "ondijital", "money", "fibabanka", "odeabank", "şekerbank", "albaraka", "hsbc", "teb", "ing",
            "vakıf", "bankası", "bankacılık", "midas", "colendi", "paycell", "sipay", "okx", "bybit", "bitget",
            "kucoin", "icrypex", "gedik", "investing", "tradingview", "matriks", "foreks", "cepteteb", "nkolay",
            "multinet", "pluxee", "sodexo", "edenred", "setcard"),
        Group.SHOPPING to listOf("trendyol", "hepsiburada", "amazon", "n11", "getir", "yemeksepeti", "temu",
            "aliexpress", "sahibinden", "letgo", "dolap", "migros", "a101", "shein", "ebay", "ikea", "zara", "çiçeksepeti",
            "pttavm", "boyner", "waikiki", "defacto", "koton", "mediamarkt", "teknosa", "vatan", "banabi", "istegelsin",
            "carrefour", "şok", "bim", "macrocenter", "starbucks", "dominos", "burgerking", "mcdonalds", "kfc", "popeyes",
            "morhipo", "gratis", "watsons", "rossmann", "decathlon", "nike", "adidas", "etsy", "vinted", "olx", "armut", "hopi"),
        Group.TRAVEL to listOf("maps", "waze", "uber", "bitaksi", "booking", "airbnb", "turkishairlines", "pegasus",
            "obilet", "moovit", "istanbulkart", "enuygun", "trivago", "skyscanner", "yandex.taxi", "thy", "ajet",
            "sunexpress", "anadolujet", "tcdd", "iett", "metro istanbul", "metroistanbul", "marti", "bolt", "lyft", "blablacar", "tripadvisor",
            "expedia", "agoda", "kayak", "flightradar", "citymapper", "yandex.navi", "tatilsepeti", "jollytur", "etstur",
            "otelz", "hotels", "izban", "kentkart"),
        Group.MEDIA to listOf("spotify", "netflix", "disney", "twitch", "soundcloud", "deezer", "music", "video",
            "player", "podcast", "primevideo", "exxen", "blutv", "tabii", "mubi", "shazam", "fizy", "trt", "puhutv", "gain",
            "bein", "todtv", "ssport", "dsmart", "tivibu", "vlc", "mxplayer", "radio", "radyo", "news", "haber",
            "hurriyet", "sozcu", "milliyet", "ntv", "cnnturk", "bbc", "medium", "wattpad", "kindle", "storytel", "audible",
            "letterboxd", "imdb", "capcut", "picsart", "lightroom", "inshot", "muud", "plex", "jellyfin", "crunchyroll",
            "hbomax", "wbd.stream"),
        Group.GAMES to listOf("game", "games", "supercell", "roblox", "mojang", "minecraft", "pubg", "epicgames",
            "riotgames", "gameloft", "king.com", "peakgames", "dreamgames", "rollic", "mihoyo", "hoyoverse", "garena",
            "moonton", "zynga", "rovio", "playrix", "miniclip", "nintendo", "ubisoft", "netmarble", "nexon", "activision",
            "tencent", "voodoo", "ketchapp"),
        Group.PRODUCTIVITY to listOf("office", "word", "excel", "powerpoint", "notion", "obsidian", "todo", "outlook",
            "slack", "teams", "zoom", "trello", "evernote", "todoist", "ticktick", "chatgpt", "claude", "gemini",
            "docs", "dropbox", "onedrive", "pdf", "acrobat", "canva", "figma", "miro", "asana", "clickup", "jira",
            "github", "copilot", "perplexity", "deepseek", "grok", "mistral", "calendar", "mail", "webex"),
        Group.TOOLS to listOf("vpn", "defender", "scmx", "antivirus", "security", "cleaner", "rar", "rarlab", "zip",
            "telekom", "turkcell", "vodafone", "keyboard", "swiftkey", "gboard", "scanner", "flashlight", "calculator",
            "translate", "launcher", "widget", "backup", "authenticator", "bluetooth", "wifi", "speedtest", "ookla",
            "qr", "barcode", "compass", "battery", "recorder", "termux", "adguard", "nextdns", "1dot1dot1dot1",
            "cloudflare", "bitwarden", "1password", "lastpass", "anydesk", "teamviewer", "shizuku", "fdroid",
            "zarchiver", "tasker", "macrodroid", "homeassistant", "tuya", "smartlife"),
    ).map { (g, ks) -> g to ks.map(::norm) }

    private val split = Regex("[^a-z0-9.]+")

    /** Lower-case and fold Turkish letters so "İşCep", "iscep" and "ışcep" all match the same keyword. */
    fun norm(t: String): String = t.replace('İ', 'i').lowercase(java.util.Locale.ROOT)
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
        .replace("\u0307", "")

    /** Returns a group, or null when the rules can't tell (→ Gemini / Other). */
    fun of(a: AppItem): Group? {
        val p = a.pkg
        if (p.startsWith("com.mahmut.")) return Group.MINE
        if (p in samsungEssentials) return Group.SAMSUNG
        val hay = norm("$p ${a.label}")
        val tokens = hay.split(split).flatMap { it.split('.') + it }.toSet()
        fun hit(k: String) = if (k.length >= 5) hay.contains(k) else k in tokens
        keywords.first().second.let { if (it.any(::hit)) return Group.GOV }
        keywords[1].second.let { if (it.any(::hit)) return Group.INSPIRE }
        if (p.startsWith("com.google.") || p == "com.android.chrome" || p == "com.android.vending") return Group.GOOGLE
        if (p.startsWith("com.samsung.") || p.startsWith("com.sec.") || p.startsWith("com.osp.") ||
            p.startsWith("com.microsoft.skydrive") && a.info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return Group.SAMSUNG_EXTRA
        for ((g, ks) in keywords.drop(2)) if (ks.any(::hit)) return g
        when (a.info.category) {
            ApplicationInfo.CATEGORY_GAME -> return Group.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_NEWS -> return Group.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> return Group.SOCIAL
            ApplicationInfo.CATEGORY_MAPS -> return Group.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return Group.PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> return Group.TOOLS
        }
        if (a.info.flags and ApplicationInfo.FLAG_SYSTEM != 0 || p.startsWith("com.android.")) return Group.TOOLS
        return null
    }
}

/* ======================= Gemini (unknown apps only) ======================= */

object Gemini {
    fun classify(key: String, model: String, apps: List<AppItem>): Map<String, Group> {
        val prompt = buildString {
            appendLine("Sort these Android apps into exactly one group each.")
            appendLine("Groups: GOV=official government/public services, INSPIRE=visual inspiration & moodboards, SOCIAL, MEDIA=video/music/news/photo, GAMES, FINANCE=banking/payments/crypto, SHOPPING=shops/food delivery/marketplaces, TRAVEL=maps/transport/flights/hotels, PRODUCTIVITY=work/notes/office/AI assistants, TOOLS=utilities/system/settings, OTHER.")
            if (Group.customs.isNotEmpty()) appendLine("The user also made these groups; prefer one when it clearly fits: " +
                Group.customs.joinToString { "${it.name}=\"${it.title}\"" } + ".")
            appendLine("Reply ONLY with a JSON object mapping each package name to a group id.")
            apps.forEach { appendLine("${it.pkg} | ${it.label}") }
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 60_000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        val j = try { JSONObject(txt) } catch (_: Exception) { JSONObject() }
        if (code !in 200..299) throw IOException(j.optJSONObject("error")?.optString("message") ?: "HTTP $code")
        val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Empty answer")
        val text = (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
            .filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
        return parse(text, apps)
    }

    private val allowed get() = listOf(Group.GOV, Group.INSPIRE, Group.SOCIAL, Group.MEDIA, Group.GAMES, Group.FINANCE,
        Group.SHOPPING, Group.TRAVEL, Group.PRODUCTIVITY, Group.TOOLS, Group.OTHER) + Group.customs

    /** Reads the model's JSON (tolerates code fences / chatter); unknown or missing ids become OTHER. */
    fun parse(text: String, apps: List<AppItem>): Map<String, Group> {
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) throw IOException("Gemini answer had no JSON")
        val obj = JSONObject(text.substring(a, b + 1))
        return apps.associate { app ->
            val v = obj.optString(app.pkg).trim()
            app.pkg to (allowed.firstOrNull { it.name == v || it.name == v.uppercase() } ?: Group.OTHER)
        }
    }
}

/** Apps per Gemini request: one call covers a typical batch of new installs. */
const val GEMINI_BATCH = 12

/**
 * The developer part of a package name: "com.akbank" for "com.akbank.android.apps.x", three parts when the second one is
 * generic ("tr.gov.saglik" for "tr.gov.saglik.enabiz").
 */
fun devPrefix(pkg: String): String {
    val p = pkg.split('.')
    if (p.size < 3) return pkg
    return if (p[1] in setOf("com", "org", "net", "gov", "edu", "co", "ac", "app", "android", "mobile", "apps")) p.take(3).joinToString(".")
    else p.take(2).joinToString(".")
}

/** Lets tests feed a synthetic app list instead of LauncherApps. */
object TestHooks { @Volatile var apps: (() -> List<AppItem>)? = null; @Volatile var freezeMotion = false }

/* ======================= State ======================= */

class Siftra(private val ctx: Context) {
    val prefs = ctx.getSharedPreferences("siftra", 0)
    private val la = ctx.getSystemService(LauncherApps::class.java)

    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var groupOf by mutableStateOf<Map<String, Group>>(emptyMap()); private set
    var favorites by mutableStateOf(prefs.getString("fav", "")!!.split('|').filter { it.isNotBlank() }); private set
    var counts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var hidden by mutableStateOf(prefs.getStringSet("hidden", emptySet())!!.toSet()); private set
    var showExtras by mutableStateOf(prefs.getBoolean("extras", false))
    var status by mutableStateOf("")
    var geminiKey by mutableStateOf(prefs.getString("key", "")!!)
    var backdrop by mutableStateOf(Backdrop.entries.firstOrNull { it.name == prefs.getString("backdrop", "YOU") } ?: Backdrop.YOU); private set
    var ribbed by mutableStateOf(prefs.getBoolean("ribbed", false)); private set
    var motion by mutableStateOf(prefs.getBoolean("motion", true)); private set
    var photo by mutableStateOf<ImageBitmap?>(null); private set
    private val photoFile get() = java.io.File(ctx.filesDir, "backdrop.jpg")

    var clockStyle by mutableStateOf(ClockStyle.entries.firstOrNull { it.name == prefs.getString("clock", "SPLIT") } ?: ClockStyle.SPLIT); private set
    var classy by mutableStateOf(prefs.getBoolean("classy", true)); private set
    var you by mutableStateOf(prefs.getBoolean("you", true)); private set
    var themed by mutableStateOf(prefs.getBoolean("themed", true)); private set
    fun setClock(c: ClockStyle) { clockStyle = c; prefs.edit().putString("clock", c.name).apply() }
    fun nextClock() = setClock(ClockStyle.entries[(clockStyle.ordinal + 1) % ClockStyle.entries.size])
    fun setClassyFont(v: Boolean) { classy = v; prefs.edit().putBoolean("classy", v).apply() }
    fun setYouColors(v: Boolean) { you = v; prefs.edit().putBoolean("you", v).apply() }
    fun setThemedIcons(v: Boolean) { themed = v; prefs.edit().putBoolean("themed", v).apply() }

    // Gestures & dots (0.5.6)
    var dots by mutableStateOf(prefs.getBoolean("dots", true)); private set
    var swipeDown by mutableStateOf(prefs.getBoolean("swipeDown", true)); private set
    var doubleTapLock by mutableStateOf(prefs.getBoolean("doubleTapLock", true)); private set
    fun setDotsOn(v: Boolean) { dots = v; Dots.show = v; prefs.edit().putBoolean("dots", v).apply() }
    fun setSwipeDownOn(v: Boolean) { swipeDown = v; prefs.edit().putBoolean("swipeDown", v).apply() }
    fun setDoubleTapLockOn(v: Boolean) { doubleTapLock = v; prefs.edit().putBoolean("doubleTapLock", v).apply() }

    // Widget page (0.5.7)
    val widgets = androidx.compose.runtime.mutableStateListOf<WidgetSlot>().apply { addAll(WidgetSlots.load(prefs.getString("widgets", null))) }
    private fun saveWidgets() { prefs.edit().putString("widgets", WidgetSlots.save(widgets)).apply() }
    fun addWidget(w: WidgetSlot) { widgets.removeAll { it.id == w.id }; widgets += w; saveWidgets() }
    fun removeWidget(id: Int) { widgets.removeAll { it.id == id }; saveWidgets() }
    fun moveWidget(id: Int, d: Int) {
        val i = widgets.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in widgets.indices) return
        widgets.add(j, widgets.removeAt(i)); saveWidgets()
    }
    fun resizeWidget(id: Int, dh: Int) {
        val i = widgets.indexOfFirst { it.id == id }; if (i < 0) return
        widgets[i] = widgets[i].copy(height = (widgets[i].height + dh).coerceIn(80, 560)); saveWidgets()
    }
    fun toggleWidgetWidth(id: Int) {
        val i = widgets.indexOfFirst { it.id == id }; if (i < 0) return
        widgets[i] = widgets[i].copy(full = !widgets[i].full); saveWidgets()
    }

    var layout by mutableStateOf(HomeLayout.entries.firstOrNull { it.name == prefs.getString("layout", "CARDS") } ?: HomeLayout.CARDS); private set
    /** Picking a layout also applies its matching backdrop, glass and clock (all still changeable). */
    fun setHomeLayout(l: HomeLayout) {
        layout = l; prefs.edit().putString("layout", l.name).apply()
        when (l) {
            HomeLayout.STRATA -> { setBackdropMode(Backdrop.YOU); setRibbedGlass(false); setClock(ClockStyle.SPLIT) }
            HomeLayout.MONOLITH -> { setBackdropMode(Backdrop.EMBER); setRibbedGlass(true); setClock(ClockStyle.MONOLITH) }
            HomeLayout.ORBIT -> { setBackdropMode(Backdrop.PASTEL); setRibbedGlass(false); setClock(ClockStyle.ANALOG) }
            HomeLayout.INDEX -> { setBackdropMode(Backdrop.LIME); setRibbedGlass(true); setClock(ClockStyle.MINIMAL); setThemedIcons(true) }
            HomeLayout.CARDS -> {}
        }
    }

    fun setBackdropMode(b: Backdrop) { backdrop = b; prefs.edit().putString("backdrop", b.name).apply() }
    fun setRibbedGlass(v: Boolean) { ribbed = v; prefs.edit().putBoolean("ribbed", v).apply() }
    fun setMotionOn(v: Boolean) { motion = v; prefs.edit().putBoolean("motion", v).apply() }

    /** Picked photo → scaled to the screen once, stored privately, drawn behind the glass. */
    fun setPhoto(uri: android.net.Uri) {
        val dm = ctx.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels) + 200
        try {
            val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > target) d.setTargetSize(info.size.width * target / m, info.size.height * target / m)
            }
            photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            photo = bmp.asImageBitmap()
            setBackdropMode(Backdrop.PHOTO)
        } catch (e: Exception) { status = "Couldn't load that photo" }
    }

    private fun loadPhoto() {
        if (photoFile.exists()) android.graphics.BitmapFactory.decodeFile(photoFile.path)?.let { photo = it.asImageBitmap() }
    }

    init {
        Dots.show = dots
        loadPhoto()
        counts = prefs.all.filterKeys { it.startsWith("lc|") }.mapNotNull { (k, v) -> (v as? Int)?.let { k.drop(3) to it } }.toMap()
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(p: String?, u: UserHandle?) = changed(p)
            override fun onPackageAdded(p: String?, u: UserHandle?) = changed(p)
            override fun onPackageChanged(p: String?, u: UserHandle?) = changed(p)
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = changed(*p.orEmpty())
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = changed(*p.orEmpty())
        }, Handler(Looper.getMainLooper()))
    }

    var reloadTick by mutableStateOf(0); private set
    private fun reloadSoon() { reloadTick++ }
    /** Packages whose icon/name must be read again on the next load; everything else is reused. */
    private val dirty = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun changed(vararg p: String?) { p.filterNotNull().forEach { dirty += it }; reloadSoon() }

    private val iconPx by lazy { (56 * ctx.resources.displayMetrics.density).toInt() }
    internal val iconCache by lazy { IconCache(ctx, iconPx) }
    private val io = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val cacheLock = kotlinx.coroutines.sync.Mutex()
    /** How the last load was served, for tests and the About page. */
    var lastLoad = ""; private set

    /**
     * Lists every launchable app (one cheap system call per profile), then fills in names and icons from
     * memory, then the disk cache, and only opens the APK for apps that are new or were updated.
     */
    suspend fun load() {
        val prev = apps
        val built = java.util.concurrent.atomic.AtomicInteger(); val fromDisk = java.util.concurrent.atomic.AtomicInteger()
        val list = withContext(Dispatchers.Default) {
            TestHooks.apps?.let { return@withContext it().sortedBy { a -> a.label.lowercase() } }
            val infos = la.profiles.flatMap { u -> la.getActivityList(null, u) }.filter { it.applicationInfo.packageName != ctx.packageName }
            val stale = HashSet(dirty).also { dirty.removeAll(it) }
            val mem = prev.associateBy { it.key }
            val disk = if (prev.isEmpty()) iconCache.read() else emptyMap()
            kotlinx.coroutines.coroutineScope {
                infos.chunked((infos.size / 6).coerceAtLeast(12)).map { chunk ->
                    async {
                        chunk.map { inf ->
                            val key = keyOf(inf); val stamp = inf.applicationInfo.sourceDir.orEmpty()
                            val pkg = inf.componentName.packageName
                            val m = mem[key]
                            if (m != null && m.stamp == stamp && pkg !in stale) return@map m
                            val d = disk[key]
                            if (d != null && d.stamp == stamp && pkg !in stale) iconCache.decode(d)?.let { (ic, mo) ->
                                fromDisk.incrementAndGet()
                                return@map AppItem(key, pkg, d.label, ic, inf.componentName, inf.user, inf.applicationInfo, mo, stamp)
                            }
                            built.incrementAndGet()
                            inf.toItem(key, stamp)
                        }
                    }
                }.awaitAll().flatten()
            }.sortedBy { it.label.lowercase() }
        }
        lastLoad = "${list.size} apps · ${fromDisk.get()} from cache · ${built.get()} read"
        apps = list
        regroup()
        if (TestHooks.apps == null && (built.get() > 0 || (prev.isNotEmpty() && list.size != prev.size))) saveIcons(list)
        if (geminiKey.isNotBlank()) { val l = learned(); if (list.any { needsAi(it, l) }) organize(force = false) }
    }

    private fun saveIcons(list: List<AppItem>) {
        io.launch { cacheLock.withLock { try { iconCache.write(list) } catch (_: Exception) {} } }
    }

    /** Settings › Apps: forget every cached icon and name and read them all again. */
    suspend fun refreshIcons() {
        cacheLock.withLock { withContext(Dispatchers.IO) { iconCache.clear() } }
        apps.forEach { dirty += it.pkg }
        load()
    }

    private fun keyOf(i: LauncherActivityInfo) = "${i.componentName.flattenToShortString()}#${i.user.hashCode()}"

    private fun LauncherActivityInfo.toItem(key: String, stamp: String): AppItem {
        val px = iconPx
        val raw = getIcon(0)                                                  // one resource load per app
        val d = if (user == android.os.Process.myUserHandle()) raw else ctx.packageManager.getUserBadgedIcon(raw, user)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px); d.draw(Canvas(b))
        // Material You themed icon: the adaptive icon's monochrome layer (Android 13+), framed to the visible area.
        val mono = if (android.os.Build.VERSION.SDK_INT >= 33) try {
            (raw as? android.graphics.drawable.AdaptiveIconDrawable)?.monochrome?.let { md ->
                val mb = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                md.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4); md.draw(Canvas(mb)); mb.asImageBitmap()
            }
        } catch (_: Exception) { null } else null
        return AppItem(key, componentName.packageName, label.toString(), b.asImageBitmap(), componentName, user, applicationInfo, mono, stamp)
    }

    private fun override(a: AppItem) = Group.byName(prefs.getString("ov|${a.pkg}", null))
    private fun ai(a: AppItem) = Group.byName(prefs.getString("ai|${a.pkg}", null))
    private fun needsAi(a: AppItem, learned: Map<String, Group>) =
        override(a) == null && Rules.of(a) == null && ai(a) == null && learned[devPrefix(a.pkg)] == null

    fun regroup() {
        val learned = learned()
        groupOf = apps.associate { it.key to (override(it) ?: Rules.of(it) ?: learned[devPrefix(it.pkg)] ?: ai(it) ?: Group.OTHER) }
    }

    /**
     * Learning from your moves: when every app you moved from one developer went to the same group, that developer's
     * other apps the rules can't place go there too (before Gemini is asked, and over an older Gemini guess).
     */
    fun learned(): Map<String, Group> {
        val by = HashMap<String, MutableSet<Group>>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("ov|")) Group.byName(v as? String)?.let { g -> by.getOrPut(devPrefix(k.drop(3))) { mutableSetOf() } += g }
        }
        return by.filterValues { it.size == 1 }.mapValues { it.value.first() }
    }

    /** Why an app sits where it does, shown in the Move sheet. */
    fun sourceOf(a: AppItem): String = when {
        override(a) != null -> "Moved there by you"
        Rules.of(a) != null -> "Sorted on your phone"
        learned()[devPrefix(a.pkg)] != null -> "Learned from apps you moved"
        ai(a) != null -> "Placed by Gemini"
        else -> "Not sorted yet"
    }

    /** Live progress of a Gemini sort (shown full-screen only when the user pressed Re-sort). */
    class SortProgress(labels: List<String>) {
        val rows = androidx.compose.runtime.mutableStateListOf(*labels.map { it to (null as Group?) }.toTypedArray())
        var done by mutableStateOf(0)
        var running by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        val total get() = rows.size
    }
    var progress by mutableStateOf<SortProgress?>(null)
    var progressVisible by mutableStateOf(false)

    /** Re-sorts everything. force = ask Gemini again for every app the rules can't place. show = open the progress screen. */
    suspend fun organize(force: Boolean, show: Boolean = false) {
        val learned = learned()
        val unknown = apps.filter { override(it) == null && Rules.of(it) == null && learned[devPrefix(it.pkg)] == null && (force || ai(it) == null) }
            .distinctBy { it.pkg }
        if (unknown.isNotEmpty() && geminiKey.isNotBlank()) {
            val pr = SortProgress(unknown.map { it.label }); progress = pr; progressVisible = show
            status = if (show) "" else "Gemini is sorting ${unknown.size} app${if (unknown.size > 1) "s" else ""}…"
            try {
                unknown.chunked(GEMINI_BATCH).forEachIndexed { b, batch ->
                    val res = withContext(Dispatchers.IO) { Gemini.classify(geminiKey, prefs.getString("model", "gemini-flash-latest")!!, batch) }
                    prefs.edit().apply { res.forEach { (p, g) -> putString("ai|$p", g.name) } }.apply()
                    batch.forEachIndexed { k, a -> pr.rows[b * GEMINI_BATCH + k] = a.label to (res[a.pkg] ?: Group.OTHER) }
                    pr.done += batch.size
                    regroup()
                }
                status = if (show) "" else "Sorted ✓"
            } catch (e: Exception) { pr.error = e.message ?: "Gemini failed"; status = "Gemini: ${e.message}" }
            pr.running = false
        } else status = "Sorted ✓"
        regroup()
    }

    fun resetMoves() { prefs.edit().apply { prefs.all.keys.filter { it.startsWith("ov|") }.forEach { k -> remove(k) } }.apply(); regroup() }

    val visible get() = apps.filter { it.pkg !in hidden }

    /* ---------- custom groups ---------- */
    private val customsLoaded = run {
        Group.customs.clear()
        try {
            val arr = JSONArray(prefs.getString("customGroups", "[]"))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { Group.customs.add(Group.newCustom(it.getString("id"), it.getString("title"))) }
        } catch (_: Exception) {}
        true
    }
    private fun saveCustoms() = prefs.edit().putString("customGroups",
        JSONArray(Group.customs.map { JSONObject().put("id", it.name).put("title", it.title) }).toString()).apply()

    fun createGroup(title: String): Group {
        val g = Group.newCustom("C_${System.currentTimeMillis()}", title.trim().ifBlank { "My group" })
        Group.customs.add(g); saveCustoms()
        saveGroupOrder(groupOrder + g)
        return g
    }
    fun renameGroup(g: Group, title: String) { if (!g.custom || title.isBlank()) return; g.title = title.trim(); saveCustoms(); regroup() }
    /** Deletes a custom group; its apps fall back to their automatic group. */
    fun deleteGroup(g: Group) {
        if (!g.custom) return
        Group.customs.remove(g); saveCustoms()
        prefs.edit().apply { prefs.all.filter { (k, v) -> (k.startsWith("ov|") || k.startsWith("ai|")) && v == g.name }.keys.forEach { remove(it) } }.apply()
        saveGroupOrder(groupOrder - g)
        regroup()
    }

    /* ---------- group order (drag to reorder in Settings › Sorting) ---------- */
    var groupOrder by mutableStateOf(prefs.getString("groupOrder", "")!!.split(',').mapNotNull { n -> Group.byName(n) }
        .let { saved -> saved + Group.entries.filter { it !in saved } }); private set
    fun saveGroupOrder(list: List<Group>) { groupOrder = list; prefs.edit().putString("groupOrder", list.joinToString(",") { it.name }).apply() }

    fun sections(includeExtras: Boolean): List<Pair<Group, List<AppItem>>> {
        val v = visible
        return groupOrder.filter { includeExtras || it != Group.SAMSUNG_EXTRA }
            .mapNotNull { g -> v.filter { groupOf[it.key] == g }.takeIf { it.isNotEmpty() }?.let { g to it } }
    }

    fun mostUsed(n: Int = 10) = visible.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(n)
    fun favoriteApps() = favorites.mapNotNull { k -> visible.firstOrNull { it.key == k } }

    fun launch(a: AppItem, bounds: Rect?) {
        try { la.startMainActivity(a.component, a.user, bounds, null) } catch (_: Exception) { return }
        recordLaunch(a)
    }

    /* ---------- launch history (dated, on-device only; kept 60 days) ---------- */
    private val historyFile = java.io.File(ctx.filesDir, "launches.json")
    private val history: MutableMap<String, MutableList<Long>> = try {
        val j = JSONObject(historyFile.readText())
        j.keys().asSequence().associateWith { k -> j.getJSONArray(k).let { arr -> (0 until arr.length()).map { arr.getLong(it) }.toMutableList() } }.toMutableMap()
    } catch (_: Exception) { mutableMapOf() }
    var historyTick by mutableStateOf(0); private set

    fun recordLaunch(a: AppItem, at: Long = System.currentTimeMillis()) {
        val n = (counts[a.key] ?: 0) + 1
        counts = counts + (a.key to n)
        prefs.edit().putInt("lc|${a.key}", n).apply()
        val cutoff = at - 60L * 24 * 3600_000
        synchronized(history) {
            history.getOrPut(a.key) { mutableListOf() }.apply { add(at); removeAll { it < cutoff } }
            val out = JSONObject(); history.forEach { (k, v) -> out.put(k, JSONArray(v)) }
            try { historyFile.writeText(out.toString()) } catch (_: Exception) {}
        }
        historyTick++
    }
    /** Opens since [since] (epoch ms); 0 = all time (uses the lifetime counter). */
    fun opens(a: AppItem, since: Long): Int = if (since <= 0) counts[a.key] ?: 0 else synchronized(history) { history[a.key]?.count { it >= since } ?: 0 }

    fun saveFavorites(keys: List<String>) { favorites = keys.distinct(); prefs.edit().putString("fav", favorites.joinToString("|")).apply() }

    /* ---------- app shortcuts (only when Siftra is the default home) ---------- */
    fun shortcuts(a: AppItem): List<android.content.pm.ShortcutInfo> = try {
        if (!la.hasShortcutHostPermission()) emptyList()
        else la.getShortcuts(LauncherApps.ShortcutQuery().setPackage(a.pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), a.user)
            ?.filter { it.isEnabled }?.sortedBy { it.rank }?.take(4).orEmpty()
    } catch (_: Exception) { emptyList() }
    fun shortcutIcon(si: android.content.pm.ShortcutInfo): ImageBitmap? = try {
        la.getShortcutIconDrawable(si, ctx.resources.displayMetrics.densityDpi)?.let { d ->
            val px = (40 * ctx.resources.displayMetrics.density).toInt()
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, px, px); d.draw(Canvas(b)) }.asImageBitmap()
        }
    } catch (_: Exception) { null }
    fun startShortcut(si: android.content.pm.ShortcutInfo) { try { la.startShortcut(si, null, null) } catch (_: Exception) {} }

    fun isFav(a: AppItem) = a.key in favorites
    fun toggleFav(a: AppItem) {
        favorites = if (isFav(a)) favorites - a.key else favorites + a.key
        prefs.edit().putString("fav", favorites.joinToString("|")).apply()
    }
    fun move(a: AppItem, g: Group) { prefs.edit().putString("ov|${a.pkg}", g.name).apply(); regroup() }
    fun hide(a: AppItem) { hidden = hidden + a.pkg; prefs.edit().putStringSet("hidden", hidden).putLong("hid|${a.pkg}", System.currentTimeMillis()).apply() }
    fun unhide(pkg: String) { hidden = hidden - pkg; prefs.edit().putStringSet("hidden", hidden).remove("hid|$pkg").apply() }
    fun unhideAll() { hidden = emptySet(); prefs.edit().putStringSet("hidden", hidden).apply() }
    fun hiddenAt(pkg: String) = prefs.getLong("hid|$pkg", 0L)
    /** Hidden apps that are still installed, newest first. */
    fun hiddenApps() = apps.filter { it.pkg in hidden }.distinctBy { it.pkg }.sortedByDescending { hiddenAt(it.pkg) }

    /* ---------- sorting stats ---------- */
    /* ---------- recent searches (on-device, last 6) ---------- */
    var recent by mutableStateOf(prefs.getString("recent", "")!!.split('\u0001').filter { it.isNotBlank() }); private set
    fun addRecent(q: String) {
        val t = q.trim(); if (t.isEmpty()) return
        recent = (listOf(t) + recent.filterNot { it.equals(t, ignoreCase = true) }).take(6)
        prefs.edit().putString("recent", recent.joinToString("\u0001")).apply()
    }

    fun manualMoves() = prefs.all.keys.count { it.startsWith("ov|") }
    /** (sorted by on-device rules, placed by Gemini, moved by you) */
    fun sortStats(): Triple<Int, Int, Int> {
        var rules = 0; var ai = 0; var manual = 0
        val learned = learned()
        apps.distinctBy { it.pkg }.forEach { a ->
            when {
                override(a) != null -> manual++
                Rules.of(a) != null || learned[devPrefix(a.pkg)] != null -> rules++
                ai(a) != null -> ai++
                else -> rules++   // fell to Other without Gemini
            }
        }
        return Triple(rules, ai, manual)
    }

    /* ---------- Gemini key check ---------- */
    var keyState by mutableStateOf<Boolean?>(null)   // null unknown, true valid, false invalid
    suspend fun validateKey() {
        if (geminiKey.isBlank()) { keyState = null; return }
        keyState = withContext(Dispatchers.IO) {
            try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 10_000; c.setRequestProperty("x-goog-api-key", geminiKey)
                c.responseCode in 200..299
            } catch (_: Exception) { null }
        }
    }
    fun appInfo(a: AppItem) { try { la.startAppDetailsActivity(a.component, a.user, null, null) } catch (_: Exception) {} }
    fun saveKey(k: String) { geminiKey = k.trim(); prefs.edit().putString("key", geminiKey).apply() }
    fun setExtras(v: Boolean) { showExtras = v; prefs.edit().putBoolean("extras", v).apply() }
}
