package io.github.raghavsatyadev.tvfreedom.home

import android.animation.ValueAnimator
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

/**
 * Material 3 Expressive focus for a tile: it grows on the spatial curve (a small overshoot, then settles), its corners
 * morph from round to rounder, and a ring in the focused app's primary color fades in. Losing focus runs the effects
 * curve, which never overshoots.
 */
class FocusMotion(private val card: View) {

    private val density = card.resources.displayMetrics.density
    private val ring = GradientDrawable()
    private var radius = RADIUS_DP * density
    private var anim: ValueAnimator? = null

    init {
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        card.clipToOutline = true
        ring.setColor(0)
        ring.alpha = 0
        card.foreground = ring
        apply(0f)
    }

    /** Animate to the focused or the resting look. */
    fun set(on: Boolean) {
        track(on)
        val from = (radius / density - RADIUS_DP) / (FOCUSED_RADIUS_DP - RADIUS_DP)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(from, if (on) 1f else 0f).apply {
            duration = if (on) Motion.FAST_SPATIAL_MS else Motion.DEFAULT_EFFECTS_MS
            interpolator = if (on) Motion.FAST_SPATIAL else Motion.DEFAULT_EFFECTS
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    /** Jump straight to a look, with no animation (a rebind of the focused tile). */
    fun snap(on: Boolean) {
        anim?.cancel()
        track(on)
        apply(if (on) 1f else 0f)
    }

    private fun track(on: Boolean) {
        if (on) focused = this else if (focused === this) focused = null
    }

    /** t is 0 at rest and 1 focused; the spatial curve takes it a little past 1 before it settles. */
    private fun apply(t: Float) {
        val scale = 1f + (SCALE - 1f) * t
        card.scaleX = scale
        card.scaleY = scale
        val r = (RADIUS_DP + (FOCUSED_RADIUS_DP - RADIUS_DP) * maxOf(0f, t)) * density
        if (r != radius) {
            radius = r
            card.invalidateOutline()
        }
        ring.cornerRadius = radius
        ring.setStroke((RING_DP * density).roundToInt(), ringColor)
        ring.alpha = (255 * t.coerceIn(0f, 1f)).roundToInt()
    }

    companion object {
        const val SCALE = 1.10f
        const val RADIUS_DP = 16f
        const val FOCUSED_RADIUS_DP = 26f
        private const val RING_DP = 3f

        /** The focused app's accent; [Tint] moves it as the backdrop fades between apps. */
        private var ringColor = Palette.of(Palette.DEFAULT_SEED).primary
        private var focused: FocusMotion? = null

        /** Called by [Tint] on each frame of a color change. */
        fun setRingColor(color: Int) {
            ringColor = color
            focused?.let { it.ring.setStroke((RING_DP * it.density).roundToInt(), color) }
        }
    }
}
