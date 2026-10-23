package io.github.raghavsatyadev.tvfreedom.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** Draws the tvOS style lighter band behind the favourite rows and adds a gap above and below them. */
class DockDecoration(
    private val adapter: AppAdapter,
    private val columns: Int,
    private val density: Float,
) : RecyclerView.ItemDecoration() {

    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141416.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0x1FFFFFFF
    }
    private val rect = RectF()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        val dockSize = adapter.dockSize()
        if (pos in 0 until columns) outRect.top = (6 * density).toInt()
        if (pos in 0 until dockSize && pos >= dockSize - columns) outRect.bottom = (12 * density).toInt()
        else if (pos in 0 until dockSize) outRect.bottom = (ROW_GAP_DP * density).toInt()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dockSize = adapter.dockSize()
        var any = false
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos < 0 || pos >= dockSize || adapter.isSpacer(pos)) continue
            val card = child.findViewById<View>(R.id.card) ?: continue
            val l = (child.left + card.left).toFloat()
            val t = child.top + card.top + child.translationY
            val r = l + card.width
            val b = t + card.height
            if (!any) {
                rect.set(l, t, r, b)
                any = true
            } else {
                rect.union(l, t, r, b)
            }
        }
        if (!any) return
        val pad = PAD_DP * density
        rect.inset(-pad, -pad)
        val radius = 26 * density
        c.drawRoundRect(rect, radius, radius, band)
        c.drawRoundRect(rect, radius, radius, edge)
    }

    companion object {
        /** Band around the favourites: wider than a focused tile grows (1.10x plus the glass glow), so it never touches the edge. */
        const val PAD_DP = 22

        /** Extra space between favourite rows, so they are as far apart as the columns. */
        const val ROW_GAP_DP = 12
    }
}
