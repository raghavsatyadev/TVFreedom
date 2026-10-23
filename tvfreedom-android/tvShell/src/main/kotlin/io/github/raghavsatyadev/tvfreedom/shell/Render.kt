package io.github.raghavsatyadev.tvfreedom.shell

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import java.util.Calendar
import java.util.concurrent.Executors

/** An app on the TV, as the shell puts it in the data model at /apps. */
data class TvApp(val pkg: String, val label: String)

/**
 * Builds plain Android views for a surface's components. The remote moves between app tiles with the
 * system's own focus search; OK opens the app. Icons load on a background thread and are kept.
 */
class Renderer(private val ctx: Context, private val onOpen: (String) -> Unit) {

    private val icons = HashMap<String, Drawable>()
    private val loader = Executors.newSingleThreadExecutor()

    /** The view for component [id] and its children; null for an unknown component (Check refuses those). */
    fun build(surface: A2uiCoreSurfaceModel, id: String): View? {
        val c = (surface.componentRegistry as Components).byId[id] ?: return null
        val p = c.properties
        fun value(name: String) = surface.prop(id, name)
        fun kids() = Check.children(p).mapNotNull { build(surface, it) }
        val gap = dp(num(p["gap"], 16))
        val view = when (c.type) {
            "Column" -> stack(LinearLayout.VERTICAL, gap, p["align"], kids())
            "Row" -> stack(LinearLayout.HORIZONTAL, gap, p["align"], kids())
            "Panel" -> FrameLayout(ctx).apply {
                val pad = dp(num(p["padding"], 24))
                setPadding(pad, pad, pad, pad)
                clipChildren = false
                clipToPadding = false
                background = shape(color(p["color"], PANEL), dp(num(p["radius"], 28)).toFloat())
                kids().forEach { addView(it) }
            }
            "Spacer" -> Space(ctx).apply { minimumWidth = dp(num(p["size"], 24)); minimumHeight = minimumWidth }
            "Text" -> text(value("text")?.toString().orEmpty(), textSize(p["style"]), p["color"])
            "Clock" -> TextClock(ctx).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                style(this, textSize(p["style"] ?: "display"), p["color"])
            }
            "Date" -> TextClock(ctx).apply {
                val f = p["format"] as? String ?: "EEEE d MMMM"
                format24Hour = f
                format12Hour = f
                style(this, textSize(p["style"] ?: "title"), p["color"])
            }
            "Greeting" -> text(greeting(value("name")?.toString()), textSize(p["style"] ?: "headline"), p["color"])
            "AppRow" -> appRow(apps(surface, value("apps")), Tiles(p))
            "AppGrid" -> appGrid(apps(surface, value("apps")), Tiles(p), num(p["columns"], 6))
            else -> null
        } ?: return null
        // The root's background fills the whole screen (the activity paints it); others colour their own box.
        if (id != "root") (p["background"] as? String)?.let { view.setBackgroundColor(color(it, BACKGROUND)) }
        return view
    }

    /** How a block draws its tiles: size (small, medium, large), shape (round, square, circle) and labels. */
    private inner class Tiles(p: Map<String, Any?>) {
        val size = dp(when (p["size"]) { "small" -> 96; "large" -> 160; else -> 120 })
        val radius = when (p["shape"]) { "square" -> dp(12).toFloat(); "circle" -> size / 2f; else -> dp(32).toFloat() }
        val labels = p["labels"] == true
        val gap = dp(num(p["gap"], 20))
        val focus = color(p["focusColor"], FOCUS)
        val fill = color(p["tileColor"], TILE)
    }

    private fun stack(orientation: Int, gap: Int, align: Any?, views: List<View>) = LinearLayout(ctx).apply {
        this.orientation = orientation
        val across = when (align) { "center" -> Gravity.CENTER; "end" -> Gravity.END or Gravity.BOTTOM; else -> Gravity.START }
        gravity = if (orientation == LinearLayout.HORIZONTAL) Gravity.CENTER_VERTICAL or (across and Gravity.HORIZONTAL_GRAVITY_MASK)
            else across and Gravity.HORIZONTAL_GRAVITY_MASK
        clipChildren = false
        clipToPadding = false
        views.forEachIndexed { i, v ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) if (orientation == LinearLayout.HORIZONTAL) lp.marginStart = gap else lp.topMargin = gap
            addView(v, lp)
        }
    }

    private fun text(s: String, sp: Float, color: Any?) = TextView(ctx).apply {
        text = s
        style(this, sp, color)
    }

    private fun style(v: TextView, sp: Float, color: Any?) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        v.setTextColor(color(color, ON_SURFACE))
    }

    private fun textSize(style: Any?) = when (style) {
        "display" -> 72f
        "headline" -> 45f
        "title" -> 22f
        "label" -> 13f
        else -> 16f
    }

    private fun greeting(name: String?): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        return if (name.isNullOrBlank()) part else "$part, $name"
    }

    /** The listed apps the TV has, in the list's order, with the TV's own labels. */
    private fun apps(surface: A2uiCoreSurfaceModel, v: Any?): List<TvApp> {
        val labels = ((surface.dataModel as Data).byPath["/apps"] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }.associate { it["pkg"] as? String to (it["label"] as? String ?: "") }
        return packagesOf(v).filter { it in labels }.map { TvApp(it, labels[it].orEmpty()) }
    }

    /** App tiles in a scrolling row; the row scrolls itself to keep the focused tile in view. */
    private fun appRow(apps: List<TvApp>, t: Tiles): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        apps.forEachIndexed { i, app ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.marginStart = t.gap
            row.addView(tile(app, t), lp)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
        }
    }

    /** App tiles in rows of [columns]; the remote moves up, down and across. */
    private fun appGrid(apps: List<TvApp>, t: Tiles, columns: Int) = GridLayout(ctx).apply {
        columnCount = columns.coerceIn(1, 12)
        clipChildren = false
        clipToPadding = false
        setPadding(dp(12), dp(12), dp(12), dp(12))
        apps.forEach { app ->
            addView(tile(app, t), GridLayout.LayoutParams().apply {
                setMargins(0, 0, t.gap, t.gap)
            })
        }
    }

    /** One app: a focusable rounded tile (tag = package) with the icon, and the name under it if asked. */
    private fun tile(app: TvApp, t: Tiles): View {
        val icon = ImageView(ctx).apply { contentDescription = app.label }
        val box = FrameLayout(ctx).apply {
            tag = app.pkg
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(t.focus, t.radius))
                addState(intArrayOf(), shape(t.fill, t.radius))
            }
            val i = t.size * 3 / 5
            addView(icon, FrameLayout.LayoutParams(i, i, Gravity.CENTER))
            setOnClickListener { onOpen(app.pkg) }
            setOnFocusChangeListener { v, has ->
                val s = if (has) 1.12f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
            loadIcon(app.pkg, icon)
        }
        if (!t.labels) return box.apply { layoutParams = ViewGroup.LayoutParams(t.size, t.size) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(box, LinearLayout.LayoutParams(t.size, t.size))
            addView(text(app.label, 13f, null).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(t.size, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }

    private fun loadIcon(pkg: String, into: ImageView) {
        icons[pkg]?.let { into.setImageDrawable(it); return }
        loader.execute {
            val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return@execute
            into.post {
                icons[pkg] = d
                into.setImageDrawable(d)
            }
        }
    }

    fun close() = loader.shutdownNow()

    private fun shape(color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val BACKGROUND = 0xFF101418.toInt()
        const val ON_SURFACE = 0xFFE3E2E6.toInt()
        const val FOCUS = ON_SURFACE
        const val TILE = 0xFF1E1F24.toInt()
        const val PANEL = 0xFF1B1D22.toInt()
        val TEXT = Color.WHITE

        fun num(v: Any?, default: Int) = (v as? Number)?.toInt() ?: (v as? String)?.toIntOrNull() ?: default

        /** "#RRGGBB" or "#AARRGGBB"; anything else falls back to [default]. */
        fun color(v: Any?, default: Int) = (v as? String)?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: default
    }
}
