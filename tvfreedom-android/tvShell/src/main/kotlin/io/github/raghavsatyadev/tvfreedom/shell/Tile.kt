package io.github.raghavsatyadev.tvfreedom.shell

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Material 3 Expressive shapes by name: lobes, depth and sharpness of a [CookieDrawable] (the launcher's set). */
object Shapes {
    class Shape(val lobes: Int, val depth: Float, val sharp: Float)

    val NAMED = linkedMapOf(
        "cookie" to Shape(9, 0.08f, 1f),
        "scallop" to Shape(12, 0.06f, 1f),
        "clover" to Shape(4, 0.26f, 0.55f),
        "flower" to Shape(8, 0.16f, 0.65f),
        "wavy" to Shape(14, 0.05f, 1f),
        "burst" to Shape(10, 0.11f, 2.4f),
        "puffy" to Shape(6, 0.12f, 1f),
        "gem" to Shape(7, 0.10f, 1f),
        "pillow" to Shape(4, 0.16f, 1f),
        "circle" to Shape(4, 0f, 1f),
    )

    /** "auto" gives each app its own shape; "square" is a rounded square. */
    val NAMES = listOf("auto", "square") + NAMED.keys

    /** The shape for [name], or one picked by [key] for "auto". */
    fun of(name: String?, key: String): Shape {
        NAMED[name]?.let { return it }
        val list = NAMED.values.toList().dropLast(1)
        return list[abs(key.hashCode() % list.size)]
    }
}

/** How one block draws its app tiles, from its properties and the design's [Tone]. */
class TileStyle(p: Map<String, Any?>, tone: Tone, density: Float) {
    val size = ((when (p["size"]) { "small" -> 96; "large" -> 152; else -> 128 }) * density).roundToInt()
    val shape = p["shape"] as? String ?: "auto"
    val labels = p["labels"] == true
    val gap = (Renderer.num(p["gap"], 24) * density).roundToInt()
    val mono = p["icons"] != "color"
    val fill = tone.color(p["tileColor"], "primaryContainer")
    val ink = tone.color(p["iconColor"], "onPrimaryContainer")
    val focus = tone.color(p["focusColor"], "primary")
    val focusInk = tone.color(p["focusIconColor"], "onPrimary")
    val label = tone.color(p["labelColor"], "onSurfaceVariant")
    val focusLabel = tone.color(p["focusLabelColor"], "onSurface")
}

/**
 * An app tile as in the launcher: an M3E shape with a single-colour icon in the design's tonal colours. On focus the
 * shape rounds into a circle, grows, takes the bold colour and gets a ring; losing focus eases it back.
 */
class Tile(private val card: View, private val glyph: ImageView, private val letter: TextView,
           private val name: TextView?, pkg: String, private val s: TileStyle) {

    private val density = card.resources.displayMetrics.density
    private val shape = Shapes.of(s.shape, pkg)
    private val cookie = CookieDrawable(s.fill, shape.lobes, shape.depth)
    private val square = GradientDrawable().apply { cornerRadius = s.size * SQUARE; setColor(s.fill) }
    private val ring = GradientDrawable().apply { this.shape = GradientDrawable.OVAL; setColor(0); alpha = 0 }
    private val argb = ArgbEvaluator()
    private var anim: ValueAnimator? = null
    private var on = false
    private var level = 0f

    init {
        card.background = if (s.shape == "square") square else cookie
        card.foreground = InsetDrawable(ring, -(RING_GAP_DP * density).roundToInt())
        card.outlineProvider = null
        cookie.morphTo(shape.lobes, shape.depth, shape.sharp)
        cookie.setProgress(1f)
        draw(0f)
    }

    /** Show [d] as the icon, tinted when the design asks for single-colour icons; null shows the first letter. */
    fun icon(d: Drawable?) {
        glyph.setImageDrawable(d)
        glyph.visibility = if (d == null) View.GONE else View.VISIBLE
        letter.visibility = if (d == null) View.VISIBLE else View.GONE
        draw(level)
    }

    fun focus(focused: Boolean) {
        if (focused == on) return
        on = focused
        cookie.morphTo(shape.lobes, if (focused) 0f else shape.depth, if (focused) 1f else shape.sharp)
        val from = level
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (focused) Motion.DEFAULT_SPATIAL_MS else Motion.DEFAULT_EFFECTS_MS
            interpolator = if (focused) Motion.DEFAULT_SPATIAL else Motion.DEFAULT_EFFECTS
            addUpdateListener {
                val f = it.animatedValue as Float
                cookie.setProgress(f.coerceIn(0f, 1f))
                draw(from + ((if (focused) 1f else 0f) - from) * f)
            }
            start()
        }
        name?.let { if (focused) Flex.settle(it) else Flex.weight(it, 500) }
    }

    private fun mix(t: Float, a: Int, b: Int) = argb.evaluate(t.coerceIn(0f, 1f), a, b) as Int

    private fun draw(t: Float) {
        level = t
        val scale = 1f + (SCALE - 1f) * t
        card.scaleX = scale
        card.scaleY = scale
        val fill = mix(t, s.fill, s.focus)
        val ink = mix(t, s.ink, s.focusInk)
        cookie.setColor(fill)
        square.setColor(fill)
        square.cornerRadius = s.size * (SQUARE + (0.5f - SQUARE) * t.coerceIn(0f, 1f))
        if (s.mono) glyph.setColorFilter(ink, PorterDuff.Mode.SRC_IN) else glyph.clearColorFilter()
        letter.setTextColor(ink)
        name?.setTextColor(mix(t, s.label, s.focusLabel))
        ring.setStroke((RING_DP * density).roundToInt(), s.focus)
        ring.alpha = (255 * t.coerceIn(0f, 1f)).roundToInt()
    }

    companion object {
        const val SCALE = 1.12f
        private const val SQUARE = 0.28f
        private const val RING_DP = 3f
        private const val RING_GAP_DP = 4f
    }
}
