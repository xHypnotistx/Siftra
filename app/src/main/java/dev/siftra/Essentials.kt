package dev.siftra

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/* ======================= notification dots ======================= */

/** Unread notifications per app ("package#user"), filled by [DotService] while Siftra has notification access. */
object Dots {
    val counts = mutableStateMapOf<String, Int>()
    var connected by mutableStateOf(false)
    /** Mirrors the "Show dots" setting. */
    var show by mutableStateOf(true)
    fun keyOf(pkg: String, user: Int) = "$pkg#$user"
    fun has(a: AppItem) = counts.containsKey(keyOf(a.pkg, a.user.hashCode()))

    /** Replace the counts, touching only entries that changed so unrelated icons don't redraw. */
    fun set(m: Map<String, Int>) {
        counts.keys.filter { it !in m }.forEach { counts.remove(it) }
        m.forEach { (k, v) -> if (counts[k] != v) counts[k] = v }
    }
}

class DotService : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; Dots.connected = true; rebuild() }
    override fun onListenerDisconnected() { instance = null; Dots.connected = false; Dots.counts.clear() }
    override fun onNotificationPosted(sbn: StatusBarNotification?, map: RankingMap?) = rebuild()
    override fun onNotificationRemoved(sbn: StatusBarNotification?, map: RankingMap?) = rebuild()
    override fun onNotificationRankingUpdate(map: RankingMap?) = rebuild()

    private fun rebuild() {
        val active = try { activeNotifications } catch (_: Exception) { null } ?: return
        val ranking = try { currentRanking } catch (_: Exception) { null }
        val tmp = Ranking()
        val m = HashMap<String, Int>()
        for (sbn in active) {
            if (skip(sbn, ranking, tmp)) continue
            val k = Dots.keyOf(sbn.packageName, sbn.user.hashCode())
            m[k] = (m[k] ?: 0) + 1
        }
        Dots.set(m)
    }

    /** Same rules as the stock launcher: respect "show dot" per app/channel, no group headers, no empty or ongoing legacy ones. */
    private fun skip(sbn: StatusBarNotification, r: RankingMap?, tmp: Ranking): Boolean {
        val n = sbn.notification
        if (r != null && r.getRanking(sbn.key, tmp)) {
            if (!tmp.canShowBadge()) return true
            if (tmp.channel?.id == NotificationChannel.DEFAULT_CHANNEL_ID && n.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        }
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)
        return title.isNullOrEmpty() && text.isNullOrEmpty()
    }

    companion object {
        @Volatile var instance: DotService? = null
        fun component(ctx: Context) = ComponentName(ctx, DotService::class.java)
        fun granted(ctx: Context) = try {
            ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(ctx))
        } catch (_: Exception) { false }
        fun settingsIntent(ctx: Context) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(ctx).flattenToString())
        /** Turning dots off lets Android unbind us; turning them back on asks it to bind again. */
        fun setActive(ctx: Context, on: Boolean) {
            try {
                if (on) requestRebind(component(ctx)) else instance?.requestUnbind()
            } catch (_: Exception) {}
            if (!on) { Dots.counts.clear() }
        }
    }
}

/** Small accent dot shown on an app icon. */
@Composable
fun AppDot(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size((size.value * 0.24f).coerceIn(7f, 14f).dp).clip(CircleShape).background(LocalUi.current.accent)
        .border(1.5.dp, Color(0xCC0B0C10), CircleShape))
}

/** Dot beside a group's title/count when any app inside has unread notifications. */
@Composable
fun GroupDot(list: List<AppItem>, modifier: Modifier = Modifier) {
    if (!Dots.show) return
    val any by remember(list) { derivedStateOf { list.any { Dots.has(it) } } }
    if (any) Box(modifier.padding(horizontal = 5.dp).size(7.dp).clip(CircleShape).background(LocalUi.current.accent)
        .semantics { contentDescription = "Unread notifications in this group" })
}

/* ======================= lock + notification panel ======================= */

/** Needed only for double-tap to lock (and a reliable swipe-down). Reads no screen content. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockService? = null
        fun component(ctx: Context) = ComponentName(ctx, LockService::class.java)
        fun enabled(ctx: Context): Boolean {
            val on = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = component(ctx)
            return on.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
        /** Straight to Siftra's switch where the phone allows it, otherwise the accessibility list. */
        fun open(ctx: Context) {
            val direct = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component(ctx).flattenToString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(direct) } catch (_: Exception) {
                try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        }
        fun lock() = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        fun notifications() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
    }
}

object Gestures {
    /** Accessibility first (always works); otherwise the status bar call launchers have used for years. */
    fun openNotifications(ctx: Context): Boolean {
        if (LockService.notifications()) return true
        return try {
            val sb = ctx.getSystemService("statusbar") ?: return false
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
            true
        } catch (_: Throwable) { false }
    }

    fun appInfo(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
}

/** Increments every time the activity resumes: re-check permissions after the user comes back from system settings. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalContext.current as? LifecycleOwner
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    return tick
}

/* ======================= Settings › Gestures & dots ======================= */

@Composable
fun GesturesPage(s: Siftra, back: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val dotAccess = remember(tick) { DotService.granted(ctx) }
    val lockOn = remember(tick) { LockService.enabled(ctx) }
    fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} } }
    Page {
        Header("Gestures & dots", back)
        SCard(pad = 14.dp) {
            T("Notification dots", 16.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            T(when {
                !dotAccess -> "A dot on apps with unread notifications, and on their group. Needs notification access; Siftra only counts them, it never reads or stores what they say."
                !s.dots -> "Off."
                else -> "On. ${Dots.counts.size} app${if (Dots.counts.size == 1) "" else "s"} with unread notifications right now."
            }, 12.5.sp, color = muted(), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            if (!dotAccess) SButton("Allow notification access", true, "bell") { s.setDotsOn(true); open(DotService.settingsIntent(ctx)) }
            else SToggleRow("Show dots", s.dots) { s.setDotsOn(it); DotService.setActive(ctx, it) }
        }
        SCard(pad = 14.dp) {
            T("Swipe down for notifications", 16.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            T("Swipe down on the clock or on the groups (when they're scrolled to the top) to open the notification panel.", 12.5.sp, color = muted(), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            SToggleRow("Swipe down", s.swipeDown) { s.setSwipeDownOn(it) }
        }
        SCard(pad = 14.dp) {
            T("Double-tap to lock", 16.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            T(if (lockOn) "Double-tap any empty space on the home screen to lock the phone. Fingerprint and face unlock keep working."
              else "Android only lets apps lock the screen through an accessibility service. Siftra's service does nothing else: it can't see the screen or what you type.",
                12.5.sp, color = muted(), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            if (!lockOn) SButton("Turn on Siftra's lock service", true, "lock") { s.setDoubleTapLockOn(true); LockService.open(ctx) }
            else SToggleRow("Double-tap to lock", s.doubleTapLock) { s.setDoubleTapLockOn(it) }
        }
        if (!dotAccess || !lockOn) SCard(pad = 14.dp) {
            T("If Android says “Restricted setting”", 13.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            T("Apps installed from an APK need one extra step: open Siftra's App info, tap ⋮ at the top right, choose “Allow restricted settings”, then come back and try again.",
                12.5.sp, color = muted(), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            SButton("Open App info", false, "info") { open(Gestures.appInfo(ctx)) }
        }
    }
}
