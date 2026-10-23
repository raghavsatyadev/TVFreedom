// Ported from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import dynamiccolor.MaterialDynamicColors
import hct.Hct
import quantize.QuantizerCelebi
import scheme.SchemeFidelity
import score.Score

/**
 * Material 3 dynamic color, using Google's material-color-utilities: the seed comes from an app's banner through the
 * same quantize-and-score step Android uses for wallpapers, and the roles come from a dark Fidelity scheme, which keeps
 * brand colors recognizable (Netflix stays red, Spotify green).
 */
class Palette private constructor(val seed: Int) {
    private val s = SchemeFidelity(Hct.fromInt(seed), true, 0.0)

    val surface = ROLES.surfaceDim().getArgb(s)                 // page background
    val surfaceBright = ROLES.surfaceContainerHigh().getArgb(s) // top of the background gradient
    val container = ROLES.surfaceContainer().getArgb(s)         // the dock
    val containerHigh = ROLES.surfaceContainerHighest().getArgb(s) // cards, selected button
    val primary = ROLES.primary().getArgb(s)                    // accents
    val onPrimary = ROLES.onPrimary().getArgb(s)
    val primaryContainer = ROLES.primaryContainer().getArgb(s)  // the large background shape
    val onPrimaryContainer = ROLES.onPrimaryContainer().getArgb(s) // icons on app tiles
    val onSurface = ROLES.onSurface().getArgb(s)
    val onSurfaceVariant = ROLES.onSurfaceVariant().getArgb(s)
    val inverseSurface = ROLES.inverseSurface().getArgb(s)      // focused fill
    val inverseOnSurface = ROLES.inverseOnSurface().getArgb(s)  // focused ink

    companion object {
        const val DEFAULT_SEED = 0xFF6750A4.toInt() // Material baseline purple

        private val ROLES = MaterialDynamicColors()

        fun of(seed: Int): Palette = Palette(seed)

        /** The best theme color in an app's banner, picked the way Android picks wallpaper colors. */
        fun seedFrom(art: Drawable?): Int {
            if (art == null) return DEFAULT_SEED
            // Sample a copy: changing the bounds of the drawable on screen would shrink the logo in its tile.
            val copy = art.constantState?.newDrawable()?.mutate() ?: return DEFAULT_SEED
            val w = 64
            val h = 36
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, w, h)
            copy.draw(Canvas(small))
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            small.recycle()
            val colors = QuantizerCelebi.quantize(pixels, 64)
            val ranked = Score.score(colors, 1, DEFAULT_SEED, true)
            return ranked.firstOrNull() ?: DEFAULT_SEED
        }
    }
}
