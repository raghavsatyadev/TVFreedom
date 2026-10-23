package io.github.raghavsatyadev.tvfreedom.shell

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
 * Builds plain Android views for a surface's components, in Material 3 Expressive style: tonal colours from the
 * design's seed, the launcher's shapes, single-colour icons and Google Sans Flex. The remote moves between app tiles
 * with the system's own focus search; OK opens the app.
 */
class Renderer(private val ctx: Context, private val onOpen: (String) -> Unit) {

    private val loader = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val clocks = ArrayList<Flex.Clock>()

    /** The design's colours; set by [draw]. */
    var tone = Tone(Tone.SEED, null)
        private set

    /** The whole screen: the root and everything under it, in the root's colours. */
    fun draw(surface: A2uiCoreSurfaceModel): View? {
        clocks.forEach { it.stop() }
        clocks.clear()
        tone = Tone.of((surface.componentRegistry as Components).byId["root"]?.properties)
        return build(surface, "root")
    }

    /** The screen colour behind the design. */
    fun background(surface: A2uiCoreSurfaceModel) =
        tone.color((surface.componentRegistry as Components).byId["root"]?.properties?.get("background"), "surfaceDim")

    /** The view for component [id] and its children; null for an unknown component (Check refuses those). */
    private fun build(surface: A2uiCoreSurfaceModel, id: String): View? {
        val c = (surface.componentRegistry as Components).byId[id] ?: return null
        val p = c.properties
        fun value(name: String) = surface.prop(id, name)
        fun kids() = Check.children(p).mapNotNull { build(surface, it) }
        val gap = dp(num(p["gap"], 16))
        val view = when (c.type) {
            "Column" -> stack(LinearLayout.VERTICAL, gap, p["align"], kids())
            "Row" -> stack(LinearLayout.HORIZONTAL, gap, p["align"], kids())
            "Panel" -> panel(p, kids())
            "Spacer" -> Space(ctx).apply { minimumWidth = dp(num(p["size"], 24)); minimumHeight = minimumWidth }
            "Text" -> text(value("text")?.toString().orEmpty(), p["style"] ?: "body", p)
            "Clock" -> TextView(ctx).apply {
                style(this, p["style"] ?: "display", p)
                clocks += Flex.Clock(this).also { it.start() }
            }
            "Date" -> TextClock(ctx).apply {
                val f = p["format"] as? String ?: "EEEE d MMMM"
                format24Hour = f
                format12Hour = f
                style(this, p["style"] ?: "title", p)
            }
            "Greeting" -> text(greeting(value("name")?.toString()), p["style"] ?: "headline", p)
            "AppRow" -> appRow(apps(surface, value("apps")), TileStyle(p, tone, density()))
            "AppGrid" -> appGrid(apps(surface, value("apps")), TileStyle(p, tone, density()), num(p["columns"], 6))
            else -> null
        } ?: return null
        // The root's background fills the whole screen (the activity paints it); others colour their own box.
        if (id != "root" && p["background"] != null) view.setBackgroundColor(tone.color(p["background"], "surface"))
        return view
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

    /** A card: rounded (radius), or one of the M3E shapes, which keeps the card square. */
    private fun panel(p: Map<String, Any?>, kids: List<View>): View {
        val fill = tone.color(p["color"], "surfaceContainer")
        val named = Shapes.NAMED[p["shape"] as? String]
        val box = if (named == null) FrameLayout(ctx) else object : FrameLayout(ctx) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, h)
                val side = maxOf(measuredWidth, measuredHeight)
                val spec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
                super.onMeasure(spec, spec)
            }
        }
        return box.apply {
            val pad = dp(num(p["padding"], if (named == null) 28 else 40))
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
            background = if (named != null) CookieDrawable(fill, named.lobes, named.depth).apply {
                morphTo(named.lobes, named.depth, named.sharp)
                setProgress(1f)
            } else GradientDrawable().apply { cornerRadius = dp(num(p["radius"], 32)).toFloat(); setColor(fill) }
            kids.forEach {
                addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
        }
    }

    private fun text(s: String, style: Any, p: Map<String, Any?>) = TextView(ctx).apply {
        text = s
        style(this, style, p)
    }

    /** M3E type in Google Sans Flex: each style has a size, weight and colour role; "weight" and "color" change them. */
    private fun style(v: TextView, style: Any, p: Map<String, Any?>) {
        val (sp, weight, role) = TYPE[style] ?: TYPE.getValue("body")
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        Flex.use(v, num(p["weight"], weight))
        v.setTextColor(tone.color(p["color"], role))
        v.includeFontPadding = false
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
    private fun appRow(apps: List<TvApp>, t: TileStyle): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16), dp(16), dp(16), dp(16))
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
    private fun appGrid(apps: List<TvApp>, t: TileStyle, columns: Int) = GridLayout(ctx).apply {
        columnCount = columns.coerceIn(1, 12)
        clipChildren = false
        clipToPadding = false
        setPadding(dp(16), dp(16), dp(16), dp(16))
        apps.forEach { app ->
            addView(tile(app, t), GridLayout.LayoutParams().apply { setMargins(0, 0, t.gap, t.gap) })
        }
    }

    /** One app: a focusable shaped tile (tag = package) with its icon, and the name under it if asked. */
    private fun tile(app: TvApp, s: TileStyle): View {
        val glyph = ImageView(ctx).apply { contentDescription = app.label; visibility = View.GONE }
        val letter = TextView(ctx).apply {
            text = app.label.take(1).uppercase()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.size * 0.4f)
            Flex.use(this, 600)
        }
        val name = if (!s.labels) null else TextView(ctx).apply {
            text = app.label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            Flex.use(this, 500)
        }
        val card = FrameLayout(ctx).apply {
            tag = app.pkg
            isFocusable = true
            isClickable = true
            val i = s.size * (if (s.mono) 9 else 11) / 20
            addView(glyph, FrameLayout.LayoutParams(i, i, Gravity.CENTER))
            addView(letter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { onOpen(app.pkg) }
        }
        val tile = Tile(card, glyph, letter, name, app.pkg, s)
        card.setOnFocusChangeListener { _, has -> tile.focus(has) }
        loadIcon(app.pkg, s.mono, tile)
        if (name == null) return card.apply { layoutParams = ViewGroup.LayoutParams(s.size, s.size) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(card, LinearLayout.LayoutParams(s.size, s.size))
            addView(name, LinearLayout.LayoutParams(s.size + dp(16), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) })
        }
    }

    /** Single-colour: the Lawnicons glyph, else one made from the app's icon, else the letter. Colour: the app's icon. */
    private fun loadIcon(pkg: String, mono: Boolean, into: Tile) {
        val glyph = if (mono) IconPack.glyph(pkg) else 0
        if (glyph != 0) return into.icon(ctx.getDrawable(glyph))
        loader.execute {
            val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return@execute
            if (!mono) {
                main.post { into.icon(d) }
                return@execute
            }
            val made = IconPack.made(pkg, d) { m -> into.icon(m) }
            if (made != null) main.post { into.icon(made) }
        }
    }

    fun close() {
        clocks.forEach { it.stop() }
        loader.shutdownNow()
    }

    private fun density() = ctx.resources.displayMetrics.density

    private fun dp(v: Int) = (v * density() + 0.5f).toInt()

    companion object {
        /** Text styles: size (sp), weight, colour role. */
        val TYPE = mapOf(
            "display" to Triple(96f, 300, "onSurface"),
            "headline" to Triple(44f, 450, "onSurface"),
            "title" to Triple(24f, 500, "onSurface"),
            "body" to Triple(18f, 400, "onSurfaceVariant"),
            "label" to Triple(14f, 550, "onSurfaceVariant"),
        )

        fun num(v: Any?, default: Int) = (v as? Number)?.toInt() ?: (v as? String)?.toIntOrNull() ?: default
    }
}
