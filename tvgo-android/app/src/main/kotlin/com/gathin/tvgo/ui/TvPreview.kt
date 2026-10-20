package com.gathin.tvgo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Small pictures of what the TV will show, drawn from the launcher's own sizes (it lays out a 960 dp wide screen):
 * the home screen with the logo, colour and favourites, and the clock screensaver with the logo.
 */

private const val WIDE = 1.6f

private fun isWide(b: Bitmap) = b.width.toFloat() / b.height >= WIDE

/** The company logo kept on this phone, read again when UiState.brandStamp changes. */
@Composable
fun rememberLogo(vm: TvGoViewModel): Bitmap? = androidx.compose.runtime.remember(vm.state.brandStamp) {
    vm.brand.file.takeIf { it.isFile }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
}

/**
 * The home screen: a square [logo] left of the clock, a wide one on the greeting's line; the [favourites] dock as
 * rows of [columns] tiles, at the bottom like on the TV, with the first one's name above it.
 */
@Composable
fun HomePreview(logo: Bitmap?, color: String, favourites: List<String>, columns: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(parse(color))) {
        val k = maxWidth / 960.dp
        val wide = logo != null && isWide(logo)
        Column(Modifier.fillMaxSize().padding(start = 56.dp * k, end = 56.dp * k, top = 26.dp * k)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (logo != null && !wide) LogoImage(logo, 96.dp * k, 200.dp * k, Modifier.padding(end = 20.dp * k))
                    Column {
                        if (wide) LogoImage(logo!!, 60.dp * k, 480.dp * k, Modifier.padding(bottom = 6.dp * k))
                        else Text("GOOD AFTERNOON", color = Color.White.copy(alpha = 0.55f), fontSize = (12 * k).sp,
                            letterSpacing = (2.6 * k).sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("9:41", color = Color.White, fontSize = (52 * k).sp, lineHeight = (56 * k).sp, maxLines = 1)
                            Text(" PM", color = Color.White.copy(alpha = 0.7f), fontSize = (15 * k).sp,
                                modifier = Modifier.padding(bottom = 8.dp * k), maxLines = 1)
                            // The launcher drops the date when a logo leaves it no room before the buttons.
                            if (logo == null || wide) Text("   TUESDAY 20 OCTOBER", color = Color.White.copy(alpha = 0.65f),
                                fontSize = (14 * k).sp, letterSpacing = (2 * k).sp, maxLines = 1, overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(bottom = 9.dp * k))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k), modifier = Modifier.padding(bottom = 6.dp * k)) {
                    listOf("Inputs", "Wi-Fi", "Settings").forEach {
                        Text(it, color = Color.White, fontSize = (14 * k).sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp * k, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 14.dp * k, vertical = 8.dp * k))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val dock = favourites.take(columns * 2)
            if (dock.isNotEmpty()) Text(appName(dock[0]), color = Color.White, fontSize = (40 * k).sp, maxLines = 1,
                modifier = Modifier.padding(start = 6.dp * k, bottom = 14.dp * k))
            Column(Modifier.padding(bottom = 30.dp * k), verticalArrangement = Arrangement.spacedBy(14.dp * k)) {
                (if (dock.isEmpty()) List(columns) { "" } else dock).chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp * k)) {
                        for (i in 0 until columns) {
                            val pkg = row.getOrNull(i)
                            Box(Modifier.weight(1f).aspectRatio(16f / 8.5f).clip(RoundedCornerShape(12.dp * k))
                                .background(if (pkg == null) Color.Transparent else Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center) {
                                if (!pkg.isNullOrEmpty()) Text(appName(pkg), color = Color.White, fontSize = (16 * k).sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The clock screensaver: black with a soft glow, the logo above the clock (they drift together on the TV). */
@Composable
fun DreamPreview(logo: Bitmap?, place: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
        .background(Brush.radialGradient(listOf(Color(0xFF0B1A4A), Color.Black)))) {
        val k = maxWidth / 960.dp
        Column(Modifier.align(Alignment.Center)) {
            if (logo != null) {
                val wide = isWide(logo)
                LogoImage(logo, (if (wide) 80 else 120).dp * k, (if (wide) 560 else 220).dp * k,
                    Modifier.padding(start = 6.dp * k, bottom = 20.dp * k).alpha(0.85f))
            }
            Text("9:41", color = Color.White.copy(alpha = 0.9f), fontSize = (120 * k).sp, lineHeight = (124 * k).sp)
            Text("TUESDAY  20 OCTOBER", color = Color.White.copy(alpha = 0.55f), fontSize = (18 * k).sp,
                letterSpacing = (2.5 * k).sp, modifier = Modifier.padding(start = 6.dp * k))
            if (place.isNotEmpty()) Text("27°  Partly cloudy · $place", color = Color.White.copy(alpha = 0.8f),
                fontSize = (22 * k).sp, modifier = Modifier.padding(start = 6.dp * k, top = 18.dp * k))
        }
    }
}

@Composable
private fun LogoImage(b: Bitmap, height: Dp, maxWidth: Dp, modifier: Modifier = Modifier) =
    Image(b.asImageBitmap(), "Logo", contentScale = ContentScale.Fit, modifier = modifier.height(height).widthIn(max = maxWidth))

/** #RRGGBB, or black for "" (the plain home screen). */
fun parse(c: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(Color.Black)
