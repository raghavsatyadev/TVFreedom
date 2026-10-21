package io.github.raghavsatyadev.tvfreedom.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Background colours that go with a company logo, for the TV home screen. Its text is white, so every suggestion is
 * dark: a deep shade of the logo's main colour first, then a darker one, the opposite hue, and a near-black tinted
 * with the logo's colour. A logo with no real colour (black, white or grey) gets neutral dark greys.
 */
object BrandColors {

    /** [pixels] are ARGB ints (any sample of the logo); returns #RRGGBB strings, best first. */
    fun suggest(pixels: IntArray): List<String> {
        val hue = mainHue(pixels)
            ?: return listOf(hsl(220f, 0.08f, 0.12f), hsl(0f, 0f, 0.07f), hsl(220f, 0.12f, 0.18f), "#000000")
        val (h, s) = hue
        val sat = s.coerceIn(0.35f, 0.65f)
        return listOf(
            hsl(h, sat, 0.16f),
            hsl(h, sat, 0.09f),
            hsl((h + 180f) % 360f, sat * 0.8f, 0.14f),
            hsl(h, 0.25f, 0.06f),
        ).distinct()
    }

    /**
     * The most common hue among the logo's coloured, opaque pixels (in 24 bins of 15 degrees), with their mean
     * saturation; null when under 5% of its opaque pixels have colour.
     */
    fun mainHue(pixels: IntArray): Pair<Float, Float>? {
        val count = IntArray(24)
        val satSum = FloatArray(24)
        val hueSum = FloatArray(24)
        var opaque = 0
        for (c in pixels) {
            if ((c ushr 24) < 128) continue
            opaque++
            val (h, s, l) = toHsl(c)
            if (s < 0.25f || l < 0.12f || l > 0.92f) continue
            val bin = (h / 15f).toInt().coerceIn(0, 23)
            count[bin]++
            satSum[bin] += s
            hueSum[bin] += h
        }
        val best = count.indices.maxByOrNull { count[it] } ?: return null
        if (opaque == 0 || count[best] * 20 < opaque) return null
        return hueSum[best] / count[best] to satSum[best] / count[best]
    }

    fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0f) return Triple(0f, 0f, l)
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }.let { if (it < 0) it + 360 else it }
        return Triple(h, s, l)
    }

    fun hsl(h: Float, s: Float, l: Float): String {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when ((h / 60).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return Text.rgb(ch(r), ch(g), ch(b))
    }
}
