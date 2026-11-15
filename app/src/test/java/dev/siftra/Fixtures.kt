package dev.siftra

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.graphics.asImageBitmap

private val tinyIcon by lazy { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap() }

fun app(pkg: String, label: String, system: Boolean = false, category: Int = ApplicationInfo.CATEGORY_UNDEFINED, mono: Boolean = false) = AppItem(
    key = "$pkg/.Main#0", pkg = pkg, label = label, icon = tinyIcon,
    component = ComponentName(pkg, "$pkg.Main"), user = Process.myUserHandle(),
    info = ApplicationInfo().apply {
        packageName = pkg; flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0; this.category = category
    },
    mono = if (mono) tinyIcon else null,
)

/** A realistic Turkish Galaxy S23 app list with the group each app should land in. */
val CATALOG: List<Pair<AppItem, Group>> = listOf(
    // government
    app("tr.gov.turkiye.edevlet.kapisi", "e-Devlet") to Group.GOV,
    app("tr.gov.saglik.enabiz", "e-Nabız") to Group.GOV,
    app("tr.gov.saglik.mhrsmobil", "MHRS") to Group.GOV,
    app("tr.gov.osym.ais", "ÖSYM AİS") to Group.GOV,
    app("tr.gov.gib.ivd", "İnteraktif Vergi Dairesi") to Group.GOV,
    // inspiration
    app("com.pinterest", "Pinterest") to Group.INSPIRE,
    app("it.savee.app", "Savee") to Group.INSPIRE,
    app("na.are.android", "Are.na") to Group.INSPIRE,
    app("so.cosmos.app", "Cosmos") to Group.INSPIRE,
    app("com.mymind.app", "mymind") to Group.INSPIRE,
    app("com.behance.behance", "Behance") to Group.INSPIRE,
    app("com.artstation.mobile", "ArtStation") to Group.INSPIRE,
    // samsung
    app("com.samsung.android.dialer", "Phone", system = true) to Group.SAMSUNG,
    app("com.samsung.android.messaging", "Messages", system = true) to Group.SAMSUNG,
    app("com.sec.android.app.myfiles", "My Files", system = true) to Group.SAMSUNG,
    app("com.samsung.android.app.notes", "Samsung Notes", system = true) to Group.SAMSUNG,
    app("com.sec.android.app.camera", "Camera", system = true) to Group.SAMSUNG,
    app("com.android.settings", "Settings", system = true) to Group.SAMSUNG,
    app("com.samsung.android.bixby.agent", "Bixby", system = true) to Group.SAMSUNG_EXTRA,
    app("com.samsung.android.app.tips", "Tips", system = true) to Group.SAMSUNG_EXTRA,
    app("com.samsung.android.arzone", "AR Zone", system = true) to Group.SAMSUNG_EXTRA,
    app("com.samsung.android.tvplus", "Samsung TV Plus", system = true) to Group.SAMSUNG_EXTRA,
    // google
    app("com.google.android.apps.photos", "Photos") to Group.GOOGLE,
    app("com.google.android.apps.nbu.files", "Files by Google") to Group.GOOGLE,
    app("com.google.android.gm", "Gmail", system = true) to Group.GOOGLE,
    app("com.google.android.youtube", "YouTube", system = true) to Group.GOOGLE,
    app("com.android.chrome", "Chrome", system = true) to Group.GOOGLE,
    app("com.android.vending", "Play Store", system = true) to Group.GOOGLE,
    // social
    app("com.instagram.android", "Instagram") to Group.SOCIAL,
    app("com.whatsapp", "WhatsApp") to Group.SOCIAL,
    app("org.telegram.messenger", "Telegram") to Group.SOCIAL,
    app("com.discord", "Discord") to Group.SOCIAL,
    app("com.bereal.ft", "BeReal.") to Group.SOCIAL,
    app("com.zhiliaoapp.musically", "TikTok") to Group.SOCIAL,
    app("com.twitter.android", "X") to Group.SOCIAL,
    app("com.instagram.barcelona", "Threads") to Group.SOCIAL,
    // media
    app("com.spotify.music", "Spotify") to Group.MEDIA,
    app("com.netflix.mediaclient", "Netflix") to Group.MEDIA,
    app("com.exxen.android", "Exxen") to Group.MEDIA,
    app("tv.twitch.android.app", "Twitch") to Group.MEDIA,
    // games
    app("com.supercell.clashroyale", "Clash Royale", category = ApplicationInfo.CATEGORY_GAME) to Group.GAMES,
    app("com.mojang.minecraftpe", "Minecraft") to Group.GAMES,
    app("com.roblox.client", "Roblox") to Group.GAMES,
    // finance (Turkish banks etc. — several declare CATEGORY_PRODUCTIVITY, rules must win)
    app("com.akbank.android.apps.akbank_direkt", "Akbank") to Group.FINANCE,
    app("com.pozitron.iscep", "İşCep", category = ApplicationInfo.CATEGORY_PRODUCTIVITY) to Group.FINANCE,
    app("com.kuveytturk.mobil", "Kuveyt Türk", category = ApplicationInfo.CATEGORY_PRODUCTIVITY) to Group.FINANCE,
    app("com.ykb.android", "Yapı Kredi") to Group.FINANCE,
    app("com.garanti.cepsubesi", "Garanti BBVA") to Group.FINANCE,
    app("com.ziraat.ziraatmobil", "Ziraat Mobil") to Group.FINANCE,
    app("com.turkiyefinans.mobile", "Türkiye Finans", category = ApplicationInfo.CATEGORY_PRODUCTIVITY) to Group.FINANCE,
    app("com.ondijital.mobile", "ON Dijital", category = ApplicationInfo.CATEGORY_PRODUCTIVITY) to Group.FINANCE,
    app("com.mobillium.papara", "Papara") to Group.FINANCE,
    app("com.binance.dev", "Binance") to Group.FINANCE,
    app("com.money.app", "Money") to Group.FINANCE,
    // shopping
    app("trendyol.com", "Trendyol") to Group.SHOPPING,
    app("com.pozitron.hepsiburada", "Hepsiburada") to Group.SHOPPING,
    app("com.getir", "Getir") to Group.SHOPPING,
    app("com.inovel.app.yemeksepeti", "Yemeksepeti") to Group.SHOPPING,
    app("com.sahibinden", "sahibinden.com") to Group.SHOPPING,
    app("com.letgo.ar", "letgo") to Group.SHOPPING,
    // travel
    app("com.waze", "Waze") to Group.TRAVEL,
    app("com.bitaksi.musteri", "BiTaksi") to Group.TRAVEL,
    app("com.turkishairlines.mobile", "Turkish Airlines") to Group.TRAVEL,
    app("com.belbim.istanbulkart", "İstanbulkart") to Group.TRAVEL,
    app("com.ubercab", "Uber") to Group.TRAVEL,
    // productivity
    app("com.microsoft.office.outlook", "Outlook") to Group.PRODUCTIVITY,
    app("com.microsoft.office.word", "Word") to Group.PRODUCTIVITY,
    app("com.microsoft.office.onenote", "OneNote") to Group.PRODUCTIVITY,
    app("com.notion.id", "Notion") to Group.PRODUCTIVITY,
    app("com.anthropic.claude", "Claude") to Group.PRODUCTIVITY,
    // tools
    app("com.microsoft.swiftkey", "Microsoft SwiftKey") to Group.TOOLS,
    app("com.microsoft.scmx", "MSDefender") to Group.TOOLS,
    app("org.torproject.vpn", "Tor VPN") to Group.TOOLS,
    app("com.rarlab.rar", "RAR") to Group.TOOLS,
    app("com.ttech.android.onlineislem", "Türk Telekom") to Group.TOOLS,
    // made by Mahmut
    app("com.mahmut.quickgrab", "QuickGrab") to Group.MINE,
    app("com.mahmut.wallfit", "Wallfit") to Group.MINE,
    app("com.mahmut.rainbreak", "Rainbreak") to Group.MINE,
    // 0.5.8: wider offline rules, Turkish letter folding, and look-alike words that must not misfile
    app("com.turkcell.bip", "BiP") to Group.SOCIAL,
    app("com.sample.mobilebranch", "İŞCEP") to Group.FINANCE,
    app("com.martitech.marti", "Martı") to Group.TRAVEL,
    app("tr.com.sok.android", "ŞOK Market") to Group.SHOPPING,
    app("com.peakgames.toonblast", "Toon Blast") to Group.GAMES,
    app("com.dreamgames.royalmatch", "Royal Match") to Group.GAMES,
    app("tv.puhutv.android", "puhutv") to Group.MEDIA,
    app("com.trt.tabii", "tabii") to Group.MEDIA,
    app("tr.com.ptt.mobil", "PTT") to Group.GOV,
    app("com.anydesk.anydeskandroid", "AnyDesk") to Group.TOOLS,
    app("com.x8bit.bitwarden", "Bitwarden") to Group.TOOLS,
    app("ai.perplexity.app.android", "Perplexity") to Group.PRODUCTIVITY,
    app("com.flightradar24free", "Flightradar24") to Group.TRAVEL,
    app("com.tencent.mm", "WeChat") to Group.SOCIAL,
    app("com.playgendary.kickthebuddy", "Kick the Buddy", category = ApplicationInfo.CATEGORY_GAME) to Group.GAMES,
    app("org.iggymedia.periodtracker", "Flo Period Tracker") to Group.OTHER,
    app("com.cbs.app", "Paramount+") to Group.OTHER,
)

/** n extra random third-party apps for stress runs. */
fun filler(n: Int, seed: Int = 7): List<AppItem> {
    val r = java.util.Random(seed.toLong())
    val words = listOf("Nova", "Pixel", "Atlas", "Echo", "Lumen", "Drift", "Kite", "Orbit", "Mosaic", "Quill", "Vapor", "Zen")
    return (0 until n).map { i ->
        val w = words[r.nextInt(words.size)] + " " + words[r.nextInt(words.size)]
        app("com.filler$i.app${r.nextInt(9999)}", "$w $i", category = listOf(-1, 0, 1, 2, 3, 4, 5, 6, 7)[r.nextInt(9)])
    }
}
