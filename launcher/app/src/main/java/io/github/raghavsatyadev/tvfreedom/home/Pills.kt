package io.github.raghavsatyadev.tvfreedom.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/**
 * Material 3 Expressive buttons for the top bar: tonal pills in the backdrop's colors. On focus a pill fills with the
 * focused app's primary color, grows a little on the spatial curve and its corners tighten from round to rounded
 * square, the way M3E buttons morph their shape.
 */
class Pills(vararg views: TextView) {

    private var palette = Palette.of(Palette.DEFAULT_SEED)
    private val pills = views.map { Pill(it) }

    /** The backdrop's colors changed (a new app got focus). */
    fun setPalette(p: Palette) {
        palette = p
        for (pill in pills) pill.apply(pill.t)
    }

    private inner class Pill(val view: TextView) {
        val bg = GradientDrawable()
        val density = view.resources.displayMetrics.density
        var t = 0f
        var anim: ValueAnimator? = null

        init {
            view.background = bg
            Flex.use(view, 560)
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply(t) }
            view.setOnFocusChangeListener { _, focused -> animate(focused) }
            apply(if (view.isFocused) 1f else 0f)
        }

        fun animate(on: Boolean) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(t, if (on) 1f else 0f).apply {
                duration = if (on) Motion.FAST_SPATIAL_MS else Motion.DEFAULT_EFFECTS_MS
                interpolator = if (on) Motion.FAST_SPATIAL else Motion.DEFAULT_EFFECTS
                addUpdateListener { apply(it.animatedValue as Float) }
                start()
            }
        }

        /** t is 0 at rest and 1 focused; the spatial curve takes it a little past 1 before it settles. */
        fun apply(t: Float) {
            this.t = t
            val c = t.coerceIn(0f, 1f)
            val scale = 1f + (SCALE - 1f) * t
            view.scaleX = scale
            view.scaleY = scale
            val round = view.height / 2f
            bg.cornerRadius = round + (FOCUSED_RADIUS_DP * density - round) * c
            val rest = (palette.containerHigh and 0x00FFFFFF) or 0xE6000000.toInt()
            bg.setColor(ARGB.evaluate(c, rest, palette.primary) as Int)
            val ink = ARGB.evaluate(c, palette.onSurface, palette.onPrimary) as Int
            view.setTextColor(ink)
            view.compoundDrawableTintList = ColorStateList.valueOf(ink)
        }
    }

    private companion object {
        const val SCALE = 1.06f
        const val FOCUSED_RADIUS_DP = 14f
        val ARGB = ArgbEvaluator()
    }
}
