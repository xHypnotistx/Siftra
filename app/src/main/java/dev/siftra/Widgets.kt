@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.siftra

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/* ======================= model ======================= */

/** One widget on the widget page: its Android id, height in dp, and whether it spans the full width or half. */
data class WidgetSlot(val id: Int, val height: Int, val full: Boolean)

internal object WidgetSlots {
    fun load(json: String?): List<WidgetSlot> = try {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { WidgetSlot(it.getInt("id"), it.getInt("h"), it.optBoolean("full", true)) } }
    } catch (_: Exception) { emptyList() }

    fun save(list: List<WidgetSlot>) = JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("h", it.height).put("full", it.full)) } }.toString()

    /** Starting size from what the widget asks for: cells on Android 12+, otherwise its minimum size. */
    fun defaultSlot(id: Int, info: AppWidgetProviderInfo, density: Float): WidgetSlot {
        val minW = (info.minWidth / density).toInt(); val minH = (info.minHeight / density).toInt()
        val cw = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth else cells(minW)
        val ch = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight else cells(minH)
        val h = (max(minH, ch * 78)).coerceIn(90, 420) / 10 * 10
        return WidgetSlot(id, h, cw >= 3)
    }

    fun cells(dp: Int) = ((dp + 30) / 70).coerceAtLeast(1)
    fun sizeLabel(info: AppWidgetProviderInfo, density: Float): String {
        val w = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth else cells((info.minWidth / density).toInt())
        val h = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight else cells((info.minHeight / density).toInt())
        return "$w×$h"
    }
}

/** The one AppWidgetHost Siftra uses, plus the live widget views so paging away and back doesn't rebuild them. */
object WidgetHost {
    private const val HOST_ID = 0x51F7
    private var host: AppWidgetHost? = null
    private var hostApp: Context? = null
    private val views = HashMap<Int, AppWidgetHostView>()

    fun get(ctx: Context): AppWidgetHost {
        val app = ctx.applicationContext
        host?.let { if (hostApp === app) return it }
        views.clear()
        return AppWidgetHost(app, HOST_ID).also { host = it; hostApp = app }
    }
    fun manager(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx)

    fun view(ctx: Context, id: Int): AppWidgetHostView? {
        views[id]?.let { v -> (v.parent as? ViewGroup)?.removeView(v); return v }
        val info = manager(ctx).getAppWidgetInfo(id) ?: return null
        return try { get(ctx).createView(ctx, id, info).also { views[id] = it } } catch (_: Exception) { null }
    }

    fun delete(ctx: Context, id: Int) { views.remove(id); try { get(ctx).deleteAppWidgetId(id) } catch (_: Exception) {} }
    fun dropViews() { views.clear() }

    /** Ids Android still holds for us but no slot uses (an interrupted add, an old install): free them. */
    fun cleanOrphans(ctx: Context, keep: Set<Int>) {
        try { get(ctx).appWidgetIds.filter { it !in keep }.forEach { delete(ctx, it) } } catch (_: Exception) {}
    }

    fun label(ctx: Context, info: AppWidgetProviderInfo): String =
        try { info.loadLabel(ctx.packageManager) } catch (_: Exception) { null } ?: @Suppress("DEPRECATION") info.label ?: info.provider.shortClassName

    fun preview(ctx: Context, info: AppWidgetProviderInfo): ImageBitmap? = try {
        val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
        d?.let {
            val w = it.intrinsicWidth.takeIf { v -> v > 0 } ?: 240; val h = it.intrinsicHeight.takeIf { v -> v > 0 } ?: 240
            val k = minOf(1f, 420f / max(w, h))
            val b = Bitmap.createBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, b.width, b.height); it.draw(android.graphics.Canvas(b)); b.asImageBitmap()
        }
    } catch (_: Exception) { null }

    /** Every widget on the phone, grouped by app, for the picker. */
    fun providers(ctx: Context): List<Pair<String, List<AppWidgetProviderInfo>>> {
        val m = manager(ctx)
        val users = try { ctx.getSystemService(LauncherApps::class.java).profiles } catch (_: Exception) { emptyList() }
        val me = android.os.Process.myUserHandle()
        val all = (m.installedProviders + users.filter { it != me }.flatMap { u -> try { m.getInstalledProvidersForProfile(u) } catch (_: Exception) { emptyList() } })
            .distinctBy { it.provider.flattenToString() + it.profile.hashCode() }
        return all.groupBy { it.provider.packageName }.toList()
    }
}

/** Keeps widgets updating only while Siftra is on screen. */
@Composable
fun WidgetHostLifecycle() {
    val ctx = LocalContext.current
    val owner = ctx as? LifecycleOwner ?: return
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> try { WidgetHost.get(ctx).startListening() } catch (_: Exception) {}
                Lifecycle.Event.ON_STOP -> try { WidgetHost.get(ctx).stopListening() } catch (_: Exception) {}
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); WidgetHost.dropViews() }
    }
}

/** Activities that can run a widget's own setup screen and report back. */
interface WidgetConfigurator { fun configureWidget(id: Int, done: (Boolean) -> Unit) }

/* ======================= the page left of home ======================= */

@Composable
fun WidgetsPage(s: Siftra, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
    val density = LocalDensity.current.density

    fun finish(id: Int, info: AppWidgetProviderInfo) {
        val needsSetup = info.configure != null &&
            (Build.VERSION.SDK_INT < 28 || info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0)
        val conf = ctx as? WidgetConfigurator
        if (needsSetup && conf != null) conf.configureWidget(id) { ok ->
            if (ok) s.addWidget(WidgetSlots.defaultSlot(id, info, density)) else WidgetHost.delete(ctx, id)
        } else s.addWidget(WidgetSlots.defaultSlot(id, info, density))
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val (id, info) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (r.resultCode == Activity.RESULT_OK) finish(id, info) else WidgetHost.delete(ctx, id)
    }
    fun add(info: AppWidgetProviderInfo) {
        picking = false
        val id = try { WidgetHost.get(ctx).allocateAppWidgetId() } catch (_: Exception) { return }
        val m = WidgetHost.manager(ctx)
        val ok = try {
            if (info.profile == android.os.Process.myUserHandle()) m.bindAppWidgetIdIfAllowed(id, info.provider, Bundle())
            else m.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, Bundle())
        } catch (_: Exception) { false }
        if (ok) finish(id, info) else {
            pending = id to info
            try {
                bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile))
            } catch (_: Exception) { pending = null; WidgetHost.delete(ctx, id) }
        }
    }

    BackHandler(editing) { editing = false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Widgets", 40.sp, display = true, lineHeight = 42.sp)
                    T(if (editing) "Resize, reorder or remove, then tap Done" else "Swipe left for home", 12.sp, color = muted())
                }
                if (s.widgets.isNotEmpty()) PillButton(if (editing) "Done" else "Edit", editing) { editing = !editing }
                Spacer(Modifier.width(8.dp))
                PillButton("+ Add", false) { editing = false; picking = true }
            }
            if (s.widgets.isEmpty()) SCard(pad = 18.dp) {
                T("Weather, music, calendar, notes…", 20.sp, display = true)
                Spacer(Modifier.height(6.dp))
                T("Any Android widget can live here, one swipe right from home. Your groups stay exactly as they are.", 13.sp, color = muted(), lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
                SButton("Add a widget", true, "plus") { picking = true }
            }
            // half-width widgets pair up; full-width ones take a row
            val rows = remember(s.widgets.toList()) {
                val out = mutableListOf<List<WidgetSlot>>(); var half: WidgetSlot? = null
                s.widgets.forEach { w ->
                    if (w.full) { half?.let { out += listOf(it); half = null }; out += listOf(w) }
                    else if (half == null) half = w else { out += listOf(half!!, w); half = null }
                }
                half?.let { out += listOf(it) }
                out
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { w -> WidgetCell(s, w, editing, if (w.full) Modifier.fillMaxWidth() else Modifier.weight(1f)) }
                    if (row.size == 1 && !row[0].full) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (picking) WidgetPicker(onClose = { picking = false }, onPick = ::add)
    }
}

@Composable
private fun PillButton(text: String, accent: Boolean, onClick: () -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.clip(RoundedCornerShape(20.dp)).then(if (accent) Modifier.background(ui.accent) else Modifier.glass(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(text, 13.sp, weight = FontWeight.SemiBold, color = if (accent) onAccent() else Color.Unspecified)
    }
}

@Composable
private fun WidgetCell(s: Siftra, w: WidgetSlot, editing: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val view = remember(w.id) { WidgetHost.view(ctx, w.id) }
    Box(modifier.height(w.height.dp).animateContentSize().clip(RoundedCornerShape(24.dp)).onSizeChanged { size = it }) {
        if (view == null) Column(Modifier.fillMaxSize().glass(24.dp).padding(16.dp), verticalArrangement = Arrangement.Center) {
            T("This widget is gone", 15.sp, weight = FontWeight.SemiBold)
            T("Its app was removed or updated. Remove it here.", 12.sp, color = muted())
        } else {
            AndroidView({ view }, Modifier.fillMaxSize())
            LaunchedEffect(size) {
                if (size != IntSize.Zero && Build.VERSION.SDK_INT >= 31) with(density) {
                    try { view.updateAppWidgetSize(Bundle(), listOf(SizeF(size.width.toDp().value, size.height.toDp().value))) } catch (_: Exception) {}
                }
            }
        }
        if (editing || view == null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (editing) 0.5f else 0f)).noRipple { }) {
            FlowRow(Modifier.align(Alignment.Center).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editing) {
                    EditChip("↑", "Move up") { s.moveWidget(w.id, -1) }
                    EditChip("↓", "Move down") { s.moveWidget(w.id, 1) }
                    EditChip("−", "Shorter") { s.resizeWidget(w.id, -40) }
                    EditChip("+", "Taller") { s.resizeWidget(w.id, 40) }
                    EditChip(if (w.full) "½" else "▭", if (w.full) "Half width" else "Full width") { s.toggleWidgetWidth(w.id) }
                }
                EditChip("✕", "Remove widget") { s.removeWidget(w.id); WidgetHost.delete(ctx, w.id) }
            }
        }
    }
}

@Composable
private fun EditChip(t: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).clickable(onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        T(t, 17.sp, weight = FontWeight.SemiBold, color = Color(0xFF111216))
    }
}

/* ======================= picker ======================= */

@Composable
private fun WidgetPicker(onClose: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val density = LocalDensity.current.density
    var q by remember { mutableStateOf("") }
    val all by produceState<List<Triple<String, String, List<AppWidgetProviderInfo>>>?>(null) {
        value = withContext(Dispatchers.IO) {
            WidgetHost.providers(ctx).map { (pkg, list) ->
                val app = try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                Triple(pkg, app, list)
            }.sortedBy { it.second.lowercase() }
        }
    }
    Sheet(onClose) {
        T("Add a widget", 28.sp, display = true)
        Box(Modifier.fillMaxWidth().height(48.dp).glass(16.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) T("Search widgets", 15.sp, color = muted())
            BasicTextField(q, { q = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Search widgets" }, singleLine = true,
                textStyle = TextStyle(LocalInk.current, 15.sp, fontFamily = ui.body), cursorBrush = SolidColor(ui.accent))
        }
        val list = all
        when {
            list == null -> T("Looking for widgets…", 13.sp, color = muted())
            list.isEmpty() -> T("No app on this phone offers a widget.", 13.sp, color = muted())
            else -> {
                val shown = list.filter { (_, app, ws) -> q.isBlank() || app.contains(q, true) || ws.any { WidgetHost.label(ctx, it).contains(q, true) } }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(shown, key = { it.first }) { (_, app, ws) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(app, 15.sp, weight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ws, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                                    val label = remember(info) { WidgetHost.label(ctx, info) }
                                    val img by produceState<ImageBitmap?>(null, info) { value = withContext(Dispatchers.IO) { WidgetHost.preview(ctx, info) } }
                                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).glass(18.dp).clickable { onPick(info) }
                                        .semantics { contentDescription = "Add $label" }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                                        }
                                        T(label, 12.5.sp, maxLines = 1)
                                        T(WidgetSlots.sizeLabel(info, density), 11.sp, color = muted())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
