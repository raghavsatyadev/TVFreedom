package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * "Continue watching": the shows and films apps put in Android TV's Watch Next list, newest first, in a row under the
 * top bar. Off until TVFreedom turns it on ([Config.continueWatching]); TVFreedom also grants READ_TV_LISTINGS, without
 * which Android shows the launcher none of it. The row stays hidden while the list is empty.
 */
class ContinueWatching(private val ctx: Context, private val row: View, private val list: RecyclerView,
                       private val open: (Intent) -> Unit) {

    class Show(val title: String, val subtitle: String, val art: String?, val intent: Intent?, val pkg: String,
               val progress: Float)

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private val adapter = Cards()

    /** Told when the row appears or goes, so the top bar can point Down at it. */
    var onShown: (Boolean) -> Unit = {}

    init {
        list.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        list.itemAnimator = null
        list.adapter = adapter
    }

    /** Read the list again (each time home opens), or hide the row when it is off or Android won't share it. */
    fun refresh() {
        if (!Config.continueWatching(ctx) || ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            show(emptyList())
            return
        }
        worker.execute {
            val shows = try {
                read()
            } catch (e: RuntimeException) { // SecurityException included: the grant was taken back
                Log.w("tvfreedom", "watch next: $e")
                emptyList()
            }
            main.post { show(shows) }
        }
    }

    private fun show(shows: List<Show>) {
        adapter.set(shows)
        val on = shows.isNotEmpty()
        if ((row.visibility == View.VISIBLE) != on) {
            row.visibility = if (on) View.VISIBLE else View.GONE
            onShown(on)
        }
    }

    /** Watch Next rows, newest first. No selection: TvProvider refuses one from apps that can't read every listing. */
    private fun read(): List<Show> {
        val out = ArrayList<Pair<Long, Show>>()
        ctx.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.int(TvContract.WatchNextPrograms.COLUMN_BROWSABLE, 1) == 0) continue
                val pkg = c.str(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: continue
                val title = c.str(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: continue
                val episode = c.str(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE).orEmpty()
                val season = c.str(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
                val number = c.str(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
                val code = if (season != null && number != null) "S$season E$number" else ""
                val subtitle = listOf(code, episode).filter { it.isNotEmpty() }.joinToString(" · ")
                val art = c.str(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI)
                    ?: c.str(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
                val intent = c.str(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)?.let {
                    try {
                        Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                    } catch (e: Exception) {
                        null
                    }
                } ?: ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
                val position = c.long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                val duration = c.long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
                val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                val time = c.long(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)
                out += time to Show(title, subtitle, art, intent, pkg, progress)
            }
        }
        return out.sortedByDescending { it.first }.map { it.second }
            .distinctBy { it.intent?.toUri(0) ?: "${it.pkg}/${it.title}" }.take(MAX)
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
        ?.trim()?.ifEmpty { null }

    private fun Cursor.long(col: String): Long =
        getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L

    private fun Cursor.int(col: String, empty: Int): Int =
        getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) } ?: empty

    // ---- cards ------------------------------------------------------------------------------------

    private val art = LruCache<String, Bitmap>(24)

    private inner class Cards : RecyclerView.Adapter<Card>() {
        private var shows = emptyList<Show>()

        @android.annotation.SuppressLint("NotifyDataSetChanged") // the whole list is read again
        fun set(next: List<Show>) {
            shows = next
            notifyDataSetChanged()
        }

        override fun getItemCount() = shows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Card(parent)

        override fun onBindViewHolder(h: Card, position: Int) = h.bind(shows[position])
    }

    private inner class Card(parent: ViewGroup) : RecyclerView.ViewHolder(LinearLayout(parent.context)) {
        private val poster = FrameLayout(ctx)
        private val image = ImageView(ctx)
        private val bar = View(ctx)
        private val title = TextView(ctx)
        private val focus = FocusMotion(poster)
        private var show: Show? = null

        init {
            val box = itemView as LinearLayout
            box.orientation = LinearLayout.VERTICAL
            box.layoutParams = RecyclerView.LayoutParams(dp(CARD_W), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(GAP) }
            box.clipChildren = false
            poster.background = GradientDrawable().apply { setColor(0x26FFFFFF) }
            poster.isFocusable = true
            poster.isClickable = true
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            poster.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            bar.setBackgroundColor(0xFFFFFFFF.toInt())
            poster.addView(bar, FrameLayout.LayoutParams(0, dp(4), Gravity.BOTTOM or Gravity.START))
            box.addView(poster, LinearLayout.LayoutParams(dp(CARD_W), dp(CARD_W * 9 / 16)))
            title.setTextColor(0xD9FFFFFF.toInt())
            title.textSize = 13f
            title.isSingleLine = true
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            title.typeface = ctx.resources.getFont(R.font.outfit)
            box.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })
            poster.setOnFocusChangeListener { _, on -> focus.set(on) }
            poster.setOnClickListener { show?.let { s -> s.intent?.let(open) } }
            poster.setOnKeyListener { _, code, e ->
                // The last card: Right goes nowhere instead of jumping to the top bar.
                code == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN &&
                    bindingAdapterPosition == adapter.itemCount - 1
            }
        }

        fun bind(s: Show) {
            show = s
            title.text = s.title
            poster.contentDescription = listOf(s.title, s.subtitle).filter { it.isNotEmpty() }.joinToString(", ")
            val w = (dp(CARD_W) * s.progress).toInt()
            bar.visibility = if (w > 0) View.VISIBLE else View.GONE
            bar.layoutParams = bar.layoutParams.apply { width = w }
            focus.snap(poster.isFocused)
            val key = s.art
            image.tag = key
            image.setImageBitmap(key?.let { art.get(it) })
            if (key == null || art.get(key) != null) return
            worker.execute {
                val b = load(key) ?: return@execute
                art.put(key, b)
                main.post { if (image.tag == key) image.setImageBitmap(b) }
            }
        }
    }

    /** A poster or thumbnail from the app (content://, android.resource://, file://) or the web, made card-sized. */
    private fun load(uri: String): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= dp(CARD_W)) sample *= 2
        stream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun stream(uri: String): InputStream? {
        val u = Uri.parse(uri)
        if (u.scheme != "http" && u.scheme != "https") return ctx.contentResolver.openInputStream(u)
        val c = URL(uri).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        return c.inputStream
    }

    private fun dp(v: Int) = (v * density).toInt()

    companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val MAX = 20
        private const val CARD_W = 176
        private const val GAP = 20
    }
}
