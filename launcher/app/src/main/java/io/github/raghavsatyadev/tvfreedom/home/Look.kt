package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.graphics.Color
import kotlin.math.abs

/**
 * The look a design from the TVFreedom phone app sets (with columns, true black and Continue watching in [Config]).
 * Every value left empty keeps the stock look. Read once when Home starts.
 */
object Look {

    const val SHAPE = "tile_shape"     // "" mixed shapes, "circle", "square" (soft square), "flower"
    const val NAMES = "names"          // "" always shown, "focus" only on the focused tile, "off"
    const val ACCENT = "accent"        // "" colours follow the focused app, "#RRGGBB" one fixed colour

    const val NAMES_SHOWN = 0
    const val NAMES_FOCUS = 1
    const val NAMES_OFF = 2

    private var shape = ""
    var names = NAMES_SHOWN
        private set

    /** A fixed accent seed, or 0 when the colours follow the focused app. */
    var accent = 0
        private set

    fun load(ctx: Context) {
        val p = Config.prefs(ctx)
        shape = p.getString(SHAPE, "").orEmpty()
        names = when (p.getString(NAMES, "")) {
            "focus" -> NAMES_FOCUS
            "off" -> NAMES_OFF
            else -> NAMES_SHOWN
        }
        accent = try {
            val v = p.getString(ACCENT, "").orEmpty()
            if (v.isEmpty()) 0 else 0xFF000000.toInt() or Color.parseColor(v)
        } catch (e: IllegalArgumentException) {
            0
        }
    }

    /** Which of [Tint.LOBES] an app's tile uses. */
    fun shapeOf(pkg: String): Int = when (shape) {
        "circle" -> 0
        "square" -> Tint.SQUARE
        "flower" -> 3
        else -> abs(pkg.hashCode() % Tint.SQUARE)
    }

    /** The scallop depth at rest: none for circles. */
    fun depthOf(k: Int): Float = if (shape == "circle") 0f else Tint.DEPTHS[k]

    /** How visible a name is: [rest] for tiles without focus. */
    fun nameAlpha(focused: Boolean, rest: Float): Float = when (names) {
        NAMES_OFF -> 0f
        NAMES_FOCUS -> if (focused) 1f else 0f
        else -> if (focused) 1f else rest
    }

    fun report(ctx: Context): String {
        val p = Config.prefs(ctx)
        return "&" + SHAPE + "=" + p.getString(SHAPE, "").orEmpty() +
            "&" + NAMES + "=" + p.getString(NAMES, "").orEmpty() +
            "&" + ACCENT + "=" + java.net.URLEncoder.encode(p.getString(ACCENT, "").orEmpty(), "UTF-8")
    }
}
