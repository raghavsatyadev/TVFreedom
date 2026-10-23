package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * tvOS style home: an app-tinted Material 3 Expressive backdrop (Tint), a pinned dock row, an app grid, and one glass
 * highlight that springs between tiles.
 */
class MainActivity : Activity(), AppAdapter.TileListener {

    private lateinit var liveTvPkg: String
    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var root: FrameLayout
    private lateinit var glass: GlassView
    private lateinit var tint: Tint
    private lateinit var ambient: View
    private lateinit var heroName: TextView
    private val ambientGlow = GradientDrawable()
    private var ambientColor = 0
    private var heroShown = false
    private lateinit var greeting: TextView
    private lateinit var topBar: TopBar
    private lateinit var clock: Flex.Clock
    private lateinit var nowPlaying: NowPlaying
    private lateinit var springX: SpringAnimation
    private lateinit var springY: SpringAnimation
    private var glassTargetX = 0f
    private var glassTargetY = 0f
    private var glassShown = false
    private var density = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val minuteTick: Runnable = object : Runnable {
        override fun run() {
            topBar.update()
            handler.postDelayed(this, MINUTE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        density = resources.displayMetrics.density
        COLUMNS = Config.columns(this)
        liveTvPkg = getString(R.string.live_tv_package).trim()

        root = findViewById(R.id.root)
        glass = findViewById(R.id.glass)
        tint = Tint(root, density)
        ambient = findViewById(R.id.ambient)
        heroName = findViewById(R.id.heroName)
        ambientGlow.gradientType = GradientDrawable.RADIAL_GRADIENT
        ambientGlow.setGradientCenter(0.32f, 0.42f)
        ambient.background = ambientGlow
        ambient.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> ambientGlow.gradientRadius = (r - l) * 0.62f }
        greeting = findViewById(R.id.greeting)

        grid = findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, COLUMNS)
        grid.setHasFixedSize(true)
        grid.itemAnimator = null
        grid.layoutAnimation = LayoutAnimationController(AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f)
        adapter = AppAdapter(this, this, COLUMNS)
        grid.addItemDecoration(DockDecoration(adapter, COLUMNS, density))
        grid.adapter = adapter

        // tvOS layout: push the favourite rows to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ ->
            val h = b - t
            val rows = maxOf(1, adapter.dockSize() / COLUMNS)
            // label-less items plus DockDecoration offsets
            val dockRow = ((117 * rows + DockDecoration.ROW_GAP_DP * (rows - 1) + 6 + 12) * density).toInt()
            val want = maxOf(0, h - dockRow - (30 * density).toInt())
            if (grid.paddingTop != want) grid.setPadding(grid.paddingLeft, want, grid.paddingRight, grid.paddingBottom)
        }

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!glassShown) return
                glassTargetY -= dy
                if (springY.isRunning) springY.animateToFinalPosition(glassTargetY) else glass.translationY = glassTargetY
            }
        })

        // Liquid feel: soft springs on the highlight's position.
        springX = SpringAnimation(glass, DynamicAnimation.TRANSLATION_X)
        springY = SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y)
        for (s in arrayOf(springX, springY)) s.spring = SpringForce().setStiffness(260f).setDampingRatio(0.58f)

        topBar = TopBar(this, root, liveTvPkg, ::open)
        clock = Flex.Clock(findViewById(R.id.clock))
        nowPlaying = NowPlaying(root)
        tint.onPalette(nowPlaying::setPalette)
        val pills = Pills(
            findViewById(R.id.btnInputs), findViewById(R.id.btnWifi), findViewById(R.id.btnBluetooth),
            findViewById(R.id.btnSettings),
        )
        tint.onPalette(pills::setPalette)

        // Hide the glass when focus leaves the tiles.
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newF ->
            if (newF == null || newF.id != R.id.card) {
                hideGlass()
                tint.blur()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        forceOpaque()
        Branding.apply(this, root, findViewById(R.id.brandSquare), findViewById(R.id.brandWide), greeting)
        tint.attach(Config.brandColor(this))
        Branding.fitTopBar(
            findViewById(R.id.leftBlock), findViewById(R.id.topButtons),
            findViewById(R.id.date), findViewById(R.id.dateDivider),
        )
        glassShown = false
        glass.alpha = 0f
        heroShown = false
        heroName.alpha = 0f
        ambient.alpha = 0f
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        adapter.reload()
        tint.warm(adapter.apps())
        grid.scheduleLayoutAnimation()
        // A just-installed app: offer it as a favourite once the screen has settled.
        handler.postDelayed({
            if (!isFinishing && hasWindowFocus()) {
                NewApps.offer(this, root, findViewById(R.id.btnInputs), adapter, ::refocus)
            }
        }, 900)
        topBar.update()
        clock.start()
        nowPlaying.start()
        handler.postDelayed(minuteTick, MINUTE_MS)

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post {
            val pos = adapter.positionOf(returnKey)
            focusTile(if (pos >= 0) pos else returnSlot)
        }
    }

    /**
     * On the Mi TV the system sometimes leaves this long-lived home activity marked translucent
     * (occludesParent=false). Home then resumes it with focus but keeps the last app drawn on top, so the
     * invisible launcher swallows Home/Back. Clearing the flag on every resume makes the system redraw it.
     */
    private fun forceOpaque() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setTranslucent(false)
            } else {
                Activity::class.java.getMethod("convertFromTranslucent").invoke(this)
            }
        } catch (ignored: Exception) {
            // Hidden API blocked on this build: nothing else to try.
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(minuteTick)
        clock.stop()
        nowPlaying.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        topBar.release()
    }

    @Deprecated("Home screen: Back goes nowhere.")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    override fun onTileMenu(position: Int, card: View) {
        TileMenu.show(this, root, card, adapter, position, ::refocus)
    }

    override fun onAddTile(card: View) {
        TileMenu.showAdd(this, root, card, adapter, ::refocus)
    }

    /** The app list changed: reload it and focus the tile of [pkg]. */
    private fun refocus(pkg: String) {
        adapter.reload()
        grid.post { focusTile(adapter.positionOf(adapter.keyOfPackage(pkg))) }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    override fun onTileFocused(position: Int, card: View) {
        lastFocused = position
        lastFocusedKey = adapter.keyAt(position)
        val entry = adapter.entryAt(position)
        if (entry != null && !entry.add) tint.focus(entry.pkg, entry.banner ?: entry.icon)
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        val r = Rect(0, 0, card.width, card.height)
        root.offsetDescendantRectToMyCoords(card, r)
        val pad = 6f * density        // matches GlassView's glow inset
        val w = card.width * AppAdapter.FOCUS_SCALE + 2 * pad
        val h = card.height * AppAdapter.FOCUS_SCALE + 2 * pad
        val x = r.exactCenterX() - w / 2f
        val y = r.exactCenterY() - h / 2f

        val lp = glass.layoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt()) {
            lp.width = w.toInt()
            lp.height = h.toInt()
            glass.layoutParams = lp
            glass.setRadius(FocusMotion.FOCUSED_RADIUS_DP * density * AppAdapter.FOCUS_SCALE + pad)
        }
        glassTargetX = x
        glassTargetY = y
        if (!glassShown) {
            springX.cancel()
            springY.cancel()
            glass.translationX = x
            glass.translationY = y
            glass.animate().alpha(1f).setDuration(180).start()
            glassShown = true
        } else {
            springX.animateToFinalPosition(x)
            springY.animateToFinalPosition(y)
        }
        val item = card.parent as View
        if (position < adapter.dockSize()) {
            // Favourites: park their first row at the bottom of the screen and show the top shelf for this app.
            val rowAbove = (position / COLUMNS) * (item.height + (DockDecoration.ROW_GAP_DP * density).toInt())
            val decoratedTop = item.top - rowAbove - (6 * density).toInt()
            val dy = decoratedTop - grid.paddingTop
            if (abs(dy) > 1) grid.smoothScrollBy(0, dy)
            showHero(position, (r.top - rowAbove - dy).toFloat())
        } else {
            hideHero()
            // Scroll just enough so the whole glass (not only the tile) is visible.
            val want = Rect(0, -pad.toInt() * 2, item.width, item.height + pad.toInt())
            grid.requestChildRectangleOnScreen(item, want, false)
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private fun showHero(position: Int, dockTopInRoot: Float) {
        val color = adapter.ambientColorAt(position)
        val y = dockTopInRoot - (DockDecoration.PAD_DP + 26) * density - heroName.height
        // A logo or two rows of favourites can leave less room under the top bar: shrink the name to fit, or skip it.
        val room = dockTopInRoot - (DockDecoration.PAD_DP + 26) * density - topBarBottom() - 8 * density
        val scale = if (heroName.height == 0) 1f else minOf(1f, room / heroName.height)
        heroName.pivotX = 0f
        heroName.pivotY = heroName.height.toFloat()
        heroName.scaleX = maxOf(scale, 0f)
        heroName.scaleY = maxOf(scale, 0f)
        val label = if (scale < 0.55f) "" else adapter.labelAt(position)
        val changed = !label.contentEquals(heroName.text)

        if (!heroShown) {
            heroName.text = label
            heroName.y = y
            heroName.translationX = 0f
            heroName.animate().alpha(1f).setDuration(260).start()
            setAmbient(color)
            ambient.animate().alpha(1f).setDuration(600).start()
            heroShown = true
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction {
                heroName.text = label
                heroName.y = y
                heroName.translationX = 12 * density
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start()
            }.start()
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction {
                    setAmbient(color)
                    ambient.animate().alpha(1f).setDuration(500).start()
                }.start()
            }
        } else {
            heroName.y = y
        }
    }

    /** The bottom of the clock and buttons row, in root coordinates. */
    private fun topBarBottom(): Float {
        val bar = findViewById<View>(R.id.leftBlock).parent as View
        val r = Rect(0, 0, bar.width, bar.height)
        root.offsetDescendantRectToMyCoords(bar, r)
        return r.bottom.toFloat()
    }

    private fun setAmbient(color: Int) {
        ambientColor = color
        val c = color and 0x00FFFFFF
        ambientGlow.colors = intArrayOf(0x5C000000 or c, 0x26000000 or c, c)
    }

    private fun hideHero() {
        if (!heroShown) return
        heroShown = false
        heroName.animate().alpha(0f).setDuration(160).start()
        ambient.animate().alpha(0f).setDuration(300).start()
    }

    private fun hideGlass() {
        if (!glassShown) return
        glassShown = false
        glass.animate().alpha(0f).setDuration(160).start()
    }

    // ---- navigation ------------------------------------------------------------------------------

    override fun onTileVerticalKey(position: Int, keyCode: Int): Boolean {
        val count = adapter.itemCount
        val target = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false          // dock row: let focus move to the top bar
            adapter.nearestTile(position - COLUMNS, false)
        } else {
            val lastRowStart = ((count - 1) / COLUMNS) * COLUMNS
            if (position >= lastRowStart) return true     // already on the last row: stay put
            val below = minOf(position + COLUMNS, count - 1)
            // A gap in the last favourite row: stay in the favourites (e.g. TV+ → Plex), don't skip a row.
            adapter.nearestTile(below, below >= adapter.dockSize())
        }
        focusTile(target)
        return true
    }

    private fun focusTile(position: Int) {
        if (adapter.itemCount == 0) return
        val pos = adapter.nearestTile(position.coerceIn(0, adapter.itemCount - 1), true)
        val vh = grid.findViewHolderForAdapterPosition(pos)
        if (vh != null) {
            val card = vh.itemView.findViewById<View>(R.id.card)
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused) onTileFocused(pos, card) else card.requestFocus()
            return
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                val h = rv.findViewHolderForAdapterPosition(pos)
                val target = h?.itemView?.findViewById(R.id.card) ?: rv.getChildAt(0)
                target?.requestFocus()
            }
        })
        grid.smoothScrollToPosition(pos)
    }

    fun open(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Tiles per row and dock slots (config.xml columns, or set by TVFreedom). Set in onCreate. */
        var COLUMNS = 4
        private const val MINUTE_MS = 60_000L

        // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
        // or Developer options → "Don't keep activities"); they only reset when the process dies.
        private var lastFocused = 0

        /** App of the last focused tile, so focus follows it when the app list changes. */
        private var lastFocusedKey: String? = null
    }
}
