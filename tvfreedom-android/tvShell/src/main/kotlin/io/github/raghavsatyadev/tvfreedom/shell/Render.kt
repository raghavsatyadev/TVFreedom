package io.github.raghavsatyadev.tvfreedom.shell

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.protocol.A2uiDataPath
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
    private val onSurface = 0xFFE3E2E6.toInt()

    /** The view for component [id] and its children; null for an unknown component. */
    fun build(surface: A2uiCoreSurfaceModel, id: String): View? {
        val c = (surface.componentRegistry as Components).byId[id] ?: return null
        val data = surface.dataModel as Data
        fun value(name: String): Any? = surface.evaluatePayload(id, { data[it] }, A2uiDataPath("/"), c.properties[name])
        val gap = dp((c.properties["gap"] as? Number)?.toInt() ?: 16)
        @Suppress("UNCHECKED_CAST")
        val children = c.properties["children"] as? List<String> ?: emptyList()
        return when (c.type) {
            "Column" -> stack(LinearLayout.VERTICAL, gap, children.mapNotNull { build(surface, it) })
            "Row" -> stack(LinearLayout.HORIZONTAL, gap, children.mapNotNull { build(surface, it) })
            "Text" -> text(value("text")?.toString().orEmpty(), when (c.properties["style"]) {
                "display" -> 45f
                "title" -> 22f
                else -> 16f
            })
            "Clock" -> TextClock(ctx).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
                setTextColor(onSurface)
            }
            "AppRow" -> appRow(apps(value("apps")))
            else -> null
        }
    }

    private fun stack(orientation: Int, gap: Int, views: List<View>) = LinearLayout(ctx).apply {
        this.orientation = orientation
        gravity = if (orientation == LinearLayout.HORIZONTAL) Gravity.CENTER_VERTICAL else Gravity.START
        clipChildren = false
        clipToPadding = false
        views.forEachIndexed { i, v ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) if (orientation == LinearLayout.HORIZONTAL) lp.marginStart = gap else lp.topMargin = gap
            addView(v, lp)
        }
    }

    private fun text(s: String, sp: Float) = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(onSurface)
    }

    private fun apps(v: Any?): List<TvApp> = (v as? List<*>).orEmpty().mapNotNull { a ->
        val m = a as? Map<*, *> ?: return@mapNotNull null
        TvApp(m["pkg"] as? String ?: return@mapNotNull null, m["label"] as? String ?: "")
    }

    /** App tiles in a scrolling row; the row scrolls itself to keep the focused tile in view. */
    private fun appRow(apps: List<TvApp>): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        apps.forEachIndexed { i, app ->
            val lp = LinearLayout.LayoutParams(dp(120), dp(120))
            if (i > 0) lp.marginStart = dp(20)
            row.addView(tile(app), lp)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
        }
    }

    private fun tile(app: TvApp): View {
        val radius = dp(32).toFloat()
        fun shape(color: Int) = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        val icon = ImageView(ctx).apply { contentDescription = app.label }
        return FrameLayout(ctx).apply {
            tag = app.pkg
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(onSurface))
                addState(intArrayOf(), shape(0xFF1E1F24.toInt()))
            }
            addView(icon, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
            setOnClickListener { onOpen(app.pkg) }
            setOnFocusChangeListener { v, has ->
                val s = if (has) 1.12f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
            loadIcon(app.pkg, icon)
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

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val BACKGROUND = 0xFF101418.toInt()
        val TEXT = Color.WHITE
    }
}
