package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * The app grid. Its top edge fades when a row is cut off there (half under the top bar), so it fades out instead of
 * showing names with their tiles cut off. The bottom never fades: the favourites sit there.
 */
class FadeGrid @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : RecyclerView(ctx, attrs) {
    private val margin = DockDecoration.PAD_DP * resources.displayMetrics.density

    init {
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength((FADE_DP * resources.displayMetrics.density).toInt())
    }

    // Android draws the fade at the top padding line. The docked layout keeps a tall top padding (room for the hero),
    // so move the fade up to the grid's real top edge.
    override fun isPaddingOffsetRequired() = true

    override fun getTopPaddingOffset() = -paddingTop

    /** How far the topmost tile (and the glow or band around it) reaches above the top edge, as part of the fade. */
    override fun getTopFadingEdgeStrength(): Float {
        if (childCount == 0) return 0f
        var top = Float.MAX_VALUE
        for (i in 0 until childCount) getChildAt(i).let { top = minOf(top, it.top + it.translationY) }
        return ((margin - top) / verticalFadingEdgeLength.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    override fun getBottomFadingEdgeStrength() = 0f

    private companion object {
        const val FADE_DP = 72
    }
}
