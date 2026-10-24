package io.github.raghavsatyadev.tvfreedom.ui

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
import androidx.compose.ui.graphics.lerp
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

/**
 * The TVFreedom home screen in [look], drawn with [apps] (favourites first), the way the TV draws it: clock, buttons,
 * the chosen app's name, and the favourites row with the first app chosen. [columns] when the look keeps the TV's own.
 */
@Composable
fun LookPreview(look: TvLook, apps: List<String>, columns: Int, modifier: Modifier = Modifier) {
    val cols = (look.columns ?: columns).coerceIn(3, 6)
    val row = apps.take(cols - 1)
    val focused = row.firstOrNull()
    val accent = look.accent.takeIf { it.isNotEmpty() }?.let { parse(it) }
    val tint = accent ?: focused?.let { appColor(it) } ?: Color(0xFF6750A4)
    val back = if (look.trueBlack) Brush.verticalGradient(listOf(Color.Black, Color.Black))
        else Brush.verticalGradient(listOf(lerp(Color(0xFF101014), tint, 0.22f), Color(0xFF0B0B0E)))
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(back)) {
        val k = maxWidth / 960.dp
        val pill = lerp(Color(0xFF2A2A2E), tint, 0.25f)
        Column(Modifier.fillMaxSize().padding(start = 56.dp * k, end = 56.dp * k, top = 26.dp * k, bottom = 18.dp * k)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GOOD EVENING", color = Color.White.copy(alpha = 0.55f), fontSize = (12 * k).sp, letterSpacing = (2.6 * k).sp, maxLines = 1)
                    Text("9:41", color = Color.White, fontSize = (46 * k).sp, lineHeight = (50 * k).sp, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k)) {
                    listOf("Inputs", "Wi-Fi", "Settings").forEach {
                        Text(it, color = Color.White, fontSize = (13 * k).sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(pill).padding(horizontal = 14.dp * k, vertical = 8.dp * k))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (focused != null) Text(appName(focused), color = Color.White, fontSize = (34 * k).sp, maxLines = 1,
                modifier = Modifier.padding(start = 6.dp * k, bottom = 12.dp * k))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp * k)).background(Color(0xFF141416))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp * k)).padding(10.dp * k)) {
                (row + "+").forEach { pkg -> Tile(look, pkg, pkg == focused, accent, k, Modifier.weight(1f)) }
                repeat(cols - row.size - 1) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(look: TvLook, pkg: String, focused: Boolean, accent: Color?, k: Float, modifier: Modifier) {
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

/** The tile shapes the launcher draws (Tint.LOBES / DEPTHS), "" picking one per app like the TV does. */
private fun shapeFor(shape: String, pkg: String): Shape = when (shape) {
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
private fun appColor(pkg: String): Color = BRANDS[pkg] ?: HUES[abs(pkg.hashCode() % HUES.size)]

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
