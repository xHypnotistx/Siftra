package dev.siftra

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/* ======================= backdrop ======================= */

enum class Backdrop(val title: String) { AURORA("Aurora"), YOU("You"), EMBER("Ember"), MONO("Mono"), LIME("Lime"), PASTEL("Pastel"), PHOTO("Photo"), WALLPAPER("Wallpaper") }

private class Palette(val base: Color, val blobs: List<Color>)

private val palettes = mapOf(
    Backdrop.AURORA to Palette(Color(0xFF05070F), listOf(Color(0xFF1F4BFF), Color(0xFF7B2CFF), Color(0xFF00C2FF), Color(0xFF2A1A8F))),
    Backdrop.EMBER to Palette(Color(0xFF070912), listOf(Color(0xFF14307A), Color(0xFFFF6A2B), Color(0xFFB0124A), Color(0xFF3FA9F5))),
    Backdrop.LIME to Palette(Color(0xFF0A0B08), listOf(Color(0x4DC6FF3D), Color(0x8C4A6014), Color(0x1FC6FF3D))),
    Backdrop.PASTEL to Palette(Color(0xFFA29CF5), listOf(Color(0xFFFFFFFF), Color(0xFFFFC2D6), Color(0xFFC8F3FF), Color(0xFF7F86F2))),
    Backdrop.MONO to Palette(Color(0xFF0A0A0B), listOf(Color(0xFF8E8E93), Color(0xFF3A3A3F), Color(0xFFD9D9DE), Color(0xFF26262A))),
)

/** Everything a glass surface needs to paint the backdrop that sits behind it. */
class GlassEnv(
    val mode: Backdrop, val photo: ImageBitmap?, val ribbed: Boolean,
    val time: State<Float>, val screen: State<Size>,
    val you: List<Color> = emptyList(), val youBase: Color = Color.Black,
    /** The backdrop, pre-blurred once at 1/[SHARED_SCALE] size; every glass panel samples it instead of blurring its own copy. */
    val shared: GraphicsLayer? = null,
)

const val SHARED_SCALE = 4f

/** Called by the screen-wide backdrop each time it repaints: refreshes the shared blurred copy. */
fun DrawScope.recordSharedBackdrop(env: GlassEnv) {
    val layer = env.shared ?: return
    val w = (size.width / SHARED_SCALE).toInt().coerceAtLeast(1); val h = (size.height / SHARED_SCALE).toInt().coerceAtLeast(1)
    layer.renderEffect = blurEffect(env.ribbed, density)
    layer.record(this, layoutDirection, IntSize(w, h)) { scale(1f / SHARED_SCALE, 1f / SHARED_SCALE, Offset.Zero) { drawBackdrop(env) } }
}

val LocalGlass = staticCompositionLocalOf<GlassEnv?> { null }

fun DrawScope.drawBackdrop(env: GlassEnv) {
    val scr = env.screen.value
    val w = scr.width; val h = scr.height
    if (w <= 0f) return
    if (env.mode == Backdrop.PHOTO) {
        val img = env.photo ?: return
        val sc = max(w / img.width, h / img.height)
        val dw = (img.width * sc).toInt(); val dh = (img.height * sc).toInt()
        drawImage(img, dstOffset = IntOffset(((w - dw) / 2).toInt(), ((h - dh) / 2).toInt()), dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.Low)
        return
    }
    val p = if (env.mode == Backdrop.YOU) Palette(env.youBase, env.you) else palettes[env.mode] ?: return
    val t = env.time.value
    drawRect(p.base, size = scr)
    p.blobs.forEachIndexed { i, c ->
        val a = t * 2f * PI.toFloat() / 48f + i * 1.7f            // one slow loop ≈ 48 s
        val cx = w * (0.5f + 0.42f * sin(a * (0.8f + i * 0.13f) + i))
        val cy = h * (0.5f + 0.42f * cos(a * (0.6f + i * 0.17f) + i * 2.1f))
        val r = max(w, h) * (0.42f + 0.07f * sin(a * 1.3f + i))
        drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = c.alpha * 0.35f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    }
}

/* ======================= textured-glass GPU effect ======================= */

private const val GLASS_AGSL = """
uniform shader content;
uniform float strength;
uniform float ribbed;
uniform float rib;
float h(float2 p) { float3 q = fract(float3(p.xyx) * 0.1031); q += dot(q, q.yzx + 33.33); return fract((q.x + q.y) * q.z); }
float n(float2 p) {
    float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h(i), h(i + float2(1.0, 0.0)), u.x), mix(h(i + float2(0.0, 1.0)), h(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 xy) {
    float2 off;
    float shade = 0.0;
    if (ribbed > 0.5) {
        float ph = fract(xy.x / rib);
        off = float2((ph - 0.5) * strength * 2.2, 0.0);
        shade = (0.5 - abs(ph - 0.5)) * 0.12 - 0.03;
    } else {
        float2 q = xy * 0.055;
        off = (float2(n(q), n(q + 19.7)) - 0.5) * strength * 1.6
            + (float2(h(xy), h(xy + 5.3)) - 0.5) * strength * 0.9;
    }
    half4 c = content.eval(xy + off);
    float g = h(xy * 1.37) - 0.5;
    c.rgb = c.rgb + half3(g * 0.08 + shade);
    return c;
}
"""

private val effects = HashMap<String, RenderEffect?>()

/** Blur for the shared, quarter-size backdrop (radius scaled down to match). */
private fun blurEffect(ribbed: Boolean, density: Float): RenderEffect = effects.getOrPut("b$ribbed$density") {
    val r = (if (ribbed) 7f else 13f) * density / SHARED_SCALE
    android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP).asComposeRenderEffect()
}!!

/** Per-panel frosted/ribbed distortion + grain at full resolution (Android 13+); null = plain blur. */
private fun distortEffect(ribbed: Boolean, density: Float): RenderEffect? = effects.getOrPut("d$ribbed$density") {
    if (Build.VERSION.SDK_INT >= 33) try {
        val sh = RuntimeShader(GLASS_AGSL).apply {
            setFloatUniform("strength", (if (ribbed) 9f else 4f) * density)
            setFloatUniform("ribbed", if (ribbed) 1f else 0f)
            setFloatUniform("rib", 14f * density)
        }
        android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content").asComposeRenderEffect()
    } catch (_: Throwable) { null } else null
}

/* ======================= the glass modifier ======================= */

private val grain: ShaderBrush by lazy {
    val n = 160
    val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = java.util.Random(42)
    for (y in 0 until n) for (x in 0 until n) {
        val v = if (rnd.nextBoolean()) 255 else 0
        b.setPixel(x, y, android.graphics.Color.argb(6 + rnd.nextInt(28), v, v, v))
    }
    ShaderBrush(ImageShader(b.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private val sheen = Brush.linearGradient(listOf(Color(0x24FFFFFF), Color.Transparent, Color(0x0DFFFFFF)), Offset.Zero, Offset.Infinite)
private val edge = Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF)))

/**
 * Textured glass. With a Siftra backdrop it shows the backdrop behind it blurred + distorted
 * (real glass). With the system wallpaper it falls back to a tinted grain plate.
 * dark = smokier tint for menus that must stay readable.
 */
fun Modifier.glass(r: Dp = 26.dp, strong: Boolean = false, dark: Boolean = false): Modifier = composed {
    val env = LocalGlass.current
    val ui = LocalUi.current
    val light = LocalInk.current.luminance() < 0.5f
    val wash = if (ui.you) ui.accent.copy(alpha = 0.07f) else Color.Transparent
    val rim = if (light) Brush.verticalGradient(listOf(Color(0xA6FFFFFF), Color(0x40FFFFFF))) else if (ui.you) Brush.verticalGradient(listOf(ui.soft.copy(alpha = 0.55f), Color(0x14FFFFFF))) else edge
    val shape = RoundedCornerShape(r)
    val real = env?.shared != null && env.mode != Backdrop.WALLPAPER && (env.mode != Backdrop.PHOTO || env.photo != null)
    val tint = when {
        light && !dark -> Brush.verticalGradient(listOf(Color(0x6BFFFFFF), Color(0x29FFFFFF)))
        dark && real -> Brush.verticalGradient(listOf(Color(0x59000000), Color(0x73000000)))
        dark -> Brush.verticalGradient(listOf(Color(0xD9202229), Color(0xE6141519)))
        real && strong -> Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF)))
        real -> Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF)))
        strong -> Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF)))
        else -> Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF)))
    }
    val clipped = this.clip(shape)
    val painted = if (real) {
        var pos by remember { mutableStateOf(Offset.Zero) }
        clipped.onGloballyPositioned { pos = it.positionInWindow() }.drawWithCache {
            val layer = obtainGraphicsLayer()
            layer.renderEffect = distortEffect(env!!.ribbed, density)
            val shared = env.shared!!
            onDrawBehind {
                val p = pos
                layer.record(this, layoutDirection, IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                    translate(-p.x, -p.y) { scale(SHARED_SCALE, SHARED_SCALE, Offset.Zero) { drawLayer(shared) } }
                }
                drawLayer(layer)
                drawRect(tint); drawRect(wash); drawRect(sheen); drawRect(grain, alpha = 0.6f)
            }
        }
    } else clipped.drawBehind { drawRect(tint); drawRect(wash); drawRect(sheen); drawRect(grain, alpha = 0.75f) }
    painted.border(1.dp, rim, shape)
}
