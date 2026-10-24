package io.github.raghavsatyadev.tvfreedom.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * An app tile as a Material 3 Expressive shape: each app keeps one of the shapes (cookie, clover, soft burst...) with a
 * single-colour icon inside, in the home screen's tonal colours (they follow the backdrop as focus moves). On focus
 * the shape grows, rounds into a circle and turns to the bold accent, with a ring around it; losing focus eases it back.
 */
class ShapeTile(private val card: View, private val glyph: ImageView, private val letter: TextView) {

    private val density = card.resources.displayMetrics.density
    private val shape = CookieDrawable(pal.primaryContainer, Tint.LOBES[0], Tint.DEPTHS[0])
    private val ring = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0)
        alpha = 0
    }
    private val dashed = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x14FFFFFF)
    }
    /** Banner tiles ([Look.banners]): a rounded rectangle instead of a shape. */
    private val wide = Look.banners
    private val plate = GradientDrawable().apply { cornerRadius = WIDE_RADIUS_DP * density }
    private val argb = ArgbEvaluator()

    /** The tile shows the app's own banner in its colours: no tint on it. */
    var photo = false
    private var k = 0
    private var add = false
    private var on = false
    private var level = 0f        // 0 resting, 1 focused (the spatial curve takes it a little past 1)
    private var fill = pal.primaryContainer
    private var ink = pal.onPrimaryContainer
    private var anim: ValueAnimator? = null

    init {
        // Drawn just outside the tile, with a small gap (the grid does not clip its tiles).
        if (wide) ring.shape = GradientDrawable.RECTANGLE
        if (wide) ring.cornerRadius = (WIDE_RADIUS_DP + RING_GAP_DP) * density
        card.foreground = InsetDrawable(ring, -(RING_GAP_DP * density).roundToInt())
        card.outlineProvider = null
        all[this] = true
        dashed.setStroke((2 * density).roundToInt(), 0x4DFFFFFF, 6 * density, 5 * density)
    }

    /** Show an app (or the + tile) at rest or focused, with no animation. */
    fun bind(pkg: String, isAdd: Boolean, focused: Boolean) {
        add = isAdd
        k = Look.shapeOf(pkg)
        if (wide) dashed.shape = GradientDrawable.RECTANGLE
        if (wide) dashed.cornerRadius = WIDE_RADIUS_DP * density
        card.background = if (add) dashed else if (wide) plate else shape
        anim?.cancel()
        on = focused
        if (!wide) {
            shape.morphTo(Tint.LOBES[k], if (focused) 0f else Look.depthOf(k), if (focused) 1f else Tint.SHARP[k])
            shape.setProgress(1f)
        }
        fill = if (focused) pal.primary else pal.primaryContainer
        ink = if (add) 0xCCFFFFFF.toInt() else if (focused) pal.onPrimary else pal.onPrimaryContainer
        draw(if (focused) 1f else 0f)
    }

    /** Animate to the focused or the resting look. */
    fun set(focused: Boolean) {
        if (focused == on) return
        on = focused
        if (!add && !wide) shape.morphTo(Tint.LOBES[k], if (focused) 0f else Look.depthOf(k), if (focused) 1f else Tint.SHARP[k])
        val from = level
        val fill0 = fill
        val ink0 = ink
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (focused) Motion.DEFAULT_SPATIAL_MS else Motion.DEFAULT_EFFECTS_MS
            interpolator = if (focused) Motion.DEFAULT_SPATIAL else Motion.DEFAULT_EFFECTS
            addUpdateListener {
                val f = it.animatedValue as Float
                val c = f.coerceIn(0f, 1f)
                if (!add && !wide) shape.setProgress(c)
                fill = mix(c, fill0, if (on) pal.primary else pal.primaryContainer)
                ink = if (add) ink0 else mix(c, ink0, if (on) pal.onPrimary else pal.onPrimaryContainer)
                draw(from + ((if (focused) 1f else 0f) - from) * f)
            }
            start()
        }
    }

    private fun mix(t: Float, a: Int, b: Int) = argb.evaluate(t, a, b) as Int

    private fun draw(t: Float) {
        level = t
        val scale = 1f + ((if (wide) WIDE_SCALE else SCALE) - 1f) * t
        card.scaleX = scale
        card.scaleY = scale
        if (!add) if (wide) plate.setColor(fill) else shape.setColor(fill)
        if (photo) glyph.clearColorFilter() else glyph.setColorFilter(ink, PorterDuff.Mode.SRC_IN)
        letter.setTextColor(ink)
        ring.setStroke((RING_DP * density).roundToInt(), pal.primary)
        ring.alpha = (255 * t.coerceIn(0f, 1f)).roundToInt()
    }

    /** New colours: a running animation already heads for them; otherwise fade over. */
    private fun relight() {
        if (add || anim?.isRunning == true) return
        val fill0 = fill
        val ink0 = ink
        anim = ValueAnimator.ofFloat(0f, 1f).setDuration(Motion.DEFAULT_EFFECTS_MS * 2).apply {
            interpolator = Motion.DEFAULT_EFFECTS
            addUpdateListener {
                val c = it.animatedValue as Float
                fill = mix(c, fill0, if (on) pal.primary else pal.primaryContainer)
                ink = mix(c, ink0, if (on) pal.onPrimary else pal.onPrimaryContainer)
                draw(level)
            }
            start()
        }
    }

    companion object {
        const val SCALE = 1.12f
        private const val WIDE_SCALE = 1.08f
        private const val WIDE_RADIUS_DP = 14f
        private const val RING_DP = 3f
        private const val RING_GAP_DP = 4f

        /** The home screen's colours, from [Tint]: the focused app's scheme, or the brand's when none is focused. */
        private var pal: Palette = Palette.of(Palette.DEFAULT_SEED)
        private val all = java.util.WeakHashMap<ShapeTile, Boolean>()

        fun setPalette(p: Palette) {
            if (p.seed == pal.seed) return
            pal = p
            for (t in all.keys) t.relight()
        }
    }
}
