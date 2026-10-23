package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** The glass dropdown used by the Inputs pill and the tile menu: rows with an icon, a title and an optional status. */
class GlassMenu(private val act: Activity, root: ViewGroup, private val minWidth: Int) {

    private val density = act.resources.displayMetrics.density
    private val panel: View = act.layoutInflater.inflate(R.layout.popup_inputs, root, false)
    private val rows: LinearLayout = panel.findViewById(R.id.rows)
    private val popup = PopupWindow(panel, minWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))   // needed for Back to dismiss
        isOutsideTouchable = true
        animationStyle = 0
        elevation = 0f
    }

    fun isEmpty(): Boolean = rows.childCount == 0

    fun addSeparator() {
        val line = View(act)
        line.setBackgroundColor(0x1FFFFFFF)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt())
        val m = (6 * density).toInt()
        val h = (12 * density).toInt()
        lp.setMargins(h, m, h, m)
        rows.addView(line, lp)
    }

    /** A row with an app's own icon (not tinted). */
    fun addAppRow(icon: Drawable, title: String, action: () -> Unit) {
        val view = addRow(0, title, -1, action).findViewById<ImageView>(R.id.icon)
        view.imageTintList = null
        // A copy: the tile's ImageView shows the same drawable and would lose its bounds.
        view.setImageDrawable(icon.constantState?.newDrawable() ?: icon)
    }

    /** A row; state is a TvInputManager.INPUT_STATE_* value, or -1 for no status. */
    fun addRow(iconRes: Int, title: String, state: Int, action: () -> Unit): View {
        val row = act.layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).text = title
        val status = row.findViewById<TextView>(R.id.status)
        val dot = row.findViewById<View>(R.id.dot)
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        when (state) {
            TvInputManager.INPUT_STATE_CONNECTED -> {
                status.setText(R.string.connected)
                dot.visibility = View.VISIBLE
            }
            TvInputManager.INPUT_STATE_DISCONNECTED -> {
                status.setText(R.string.not_connected)
                dot.visibility = View.GONE
            }
            else -> {
                status.visibility = View.GONE
                dot.visibility = View.GONE
            }
        }
        row.setOnClickListener {
            popup.dismiss()
            action()
        }
        rows.addView(row)
        return row
    }

    /** Drop in below anchor (above it when there is no room), focused on the first row. */
    fun showBelow(anchor: View) {
        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = maxOf(minWidth, panel.measuredWidth)

        // Drop in: fade and unfold from the top edge.
        panel.alpha = 0f
        panel.pivotY = 0f
        panel.scaleY = 0.92f
        panel.translationY = -8 * density
        popup.showAsDropDown(anchor, 0, (10 * density).toInt(), Gravity.START)
        reveal()
    }

    /** Rise above anchor (the dock sits at the bottom of the screen); a long list scrolls in the room there. */
    fun showAbove(anchor: View, root: ViewGroup) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = maxOf(minWidth, panel.measuredWidth)
        val at = IntArray(2)
        anchor.getLocationInWindow(at)
        val gap = (10 * density).toInt()
        val top = (48 * density).toInt()
        val height = minOf(panel.measuredHeight, at[1] - gap - top)
        popup.width = width
        popup.height = height
        val x = minOf(at[0], root.width - width - top)

        panel.alpha = 0f
        panel.pivotY = height.toFloat()
        panel.scaleY = 0.92f
        panel.translationY = 8 * density
        popup.showAtLocation(root, Gravity.TOP or Gravity.START, x, at[1] - gap - height)
        reveal()
    }

    private fun reveal() {
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
        rows.getChildAt(0).requestFocus()
    }
}
