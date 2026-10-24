package io.github.raghavsatyadev.tvfreedom.home

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The top shelf above the docked favourites: the focused app's big name, and a soft glow in its colour behind the
 * screen. Only the docked layout shows it.
 */
class Hero(private val root: FrameLayout, private val adapter: AppAdapter, private val tint: Tint) {

    private val density = root.resources.displayMetrics.density
    private val name: TextView = root.findViewById(R.id.heroName)
    private val ambient: View = root.findViewById(R.id.ambient)
    private val glow = GradientDrawable()
    private var glowColor = 0
    private var shown = false

    init {
        glow.gradientType = GradientDrawable.RADIAL_GRADIENT
        glow.setGradientCenter(0.32f, 0.42f)
        ambient.background = glow
        ambient.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> glow.gradientRadius = (r - l) * 0.62f }
    }

    /** Back on screen: start hidden. */
    fun reset() {
        shown = false
        name.alpha = 0f
        ambient.alpha = 0f
    }

    fun show(position: Int, dockTopInRoot: Float) {
        val color = adapter.ambientColorAt(position)
        val y = dockTopInRoot - (DockDecoration.PAD_DP + 26) * density - name.height
        // A logo or two rows of favourites can leave less room under the top bar: shrink the name to fit, or skip it.
        val room = dockTopInRoot - (DockDecoration.PAD_DP + 26) * density - topBarBottom() - 8 * density
        val scale = if (name.height == 0) 1f else minOf(1f, room / name.height)
        name.pivotX = 0f
        name.pivotY = name.height.toFloat()
        name.scaleX = maxOf(scale, 0f)
        name.scaleY = maxOf(scale, 0f)
        val label = if (scale < 0.55f) "" else adapter.labelAt(position)
        val changed = !label.contentEquals(name.text)

        if (!shown) {
            name.text = label
            name.y = y
            name.translationX = 0f
            name.animate().alpha(1f).setDuration(260).start()
            setGlow(color)
            ambient.animate().alpha(1f).setDuration(600).start()
            shown = true
        } else if (changed) {
            name.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction {
                name.text = label
                name.y = y
                name.translationX = 12 * density
                name.animate().alpha(1f).translationX(0f).setDuration(200).start()
            }.start()
            if (color != glowColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction {
                    setGlow(color)
                    ambient.animate().alpha(1f).setDuration(500).start()
                }.start()
            }
        } else {
            name.y = y
        }
    }

    fun hide() {
        if (!shown) return
        shown = false
        name.animate().alpha(0f).setDuration(160).start()
        ambient.animate().alpha(0f).setDuration(300).start()
    }

    /** The bottom of the clock and buttons row (or of Continue watching under it), in root coordinates. */
    private fun topBarBottom(): Float {
        val row = root.findViewById<View>(R.id.watchRow)
        val bar = if (row.visibility == View.VISIBLE) row else root.findViewById<View>(R.id.leftBlock).parent as View
        val r = Rect(0, 0, bar.width, bar.height)
        root.offsetDescendantRectToMyCoords(bar, r)
        return r.bottom.toFloat()
    }

    private fun setGlow(color: Int) {
        glowColor = color
        // True black: no glow lights up the screen.
        val c = if (tint.black) 0 else color and 0x00FFFFFF
        glow.colors = intArrayOf(0x5C000000 or c, 0x26000000 or c, c)
    }
}
