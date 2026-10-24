package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.raghavsatyadev.tvfreedom.design.TvLook

/**
 * The TVFreedom home screen in [look], drawn with [apps] (favourites first), the way the TV draws it: background,
 * clock and buttons, then the apps as a docked favourites row, a wall of tiles or wide banners, the first app chosen.
 * [columns] when the look keeps the TV's own.
 */
@Composable
fun LookPreview(look: TvLook, apps: List<String>, columns: Int, modifier: Modifier = Modifier) {
    val cols = (look.columns ?: columns).coerceIn(3, 6)
    val focused = apps.firstOrNull()
    val accent = look.accent.takeIf { it.isNotEmpty() }?.let { parse(it) }
    val tint = accent ?: focused?.let { appColor(it) } ?: Color(0xFF6750A4)
    val dark = Color(0xFF101014)
    val back = when {
        look.trueBlack -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        look.background == "glow" -> Brush.verticalGradient(listOf(lerp(dark, tint, 0.6f), dark, Color.Black))
        look.background == "plain" -> Brush.verticalGradient(listOf(lerp(dark, tint, 0.16f), lerp(dark, tint, 0.16f)))
        else -> Brush.verticalGradient(listOf(lerp(dark, tint, 0.22f), Color(0xFF0B0B0E)))
    }
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(back)) {
        val k = maxWidth / 960.dp
        // The stock background: a big soft shape in the chosen app's colour, in the corner.
        if (look.background.isEmpty() && !look.trueBlack) Box(Modifier.size(420.dp * k).offset(640.dp * k, (-120).dp * k)
            .clip(shapeFor(look.shape, focused.orEmpty())).background(tint.copy(alpha = 0.22f)))
        Column(Modifier.fillMaxSize().padding(start = 56.dp * k, end = 56.dp * k, top = 26.dp * k, bottom = 18.dp * k)) {
            TopBar(look, tint, k)
            when (look.layout) {
                "wall" -> Wall(look, apps, cols, accent, k)
                "banner" -> Banners(look, apps, cols, k)
                else -> Dock(look, apps.take(cols - 1), cols, accent, k)
            }
        }
    }
}

/** Greeting and clock (small, big or a watch face) on the left, the buttons on the right. */
@Composable
private fun TopBar(look: TvLook, tint: Color, k: Float) {
    val pill = lerp(Color(0xFF2A2A2E), tint, 0.25f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("GOOD EVENING", color = Color.White.copy(alpha = 0.55f), fontSize = (12 * k).sp, letterSpacing = (2.6 * k).sp, maxLines = 1)
            when (look.clock) {
                "big" -> Text("9:41", color = Color.White, fontSize = (110 * k).sp, lineHeight = (112 * k).sp, maxLines = 1)
                "analog" -> Dial(tint, k, Modifier.padding(top = 6.dp * k).size(104.dp * k))
                else -> Text("9:41", color = Color.White, fontSize = (46 * k).sp, lineHeight = (50 * k).sp, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k)) {
            listOf("Inputs", "Wi-Fi", "Settings").forEach {
                Text(it, color = Color.White, fontSize = (13 * k).sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(pill).padding(horizontal = 14.dp * k, vertical = 8.dp * k))
            }
        }
    }
}

/** The favourites in a band at the bottom, the chosen app's big name above them. */
@Composable
private fun ColumnScope.Dock(look: TvLook, row: List<String>, cols: Int, accent: Color?, k: Float) {
    val focused = row.firstOrNull()
    Spacer(Modifier.weight(1f))
    if (focused != null) Text(appName(focused), color = Color.White, fontSize = (34 * k).sp, maxLines = 1,
        modifier = Modifier.padding(start = 6.dp * k, bottom = 12.dp * k))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp * k)).background(Color(0xFF141416))
        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp * k)).padding(10.dp * k)) {
        (row + "+").forEach { pkg -> Tile(look, pkg, pkg == focused, accent, k, Modifier.weight(1f)) }
        repeat(cols - row.size - 1) { Spacer(Modifier.weight(1f)) }
    }
}

/** Every app in rows from under the clock, as many rows as fit. */
@Composable
private fun ColumnScope.Wall(look: TvLook, apps: List<String>, cols: Int, accent: Color?, k: Float) {
    val focused = apps.firstOrNull()
    Spacer(Modifier.height(18.dp * k))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp * k)) {
        val rows = if (look.clock.isEmpty()) 3 else 2
        (apps + "+").take(cols * rows).chunked(cols).forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                line.forEach { pkg -> Tile(look, pkg, pkg == focused, accent, k, Modifier.weight(1f)) }
                repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Wide app pictures in rows, like Google TV's. */
@Composable
private fun ColumnScope.Banners(look: TvLook, apps: List<String>, cols: Int, k: Float) {
    val focused = apps.firstOrNull()
    Spacer(Modifier.weight(1f))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp * k)) {
        apps.take(cols * 2).chunked(cols).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp * k)) {
                line.forEach { pkg -> Banner(look, pkg, pkg == focused, k, Modifier.weight(1f)) }
                repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
