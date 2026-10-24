package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.raghavsatyadev.tvfreedom.design.TvLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun Tile(look: TvLook, pkg: String, focused: Boolean, accent: Color?, k: Float, modifier: Modifier) {
    val size = 78.dp * k
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (pkg == "+") {
                Box(Modifier.size(size * 0.9f).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White.copy(alpha = 0.8f), fontSize = (22 * k).sp)
                }
            } else {
                val base = accent ?: appColor(pkg)
                val fill = if (focused) lerp(base, Color.White, 0.55f) else base
                val shape = if (focused) CircleShape else shapeFor(look.shape, pkg)
                val inner: Dp = if (focused) size else size * 0.82f
                Box(Modifier.size(inner).then(if (focused) Modifier.border(2.dp * k, fill, CircleShape).padding(4.dp * k) else Modifier)
                    .clip(shape).background(fill), contentAlignment = Alignment.Center) {
                    Text(appName(pkg).take(1), color = lerp(base, Color.Black, 0.7f), fontSize = (26 * k).sp)
                }
            }
        }
        val shown = pkg != "+" && when (look.names) { "off" -> false; "focus" -> focused; else -> true }
        Text(if (shown) appName(pkg) else "", color = Color.White.copy(alpha = if (focused) 1f else 0.7f), fontSize = (12 * k).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.width(size * 1.4f).padding(top = 4.dp * k))
    }
}

/** A wide app card: the app's colour with its name, a white ring on the chosen one (the TV shows the app's banner). */
@Composable
internal fun Banner(look: TvLook, pkg: String, focused: Boolean, k: Float, modifier: Modifier) {
    val base = appColor(pkg)
    val card = RoundedCornerShape(10.dp * k)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .then(if (focused) Modifier.border(2.dp * k, Color.White, card).padding(3.dp * k) else Modifier)
            .clip(card).background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.45f)))),
            contentAlignment = Alignment.Center) {
            Text(appName(pkg), color = Color.White, fontSize = (15 * k).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp * k))
        }
        val shown = when (look.names) { "off" -> false; "focus" -> focused; else -> true }
        Text(if (shown) appName(pkg) else "", color = Color.White.copy(alpha = if (focused) 1f else 0.7f), fontSize = (11 * k).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp * k))
    }
}

/** The "analog" clock: dial, hour marks, hands at 9:41 and a dot in [tint] (AnalogFace on the TV). */
@Composable
internal fun Dial(tint: Color, k: Float, modifier: Modifier) = Canvas(modifier) {
    val r = size.minDimension / 2f
    val c = center
    drawCircle(Color.White.copy(alpha = 0.08f), r, c)
    for (i in 0 until 12) {
        val a = 2 * PI * i / 12
        val long = i % 3 == 0
        val inner = r * (if (long) 0.76f else 0.84f)
        val outer = r * 0.92f
        drawLine(Color.White.copy(alpha = 0.5f), c + polar(inner, a), c + polar(outer, a),
            (if (long) 3f else 1.5f) * k * density, StrokeCap.Round)
    }
    drawLine(Color.White, c, c + polar(r * 0.5f, 2 * PI * (9 + 41 / 60.0) / 12), 4.5f * k * density, StrokeCap.Round)
    drawLine(Color.White, c, c + polar(r * 0.74f, 2 * PI * 41 / 60), 3f * k * density, StrokeCap.Round)
    drawCircle(tint, 5f * k * density, c)
}

private fun polar(d: Float, a: Double) = Offset(d * sin(a).toFloat(), -d * cos(a).toFloat())

/** The tile shapes the launcher draws (Tint.LOBES / DEPTHS), "" picking one per app like the TV does. */
internal fun shapeFor(shape: String, pkg: String): Shape = when (shape) {
    "circle" -> CircleShape
    "square" -> RoundedCornerShape(30)
    "flower" -> scallop(8, 0.16f)
    else -> SHAPES[abs(pkg.hashCode() % SHAPES.size)]
}

private val SHAPES = listOf(scallop(9, 0.08f), scallop(12, 0.06f), scallop(4, 0.26f), scallop(8, 0.16f), scallop(14, 0.05f),
    scallop(10, 0.11f), scallop(6, 0.12f), scallop(7, 0.10f), scallop(4, 0.16f))

/** A circle with [lobes] soft bumps [depth] deep, fitted in its box. */
private fun scallop(lobes: Int, depth: Float) = GenericShape { size, _ ->
    val r = minOf(size.width, size.height) / 2f / (1f + depth)
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0..180) {
        val a = 2 * PI * i / 180
        val d = r * (1f + depth * cos(lobes * a).toFloat())
        val x = cx + d * cos(a - PI / 2).toFloat()
        val y = cy + d * sin(a - PI / 2).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** An app's own colour where known, else one picked from its name. */
internal fun appColor(pkg: String): Color = BRANDS[pkg] ?: HUES[abs(pkg.hashCode() % HUES.size)]

private val BRANDS = mapOf(
    "com.netflix.ninja" to Color(0xFFE50914),
    "com.google.android.youtube.tv" to Color(0xFFFF4E45),
    "com.google.android.youtube.tvmusic" to Color(0xFFFF4E45),
    "com.amazon.amazonvideo.livingroom" to Color(0xFF1A98FF),
    "in.startv.hotstar" to Color(0xFF1F80E0),
    "com.disney.disneyplus" to Color(0xFF3D6BE0),
    "com.spotify.tv.android" to Color(0xFF1ED760),
    "org.jellyfin.androidtv" to Color(0xFF9B5CE0),
    "com.plexapp.android" to Color(0xFFE5A00D),
    "com.android.vending" to Color(0xFF34A853),
)

private val HUES = listOf(Color(0xFF5B8DEF), Color(0xFFE86A5C), Color(0xFF4CB782), Color(0xFFB57BE8), Color(0xFFE8A84C), Color(0xFF4CB8C4))
