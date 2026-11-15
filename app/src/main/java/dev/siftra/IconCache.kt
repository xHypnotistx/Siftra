package dev.siftra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Icons and names of every app, kept on disk so a cold start doesn't have to open hundreds of APKs.
 * An entry is reused while the app's APK path is unchanged (it changes on every update). The whole
 * cache is dropped when the system build, language or icon size changes.
 */
internal class IconCache(private val ctx: Context, private val px: Int, private val dir: File = File(ctx.cacheDir, "icons")) {
    class Meta(val stamp: String, val label: String, val file: String, val mono: Boolean)

    private val index get() = File(dir, "index.json")
    private val sig get() = "2|${Build.FINGERPRINT}|${ctx.resources.configuration.locales.toLanguageTags()}|$px"

    fun read(): Map<String, Meta> = try {
        val o = JSONObject(index.readText())
        if (o.optString("sig") != sig) emptyMap() else {
            val a = o.getJSONArray("apps")
            (0 until a.length()).associate { i ->
                val e = a.getJSONObject(i)
                e.getString("k") to Meta(e.getString("s"), e.getString("l"), e.getString("f"), e.optBoolean("m"))
            }
        }
    } catch (_: Exception) { emptyMap() }

    /** Decodes straight into GPU memory where possible (no CPU copy, no upload while scrolling). */
    fun decode(m: Meta): Pair<ImageBitmap, ImageBitmap?>? {
        val icon = bitmap(File(dir, m.file + ".png")) ?: return null
        val mono = if (m.mono) bitmap(File(dir, m.file + ".m.png")) else null
        return icon to mono
    }

    private fun bitmap(f: File): ImageBitmap? {
        if (!f.exists()) return null
        val hw = if (Build.FINGERPRINT != "robolectric") try {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE })
        } catch (_: Throwable) { null } else null
        return (hw ?: BitmapFactory.decodeFile(f.path))?.asImageBitmap()
    }

    /** Writes the files that are missing or out of date, removes the ones no longer needed, then the index. */
    fun write(items: List<AppItem>) {
        dir.mkdirs()
        val old = read()
        val keep = HashSet<String>()
        val arr = JSONArray()
        for (a in items) {
            val name = fileName(a.key)
            val png = File(dir, "$name.png")
            val fresh = old[a.key]?.stamp == a.stamp && png.exists()
            if (!fresh && !save(a.icon, png)) continue
            val monoFile = File(dir, "$name.m.png")
            val hasMono = a.mono != null && (fresh && monoFile.exists() || save(a.mono, monoFile))
            keep += "$name.png"; if (hasMono) keep += "$name.m.png"
            arr.put(JSONObject().put("k", a.key).put("s", a.stamp).put("l", a.label).put("f", name).put("m", hasMono))
        }
        dir.listFiles()?.forEach { if (it.name.endsWith(".png") && it.name !in keep) it.delete() }
        val tmp = File(dir, "index.tmp")
        tmp.writeText(JSONObject().put("sig", sig).put("apps", arr).toString())
        tmp.renameTo(index)
    }

    fun clear() { dir.deleteRecursively() }

    private fun save(img: ImageBitmap?, f: File): Boolean {
        val b = img?.asAndroidBitmap() ?: return false
        if (b.config == Bitmap.Config.HARDWARE) return f.exists()      // came from the cache, the file is already there
        return try { f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { false }
    }

    companion object {
        fun fileName(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160) + "_" + Integer.toHexString(key.hashCode())
    }
}
