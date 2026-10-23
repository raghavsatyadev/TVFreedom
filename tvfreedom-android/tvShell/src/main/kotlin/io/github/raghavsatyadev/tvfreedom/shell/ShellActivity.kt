package io.github.raghavsatyadev.tvfreedom.shell

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Draws the saved design (else the built-in one) with the TV's apps in it; redraws when the phone sends a new one. */
class ShellActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = Engine()
    private lateinit var renderer: Renderer
    private lateinit var root: FrameLayout
    private var surface: A2uiCoreSurfaceModel? = null
    private var redrawQueued = false
    private var loaded = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = Renderer(this, ::open)
        root = FrameLayout(this).apply {
            setBackgroundColor(Renderer.BACKGROUND)
            val pad = (48 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
        }
        setContentView(root)
        scope.launch { engine.processor.collectMessages() }
        scope.launch {
            engine.processor.activeSurfaces.collect { list ->
                val s = list.firstOrNull() as? A2uiCoreSurfaceModel ?: return@collect
                if (s === surface) return@collect
                surface = s
                (s.componentRegistry as Components).onChange = ::queueRedraw
                (s.dataModel as Data).onChange = ::queueRedraw
                engine.put(s.id, "/apps", tvApps(this@ShellActivity))
                queueRedraw()
            }
        }
        loaded = Designs.saved(this).lastModified()
        Designs.listener = ::recreate
        runCatching { engine.load(Designs.current(this)) }.exceptionOrNull()?.let { show(TextView(this).apply {
            text = "This design can't be shown: ${it.message}"
            setTextColor(Renderer.TEXT)
        }) }
    }

    /** Many updates in one go (a whole design) redraw once. */
    private fun queueRedraw() {
        if (redrawQueued) return
        redrawQueued = true
        root.post {
            redrawQueued = false
            val s = surface ?: return@post
            val focused = currentFocus?.tag
            val bg = (s.componentRegistry as Components).byId["root"]?.properties?.get("background")
            root.setBackgroundColor(Renderer.color(bg, Renderer.BACKGROUND))
            renderer.build(s, "root")?.let(::show)
            val again = focused?.let { root.findViewWithTag<View>(it) }
            (again ?: firstTile(root))?.requestFocus()
        }
    }

    private fun show(v: View) {
        root.removeAllViews()
        root.addView(v)
    }

    private fun firstTile(v: View): View? = when {
        v.isFocusable && v.tag is String -> v
        v is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { firstTile(v.getChildAt(it)) }
        else -> null
    }

    /** A design sent while this screen was away shows on return; apps added or removed show too. */
    override fun onResume() {
        super.onResume()
        if (Designs.saved(this).lastModified() != loaded) return recreate()
        surface?.let { engine.put(it.id, "/apps", tvApps(this)) }
    }

    override fun onDestroy() {
        if (Designs.listener == ::recreate) Designs.listener = null
        scope.cancel()
        renderer.close()
        super.onDestroy()
    }

    private fun open(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: return
        startActivity(i)
    }
}
