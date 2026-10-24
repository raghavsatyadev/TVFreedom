package io.github.raghavsatyadev.tvfreedom.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

/**
 * App tiles. The favourites (fixed order) come first and fill whole rows, padded with empty spacer slots
 * (null entries) so the other apps, alphabetical, start on a new row. Those favourite rows are the "dock".
 * TV apps come from the Leanback launcher list; installed apps that only have a phone launcher entry are
 * listed too (icon tiles), so sideloaded apps can be opened.
 */
class AppAdapter(
    private val ctx: Context,
    private val listener: TileListener?,
    private val columns: Int,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    interface TileListener {
        fun onTileFocused(position: Int, card: View)

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        fun onTileVerticalKey(position: Int, keyCode: Int): Boolean

        /** Long press OK (or Menu) on a tile: show its menu. */
        fun onTileMenu(position: Int, card: View)

        /** OK on the "+" tile at the end of the dock: pick an app to add to the favourites. */
        fun onAddTile(card: View)
    }

    class Entry internal constructor(
        val label: String,
        val component: ComponentName,
        /** The TV banner, kept only for the banner layout ([Look.banners]): a banner costs about 230 KB. */
        val banner: Drawable?,
        /** A tiny copy of the banner (or icon) to pick the app's colours from. */
        val art: Drawable?,
        private val loadIcon: () -> Drawable,
        /** Opened with the Leanback launcher category; false for phone-only apps. */
        val leanback: Boolean,
        /** The "+" tile at the end of the dock, not an app. */
        val add: Boolean = false,
    ) {
        val pkg: String = component.packageName
        var ambient = 0

        /** The app's icon, loaded each time it is asked for and not kept (an adaptive icon is about 370 KB). Only
         * the made icon (once per app) and the tile menu need it. */
        val icon: Drawable get() = loadIcon()
    }

    /** Favourite slots including spacers: a multiple of columns. Set by reload(). */
    private var dockSize = 0

    /** Dock order, changed from the tile menu (starts from res/values/config.xml, dock_apps). */
    private val favourites = Favourites(ctx)

    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private val hidden = ctx.resources.getStringArray(R.array.hidden_apps).toHashSet()
    private val items = ArrayList<Entry?>()
    private val installedFavourites = ArrayList<String>()
    private val addTile = Entry(
        ctx.getString(R.string.add_app), ComponentName(ctx, "add"), null, null, { ctx.getDrawable(R.drawable.ic_add)!! },
        leanback = false, add = true,
    )

    init {
        // The top bar's Settings pill opens it, so the Settings app gets no tile of its own.
        ctx.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo
            ?.let { hidden.add(it.packageName) }
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true)
    }

    /** Every app tile, favourites first, without the dock's spacers. */
    fun apps(): List<Entry> = items.filter { it != null && !it.add }.map { it!! }

    @android.annotation.SuppressLint("NotifyDataSetChanged") // the whole list is rebuilt
    fun reload() {
        val pm = ctx.packageManager
        val byPkg = LinkedHashMap<String, Entry>()
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPkg)
        collect(pm, Intent.CATEGORY_LAUNCHER, byPkg)
        val fresh = byPkg.values.sortedWith { a, b ->
            val fa = favourites.indexOf(a.pkg)
            val fb = favourites.indexOf(b.pkg)
            when {
                fa >= 0 || fb >= 0 -> when {
                    fa < 0 -> 1
                    fb < 0 -> -1
                    else -> fa.compareTo(fb)
                }
                else -> a.label.compareTo(b.label, ignoreCase = true)
            }
        }
        var favCount = 0
        while (favCount < fresh.size && favourites.contains(fresh[favCount].pkg)) favCount++
        installedFavourites.clear()
        fresh.subList(0, favCount).mapTo(installedFavourites) { it.pkg }
        // The "+" tile closes the dock while some app is not a favourite yet.
        val canAdd = favCount < fresh.size
        dockSize = (favCount + (if (canAdd) 1 else 0) + columns - 1) / columns * columns
        items.clear()
        items.addAll(fresh.subList(0, favCount))
        if (canAdd) items.add(addTile)
        while (items.size < dockSize) items.add(null)
        items.addAll(fresh.subList(favCount, fresh.size))
        notifyDataSetChanged()
    }

    /**
     * One tile per app. Leanback entries come first and win; phone launcher entries are added only for
     * installed apps (not system apps, whose phone screens are usually useless on a TV).
     */
    private fun collect(pm: PackageManager, category: String, byPkg: MutableMap<String, Entry>) {
        val leanback = Intent.CATEGORY_LEANBACK_LAUNCHER == category
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (byPkg.containsKey(pkg) || pkg == ctx.packageName || hidden.contains(pkg)) continue
            if (!leanback && ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
            val art = small(banner ?: ri.loadIcon(pm))
            val label = ri.loadLabel(pm).toString()
            byPkg[pkg] = Entry(label, ComponentName(pkg, ai.name), banner.takeIf { Look.banners }, art, { ri.loadIcon(pm) }, leanback)
        }
    }

    fun favourites(): Favourites = favourites

    /** Installed favourites in dock order. */
    fun installedFavourites(): List<String> = installedFavourites

    /** The app at a position, or null for a spacer. */
    fun entryAt(position: Int): Entry? = items.getOrNull(position)

    /** Favourite slots including spacers (a multiple of the column count). */
    fun dockSize(): Int = dockSize

    fun isSpacer(position: Int): Boolean = position in items.indices && items[position] == null

    /** The nearest real tile to position, looking forward first when forward is true. */
    fun nearestTile(position: Int, forward: Boolean): Int {
        for (step in items.indices) {
            val a = if (forward) position + step else position - step
            val b = if (forward) position - step else position + step
            if (a in items.indices && items[a] != null) return a
            if (b in items.indices && items[b] != null) return b
        }
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(v, listener)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val e = items[position]
        h.card.visibility = if (e == null) View.INVISIBLE else View.VISIBLE
        h.card.isFocusable = e != null
        if (e == null) {
            h.name.visibility = View.INVISIBLE
            h.card.setOnClickListener(null)
            h.card.setOnLongClickListener(null)
            return
        }
        h.name.text = e.label
        h.name.visibility = if (e.add) View.INVISIBLE else View.VISIBLE
        val focused = h.card.isFocused   // a rebind after reload() can hit the focused tile
        h.name.alpha = Look.nameAlpha(focused, NAME_REST)
        h.tile.photo = Look.banners && e.banner != null && !e.add
        h.tile.bind(e.pkg, e.add, focused)
        h.show(e)
        if (e.add) {
            h.card.setOnClickListener { v -> listener?.onAddTile(v) }
            h.card.setOnLongClickListener { v -> v.performClick() }
            return
        }
        h.card.setOnClickListener { launch(e) }
        h.card.setOnLongClickListener { v ->
            val pos = h.bindingAdapterPosition
            if (listener != null && pos != RecyclerView.NO_POSITION) listener.onTileMenu(pos, v)
            true
        }
    }

    private fun launch(e: Entry) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(if (e.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(i)
        } catch (ex: Exception) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        val e = items[position] ?: return -1L - position
        return e.component.flattenToShortString().hashCode().toLong()
    }

    /** Stable key for the app at a position (its component), or null. */
    fun keyAt(position: Int): String? = items.getOrNull(position)?.component?.flattenToShortString()

    /** Key of the tile for a package, or null when it has none. */
    fun keyOfPackage(pkg: String): String? =
        items.firstOrNull { it != null && !it.add && it.pkg == pkg }?.component?.flattenToShortString()

    /** Position of the app with this key, or -1 when it is gone. */
    fun positionOf(key: String?): Int {
        if (key == null) return -1
        return items.indexOfFirst { it != null && it.component.flattenToShortString() == key }
    }

    fun labelAt(position: Int): String = items.getOrNull(position)?.label ?: ""

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    fun ambientColorAt(position: Int): Int {
        val e = items.getOrNull(position) ?: return GREY
        if (e.ambient == 0) e.ambient = vividColor(e.art)
        return e.ambient
    }

    class Holder(v: View, listener: TileListener?) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.card)
        private val icon: ImageView = v.findViewById(R.id.icon)
        private val letter: TextView = v.findViewById(R.id.label)
        val name: TextView = v.findViewById(R.id.name)
        val tile = ShapeTile(card, icon, letter)
        private val density = v.resources.displayMetrics.density
        private var pkg: String? = null

        /** Scale the tile from item_app.xml's 104dp card (its icon, letter and the name below follow). */
        private fun grow(k: Float) {
            if (k == 1f) return
            fun View.size(w: Float, h: Float = w) = layoutParams.let { it.width = (w * k * density).toInt(); it.height = (h * k * density).toInt(); layoutParams = it }
            card.size(104f)
            icon.size(50f)
            letter.textSize = 40f * k
            name.layoutParams = (name.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                width = (180 * k * density).toInt()
                topMargin = ((104 * k + 12) * density).toInt()
            }
        }

        /** A wide banner tile ([Look.banners]): the card takes the TV banner's shape and the picture fills it. */
        private fun widen() {
            fun View.size(w: Float, h: Float) = layoutParams.let { it.width = (w * density).toInt(); it.height = (h * density).toInt(); layoutParams = it }
            card.size(BANNER_W, BANNER_H)
            val r = 14 * density
            icon.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, v.width, v.height, r)
            }
            icon.clipToOutline = true
            name.layoutParams = (name.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                width = (BANNER_W * density).toInt()
                topMargin = ((BANNER_H + 12) * density).toInt()
            }
        }

        init {
            Flex.use(name, 560)
            Flex.use(letter, 600)
            if (Look.banners) widen() else grow(MainActivity.TILE_SCALE)

            card.setOnKeyListener { view, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (keyCode == KeyEvent.KEYCODE_MENU) return@setOnKeyListener view.performLongClick()
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                    return@setOnKeyListener false
                }
                val pos = bindingAdapterPosition
                listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode)
            }

            card.setOnFocusChangeListener { view, focused ->
                tile.set(focused)
                if (focused) Flex.settle(name)
                name.animate().alpha(Look.nameAlpha(focused, NAME_REST))
                    .translationY(if (focused) density * 6f else 0f)
                    .setDuration(Motion.DEFAULT_EFFECTS_MS).start()
                val pos = bindingAdapterPosition
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) listener.onTileFocused(pos, view)
            }
        }

        /** The icon: the pack's glyph, else one made from the app's icon, else the app's first letter. */
        fun show(e: Entry) {
            pkg = e.pkg
            if (tile.photo) return banner(e.banner!!)
            val glyph = if (e.add) R.drawable.ic_add else IconPack.glyph(e.pkg)
            if (glyph != 0) return icon(null, glyph)
            val made = IconPack.made(e.pkg, { e.icon }) { d -> if (pkg == e.pkg) if (d != null) icon(d, 0) else letter(e) }
            if (made != null) icon(made, 0) else letter(e)
        }

        private fun banner(d: Drawable) {
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
            icon.layoutParams = icon.layoutParams.apply { width = card.layoutParams.width; height = card.layoutParams.height }
            icon(d, 0)
            icon.clearColorFilter()
        }

        private fun icon(d: android.graphics.drawable.Drawable?, res: Int) {
            if (Look.banners && !tile.photo && icon.scaleType != ImageView.ScaleType.FIT_CENTER) {
                // A banner tile without a banner: the app's icon, small, on the tile's colour.
                icon.scaleType = ImageView.ScaleType.FIT_CENTER
                icon.layoutParams = icon.layoutParams.apply { width = (56 * density).toInt(); height = width }
            }
            if (res != 0) icon.setImageResource(res) else icon.setImageDrawable(d)
            icon.visibility = View.VISIBLE
            letter.visibility = View.GONE
        }

        private fun letter(e: Entry) {
            icon.setImageDrawable(null)
            icon.visibility = View.GONE
            letter.text = e.label.trim().take(1).uppercase()
            letter.visibility = View.VISIBLE
        }
    }

    companion object {
        const val FOCUS_SCALE = ShapeTile.SCALE
        private const val BANNER_W = 192f
        private const val BANNER_H = 108f
        private const val NAME_REST = 0.6f
        private const val GREY = 0xFF444444.toInt()
        private const val TILE = 0xFF1C1C1E.toInt()

        /** [d] drawn into a 64x36 bitmap (9 KB): enough to pick colours from, so the big picture can go. */
        private fun small(d: Drawable): Drawable? = try {
            val copy = d.constantState?.newDrawable()?.mutate() ?: d
            val bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, 64, 36)
            copy.draw(Canvas(bmp))
            android.graphics.drawable.BitmapDrawable(null, bmp)
        } catch (e: RuntimeException) {
            null
        }

        private fun vividColor(live: Drawable?): Int {
            if (live == null) return GREY
            // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
            val d = live.constantState?.newDrawable()?.mutate() ?: live
            val saved = Rect(d.bounds)
            val n = 12
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, n, n)
            d.draw(Canvas(bmp))
            d.bounds = saved
            val hsv = FloatArray(3)
            var best = GREY
            var bestScore = -1f
            var r = 0L
            var g = 0L
            var b = 0L
            for (y in 0 until n) {
                for (x in 0 until n) {
                    val p = bmp.getPixel(x, y)
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    Color.colorToHSV(p, hsv)
                    val score = hsv[1] * minOf(1f, hsv[2] * 1.4f)   // saturated and not too dark
                    if (score > bestScore) {
                        bestScore = score
                        best = p
                    }
                }
            }
            bmp.recycle()
            if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
                val avg = Color.rgb((r / (n * n)).toInt(), (g / (n * n)).toInt(), (b / (n * n)).toInt())
                Color.colorToHSV(avg, hsv)
                hsv[2] = maxOf(hsv[2], 0.55f)
                return Color.HSVToColor(hsv)
            }
            Color.colorToHSV(best, hsv)
            hsv[1] = minOf(1f, hsv[1] * 1.1f)
            hsv[2] = maxOf(0.5f, hsv[2])
            return Color.HSVToColor(hsv)
        }
    }
}
