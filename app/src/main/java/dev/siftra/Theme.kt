@file:OptIn(ExperimentalTextApi::class)

package dev.siftra

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ======================= fonts ======================= */

private fun sans(w: Int) = Font(R.font.instrument_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val ClassySans = FontFamily(sans(400), sans(500), sans(600), sans(700))
val ClassySerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/* ======================= Material You ======================= */

/** Colours pulled from the system's wallpaper-based palette (Android 12+). */
class UiTheme(
    val accent: Color, val soft: Color, val container: Color, val onContainer: Color,
    val tertiary: Color, val secondary: Color, val neutralDark: Color,
    val you: Boolean, val body: FontFamily, val display: FontFamily, val themedIcons: Boolean,
)

fun youTheme(ctx: Context, you: Boolean, classy: Boolean, themedIcons: Boolean): UiTheme {
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (you) UiTheme(
        accent = c(android.R.color.system_accent1_200), soft = c(android.R.color.system_accent1_100),
        container = c(android.R.color.system_accent1_700), onContainer = c(android.R.color.system_accent1_50),
        tertiary = c(android.R.color.system_accent3_200), secondary = c(android.R.color.system_accent2_200),
        neutralDark = c(android.R.color.system_neutral1_900),
        you = true, body = if (classy) ClassySans else FontFamily.Default,
        display = if (classy) ClassySerif else FontFamily.Default, themedIcons = themedIcons,
    ) else UiTheme(
        accent = Color.White, soft = Color(0xFFE6E6EA), container = Color(0x40FFFFFF), onContainer = Color.White,
        tertiary = Color(0xFFBFC6FF), secondary = Color(0xFFC9C9D0), neutralDark = Color(0xFF0B0B0E),
        you = false, body = if (classy) ClassySans else FontFamily.Default,
        display = if (classy) ClassySerif else FontFamily.Default, themedIcons = themedIcons,
    )
}

val LocalUi = staticCompositionLocalOf<UiTheme> { error("no theme") }

/** Text colour of the home layer: white on dark backdrops, deep ink on the light Pastel one. */
val LocalInk = staticCompositionLocalOf { Color.White }

/** Darker accents that read well on a light backdrop. */
fun lightVariant(ctx: Context, b: UiTheme): UiTheme {
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (b.you) UiTheme(c(android.R.color.system_accent1_600), c(android.R.color.system_accent1_500), c(android.R.color.system_accent1_100),
        c(android.R.color.system_accent1_800), c(android.R.color.system_accent3_600), c(android.R.color.system_accent2_600),
        b.neutralDark, true, b.body, b.display, b.themedIcons)
    else UiTheme(Color(0xFF5B4FD9), Color(0xFF7A6FF0), Color(0xFFE4E1FF), Color(0xFF2B2370), Color(0xFFD9468F), Color(0xFF0E8FB8),
        b.neutralDark, false, b.body, b.display, b.themedIcons)
}

private val shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 8f)

@Composable
fun T(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
      color: Color = Color.Unspecified, alpha: Float = 1f, display: Boolean = false, italic: Boolean = false,
      maxLines: Int = Int.MAX_VALUE, center: Boolean = false, tracking: TextUnit = TextUnit.Unspecified,
      lineHeight: TextUnit = TextUnit.Unspecified) {
    val ui = LocalUi.current
    val col = if (color == Color.Unspecified) LocalInk.current else color
    BasicText(text, modifier, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(col.copy(alpha = col.alpha * alpha), size, weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, lineHeight = lineHeight,
            fontFamily = if (display) ui.display else ui.body, shadow = if (col.luminance() > 0.5f) shadow else null, letterSpacing = tracking,
            textAlign = if (center) TextAlign.Center else TextAlign.Start))
}

/* ======================= clock styles ======================= */

enum class ClockStyle(val title: String) { MONOLITH("Monolith"), CLASSIC("Classic"), STACKED("Stacked"), SPLIT("Split"), ANALOG("Analog"), MINIMAL("Minimal") }

@Composable
fun ClockView(style: ClockStyle, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000L * (60 - Calendar.getInstance().get(Calendar.SECOND)).coerceAtLeast(1)) } }
    val loc = Locale.getDefault()
    fun f(p: String) = SimpleDateFormat(p, loc).format(now)
    val date = f("EEEE, d MMMM")
    val ink = LocalInk.current
    when (style) {
        ClockStyle.MONOLITH -> Row(modifier, verticalAlignment = Alignment.Top) {
            Box {                                    // tightly stacked digits, minutes tucked under the hours
                T(f("HH"), 104.sp, display = true, lineHeight = 104.sp)
                T(f("mm"), 104.sp, display = true, italic = true, color = ui.accent, lineHeight = 104.sp,
                    modifier = Modifier.padding(top = 86.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(top = 120.dp, end = 4.dp), horizontalAlignment = Alignment.End) {
                T(f("EEEE").uppercase(loc), 11.sp, weight = FontWeight.SemiBold, alpha = 0.75f, tracking = 2.4.sp)
                T(f("d MMM"), 30.sp, display = true, italic = true)
            }
        }
        ClockStyle.CLASSIC -> Column(modifier) {
            T(f("HH:mm"), 64.sp, display = true)
            T(date, 15.sp, alpha = 0.85f, italic = true, display = true)
        }
        ClockStyle.STACKED -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Column {
                T(f("HH"), 58.sp, display = true, modifier = Modifier.offset(y = 8.dp))
                T(f("mm"), 58.sp, display = true, color = ui.accent, modifier = Modifier.offset(y = (-8).dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                T(f("EEEE"), 15.sp, weight = FontWeight.SemiBold)
                T(f("d MMMM"), 14.sp, alpha = 0.75f)
            }
        }
        ClockStyle.SPLIT -> Column(modifier) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(f("HH"), 64.sp, display = true)
                T(":", 64.sp, display = true, alpha = 0.5f)
                T(f("mm"), 64.sp, display = true, italic = true, color = ui.accent)
            }
            T(date.uppercase(loc), 11.sp, weight = FontWeight.SemiBold, alpha = 0.8f, tracking = 2.sp)
        }
        ClockStyle.ANALOG -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val cal = Calendar.getInstance().apply { time = now }
            val m = cal.get(Calendar.MINUTE); val h = cal.get(Calendar.HOUR) + m / 60f
            Canvas(Modifier.size(84.dp)) {
                val r = size.minDimension / 2
                drawCircle(Color.White.copy(alpha = 0.18f), r)
                drawCircle(Color.White.copy(alpha = 0.55f), r, style = Stroke(1.dp.toPx()))
                for (i in 0 until 12) rotate(i * 30f) {
                    drawLine(ink.copy(alpha = if (i % 3 == 0) 0.9f else 0.4f), Offset(center.x, center.y - r + 5.dp.toPx()),
                        Offset(center.x, center.y - r + (if (i % 3 == 0) 11 else 8).dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }
                rotate(h * 30f) { drawLine(ink, center, Offset(center.x, center.y - r * 0.5f), 3.dp.toPx(), StrokeCap.Round) }
                rotate(m * 6f) { drawLine(ui.accent, center, Offset(center.x, center.y - r * 0.78f), 2.dp.toPx(), StrokeCap.Round) }
                drawCircle(ui.accent, 3.dp.toPx())
            }
            Spacer(Modifier.width(16.dp))
            Column {
                T(f("EEEE"), 22.sp, display = true)
                T(f("d MMMM"), 14.sp, alpha = 0.75f, italic = true, display = true)
            }
        }
        ClockStyle.MINIMAL -> Row(modifier, verticalAlignment = Alignment.Bottom) {
            T(f("HH:mm"), 34.sp, weight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            T(date, 13.sp, alpha = 0.7f, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}
