package io.github.raghavsatyadev.tvfreedom.home

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The parts of a design ([Look]) that change the screen's build, not just its colours: the clock's style. The layout
 * (dock, wall or banners) is in [MainActivity] and [AppAdapter]; the background is in [Tint].
 */
object Face {

    /** Set up the clock in [clock]'s row: the corner clock as it is, a huge one, or a watch face. */
    fun clock(clock: TextView, ampm: TextView, tint: Tint) {
        when (Look.clock) {
            "big" -> {
                clock.textSize = BIG_SP
                ampm.textSize = 24f
                (ampm.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = (22 * clock.resources.displayMetrics.density).toInt()
            }
            "analog" -> {
                clock.visibility = View.GONE
                ampm.visibility = View.GONE
                val row = clock.parent as LinearLayout
                val size = (ANALOG_DP * clock.resources.displayMetrics.density).toInt()
                val face = AnalogFace(clock.context)
                row.addView(face, 0, LinearLayout.LayoutParams(size, size))
                tint.onPalette { face.setAccent(it.primary) }
            }
        }
    }

    private const val BIG_SP = 128f
    private const val ANALOG_DP = 132
}
