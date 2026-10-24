package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * The dropdown used by the Inputs pill, the tile menu and the + tile: rows with an icon, a title and an optional
 * status, in the backdrop's colours. It always fits on the screen: below its anchor when there is room, else above.
 */
class GlassMenu(private val act: Activity, private val root: ViewGroup, private val minWidth: Int) {

    private val density = act.resources.displayMetrics.density
    private val p = palette
    private val panel: View = act.layoutInflater.inflate(R.layout.popup_inputs, root, false)
    private val rows: LinearLayout = panel.findViewById(R.id.rows)
    private val popup = PopupWindow(panel, minWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))   // needed for Back to dismiss
        isOutsideTouchable = true
        animationStyle = 0
        elevation = 0f
    }

    /** OK held down to open this menu keeps sending repeats and a release: only a new press picks a row. */
    private var armed = false

    init {
        panel.background = GradientDrawable().apply {
            cornerRadius = 24 * density
            setColor((p.containerHigh and 0x00FFFFFF) or 0xFF000000.toInt())
            setStroke(density.toInt().coerceAtLeast(1), (p.onSurface and 0x00FFFFFF) or 0x24000000)
        }
    }

    fun isEmpty(): Boolean = rows.childCount == 0

    /** A heading above the rows: the app the menu is for. */
    fun addTitle(text: String) {
        val t = TextView(act)
        t.text = text
        t.textSize = 13f
        t.letterSpacing = 0.06f
        t.setTextColor(p.onSurfaceVariant)
        t.isSingleLine = true
        Flex.use(t, 600)
        val pad = (12 * density).toInt()
        t.setPadding(pad, (6 * density).toInt(), pad, (8 * density).toInt())
        rows.addView(t)
    }

    fun addSeparator() {
        val line = View(act)
        line.setBackgroundColor((p.onSurface and 0x00FFFFFF) or 0x1F000000)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt().coerceAtLeast(1))
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
        row.background = rowBackground()
        val ink = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(p.onPrimary, p.onSurface))
        val dim = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(p.onPrimary, p.onSurfaceVariant))
        row.findViewById<ImageView>(R.id.icon).apply { setImageResource(iconRes); imageTintList = ink }
        row.findViewById<TextView>(R.id.title).apply { text = title; setTextColor(ink) }
        val status = row.findViewById<TextView>(R.id.status)
        status.setTextColor(dim)
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
        row.setOnKeyListener { _, keyCode, event -> swallowHeldOk(keyCode, event) }
        row.setOnClickListener {
            popup.dismiss()
            action()
        }
        rows.addView(row)
        return row
    }

    private fun swallowHeldOk(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) armed = true
        return !armed
    }

    private fun rowBackground() = StateListDrawable().apply {
        val r = 14 * density
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = r; setColor(p.primary) })
        addState(intArrayOf(), GradientDrawable().apply { cornerRadius = r; setColor(Color.TRANSPARENT) })
    }

    /** Under [anchor] when it fits, else above it; never off the screen. A long list scrolls. */
    fun showBelow(anchor: View) = showNear(anchor, preferAbove = false)

    /** Above [anchor] when it fits (the dock sits at the bottom of the screen), else below it. */
    fun showAbove(anchor: View) = showNear(anchor, preferAbove = true)

    private fun showNear(anchor: View, preferAbove: Boolean) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = minOf(maxOf(minWidth, panel.measuredWidth), root.width - 2 * EDGE_DP.dp())
        val at = IntArray(2)
        anchor.getLocationInWindow(at)
        val gap = 10.dp()
        val edge = EDGE_DP.dp()
        val roomBelow = root.height - (at[1] + anchor.height) - gap - edge
        val roomAbove = at[1] - gap - edge
        val want = panel.measuredHeight
        val above = if (preferAbove) want <= roomAbove || roomAbove >= roomBelow else want > roomBelow && roomAbove > roomBelow
        val height = minOf(want, if (above) roomAbove else roomBelow)
        val y = if (above) at[1] - gap - height else at[1] + anchor.height + gap
        // Centred on the anchor, kept inside the screen.
        val x = (at[0] + anchor.width / 2 - width / 2).coerceIn(edge, maxOf(edge, root.width - width - edge))
        popup.width = width
        popup.height = height

        // No fade: a held OK can cut the animation short and leave the panel see-through.
        panel.pivotY = if (above) height.toFloat() else 0f
        panel.scaleY = 0.92f
        panel.translationY = (if (above) 8 else -8) * density
        popup.showAtLocation(root, Gravity.TOP or Gravity.START, x, y)
        panel.animate().scaleY(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
        (0 until rows.childCount).map { rows.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus()
    }

    private fun Int.dp() = (this * density).toInt()

    companion object {
        private const val EDGE_DP = 24

        /** The backdrop's colours, kept current by the home screen. */
        var palette: Palette = Palette.of(Palette.DEFAULT_SEED)
    }
}
