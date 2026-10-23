// Adapted from 0xRampey/material-tv-launcher (Apache-2.0); see tvShell/NOTICE.
package io.github.raghavsatyadev.tvfreedom.shell

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.TextView
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Material 3 Expressive type with Google Sans Flex (OFL, assets/fonts): a home clock whose weight follows the time of
 * day and swells as each new minute lands, and a tile name that settles into its weight on focus.
 */
object Flex {

    private const val FONT = "fonts/GoogleSansFlex.ttf"
    private const val TICK_MS = 15_000L
    private var base: Typeface? = null

    /** Clock weight keys: hour of day, weight. */
    private val KEYS = arrayOf(
        floatArrayOf(0f, 250f), floatArrayOf(5f, 250f), floatArrayOf(9f, 430f),
        floatArrayOf(17f, 430f), floatArrayOf(21f, 300f), floatArrayOf(24f, 250f),
    )

    /**
     * Put Google Sans Flex on a view, at a weight. The font is loaded once: each weight is a variation of it set on the
     * view, and those share the one copy of the font data (a Typeface per weight would load the 4 MB font again).
     */
    fun use(view: TextView, weight: Int) {
        val font = base ?: try {
            Typeface.createFromAsset(view.context.assets, FONT)
        } catch (e: RuntimeException) {
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        }.also { base = it }
        view.typeface = font
        weight(view, weight)
    }

    /** Change the weight of a view already set up with [use]. */
    fun weight(view: TextView, weight: Int) {
        val w = weight.coerceIn(100, 1000)
        view.fontVariationSettings = "'wght' $w, 'opsz' 48, 'ROND' 0"
    }

    /** Clock weight through the day: 250 at night, rising to 430 by mid-morning, easing back after sunset. */
    fun clockWeight(): Int {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        for (i in 0 until KEYS.size - 1) {
            val (h0, w0) = KEYS[i]
            val (h1, w1) = KEYS[i + 1]
            if (h in h0..h1) return (w0 + (w1 - w0) * (h - h0) / (h1 - h0)).roundToInt()
        }
        return 300
    }

    /** Expressive type: a focused tile's name lands a little lighter and settles into its full weight. */
    fun settle(name: TextView) {
        ValueAnimator.ofInt(420, 560).setDuration(Motion.DEFAULT_SPATIAL_MS).apply {
            interpolator = Motion.DEFAULT_EFFECTS
            addUpdateListener { weight(name, it.animatedValue as Int) }
            start()
        }
    }

    /** The home clock: a plain TextView that this ticks, so each change of minute can be animated. */
    class Clock(private val view: TextView) {
        private val density = view.resources.displayMetrics.density
        private val handler = Handler(Looper.getMainLooper())
        private val tick = Runnable { tick() }

        init {
            use(view, clockWeight())
        }

        fun start() {
            handler.removeCallbacks(tick)
            tick()
        }

        fun stop() {
            handler.removeCallbacks(tick)
        }

        private fun tick() {
            val pattern = if (DateFormat.is24HourFormat(view.context)) "HH:mm" else "h:mm"
            val time = DateFormat.format(pattern, Date()).toString()
            if (view.text.isEmpty()) {
                view.text = time
                weight(view, clockWeight())
            } else if (!time.contentEquals(view.text)) {
                change(time)
            }
            handler.postDelayed(tick, TICK_MS)
        }

        /** A new minute: the old time lifts away, the new one settles in with a short weight swell. */
        private fun change(time: String) {
            val base = clockWeight()
            view.animate().alpha(0f).translationY(-6 * density)
                .setInterpolator(Motion.FAST_EFFECTS).setDuration(Motion.FAST_EFFECTS_MS)
                .withEndAction {
                    view.text = time
                    view.translationY = 8 * density
                    view.animate().alpha(1f).translationY(0f).setInterpolator(Motion.DEFAULT_SPATIAL)
                        .setDuration(Motion.DEFAULT_SPATIAL_MS).start()
                    ValueAnimator.ofInt(base + 320, base).setDuration(700).apply {
                        interpolator = Motion.DEFAULT_SPATIAL
                        addUpdateListener { weight(view, it.animatedValue as Int) }
                        start()
                    }
                }.start()
        }
    }
}
