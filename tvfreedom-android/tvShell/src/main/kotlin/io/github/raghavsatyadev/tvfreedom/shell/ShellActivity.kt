package io.github.raghavsatyadev.tvfreedom.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Draws the saved design (files/design.json, else the built-in sample) with the TV's apps in it. */
class ShellActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = Engine()
    private lateinit var renderer: Renderer
    private lateinit var root: FrameLayout
    private var surface: A2uiCoreSurfaceModel? = null
    private var redrawQueued = false

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
                engine.put(s.id, "/apps", tvApps())
                queueRedraw()
            }
        }
        val saved = File(filesDir, "design.json")
        val design = if (saved.exists()) saved.readText() else assets.open("sample.json").bufferedReader().readText()
        runCatching { engine.load(design) }.exceptionOrNull()?.let { show(TextView(this).apply {
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

    override fun onDestroy() {
        scope.cancel()
        renderer.close()
        super.onDestroy()
    }

    /** The TV's apps (those with a TV or phone launcher entry), this one left out. */
    private fun tvApps(): List<Map<String, String>> {
        val pm = packageManager
        val seen = LinkedHashMap<String, String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach {
                val pkg = it.activityInfo.packageName
                if (pkg != packageName && pkg !in seen) seen[pkg] = it.loadLabel(pm).toString()
            }
        }
        return seen.map { (pkg, label) -> mapOf("pkg" to pkg, "label" to label) }
    }

    private fun open(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: return
        startActivity(i)
    }
}
