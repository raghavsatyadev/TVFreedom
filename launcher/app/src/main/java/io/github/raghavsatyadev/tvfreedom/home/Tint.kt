package io.github.raghavsatyadev.tvfreedom.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Material 3 Expressive backdrop (after material-tv-launcher): the screen takes a dark scheme seeded from the focused
 * app's banner, and one scalloped shape in the top corner morphs to a new outline for each app. Seeds are worked out
 * on a background thread and cached per package, so moving focus only cross-fades colors.
 */
class Tint(private val root: FrameLayout, density: Float) {

    private val seeds = ConcurrentHashMap<String, Int>()
    private val palettes = ConcurrentHashMap<Int, Palette>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var idleSeed = Palette.DEFAULT_SEED
    private var palette = paletteOf(idleSeed)
    private val backdrop = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(palette.surfaceBright, palette.surface),
    )
    private val cookie = CookieDrawable(palette.primaryContainer, LOBES[0], DEPTHS[0])
    private var focusedPkg: String? = null
    private var colors: ValueAnimator? = null
    private var morph: ValueAnimator? = null
    private val onPalette = ArrayList<(Palette) -> Unit>()
    private val shape = ImageView(root.context)

    /** True black (OLED TVs): the background stays black and the corner shape is hidden; accents still follow apps. */
    var black = false
        private set

    init {
        shape.setImageDrawable(cookie)
        shape.alpha = 0.55f
        shape.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        // Its own GPU layer: a color change or morph step redraws only this layer.
        shape.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val size = (560 * density).toInt()
        val lp = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.TOP)
        lp.setMargins(0, -(150 * density).toInt(), -(120 * density).toInt(), 0)
        root.addView(shape, 0, lp)
    }

    /**
     * The colors with no app focused. A brand color from TVFreedom seeds them; black (no brand) keeps the Material
     * baseline. Call after [Branding.apply], which paints the root with the plain brand color.
     */
    fun attach(brandColor: Int, trueBlack: Boolean) {
        idleSeed = if (brandColor and 0x00FFFFFF == 0) Palette.DEFAULT_SEED else brandColor
        black = trueBlack
        shape.visibility = if (black) View.GONE else View.VISIBLE
        backdrop.colors = if (black) intArrayOf(BLACK, BLACK) else intArrayOf(palette.surfaceBright, palette.surface)
        root.background = backdrop
        if (focusedPkg == null) retint(paletteOf(idleSeed))
    }

    /** Work out every app's seed ahead of time, so the first focus on each one costs nothing. */
    fun warm(apps: List<AppAdapter.Entry>) {
        for (e in apps) {
            if (seeds.containsKey(e.pkg)) continue
            val art = e.banner ?: e.icon
            worker.execute { seedOf(e.pkg, art) }
        }
    }

    /** A tile got focus: morph the shape and fade to that app's colors (once its seed is known). */
    fun focus(pkg: String?, art: Drawable?) {
        if (pkg == null || pkg == focusedPkg) return
        focusedPkg = pkg
        morphTo(pkg)
        val seed = seeds[pkg]
        if (seed != null) {
            retint(paletteOf(seed))
            return
        }
        worker.execute {
            val p = paletteOf(seedOf(pkg, art))
            main.post { if (pkg == focusedPkg) retint(p) }
        }
    }

    /** Told each new palette as the colors start to change (the Now Playing card follows them). */
    fun onPalette(c: (Palette) -> Unit) {
        onPalette.add(c)
        c(palette)
    }

    /** Focus left the tiles (top bar, menus): back to the idle colors. */
    fun blur() {
        if (focusedPkg == null) return
        focusedPkg = null
        retint(paletteOf(idleSeed))
    }

    private fun seedOf(pkg: String, art: Drawable?): Int = seeds.getOrPut(pkg) {
        try {
            Palette.seedFrom(art)
        } catch (e: RuntimeException) {
            Palette.DEFAULT_SEED
        }
    }

    private fun paletteOf(seed: Int): Palette = palettes.getOrPut(seed) { Palette.of(seed) }

    private fun morphTo(pkg: String) {
        val k = abs(pkg.hashCode() % LOBES.size)
        cookie.morphTo(LOBES[k], DEPTHS[k], SHARP[k])
        morph?.cancel()
        morph = ValueAnimator.ofFloat(0f, 1f).setDuration(Motion.DEFAULT_SPATIAL_MS).apply {
            interpolator = Motion.DEFAULT_SPATIAL
            addUpdateListener { cookie.setProgress(it.animatedValue as Float) }
            start()
        }
    }

    /** Cross-fade the backdrop and the shape from the current palette to the next. */
    private fun retint(next: Palette) {
        if (next.seed == palette.seed) return
        val from = palette
        palette = next
        for (c in onPalette) c(next)
        colors?.cancel()
        val argb = ArgbEvaluator()
        fun mix(t: Float, a: Int, b: Int) = argb.evaluate(t, a, b) as Int
        colors = ValueAnimator.ofFloat(0f, 1f).setDuration(Motion.DEFAULT_EFFECTS_MS * 2).apply {
            interpolator = Motion.DEFAULT_EFFECTS
            addUpdateListener {
                val t = it.animatedValue as Float
                if (!black) backdrop.colors = intArrayOf(
                    mix(t, from.surfaceBright, next.surfaceBright), mix(t, from.surface, next.surface),
                )
                cookie.setColor(mix(t, from.primaryContainer, next.primaryContainer))
                FocusMotion.setRingColor(mix(t, from.primary, next.primary))
            }
            start()
        }
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()

        // Material 3 Expressive shapes (MaterialShapes): Cookie 9, Cookie 12, Clover 4-leaf, Clover 8-leaf, Sunny,
        // Soft burst, Cookie 6, Cookie 7, Cookie 4.
        val LOBES = intArrayOf(9, 12, 4, 8, 14, 10, 6, 7, 4)
        val DEPTHS = floatArrayOf(0.08f, 0.06f, 0.26f, 0.16f, 0.05f, 0.11f, 0.12f, 0.10f, 0.16f)
        val SHARP = floatArrayOf(1f, 1f, 0.55f, 0.65f, 1f, 2.4f, 1f, 1f, 1f)
    }
}
