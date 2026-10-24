package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * The app grid. Its top edge fades once it scrolls, so a row half under the top bar fades out instead of showing
 * names with their tiles cut off. The bottom never fades: the favourites sit there.
 */
class FadeGrid @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : RecyclerView(ctx, attrs) {
    init {
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength((FADE_DP * resources.displayMetrics.density).toInt())
    }

    override fun getBottomFadingEdgeStrength() = 0f

    private companion object {
        const val FADE_DP = 72
    }
}
