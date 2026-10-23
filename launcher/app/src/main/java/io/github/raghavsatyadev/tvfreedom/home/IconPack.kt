package io.github.raghavsatyadev.tvfreedom.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Single-colour app icons, the Material 3 Expressive "themed icons" look. Known TV apps get a line icon from
 * Lawnicons (res/drawable/lw_*, Apache-2.0, see launcher/NOTICE). Other apps get one made from their own icon: the
 * Android 13+ monochrome layer when there is one, else the icon's shape lifted off its background. When that would
 * only be a blob, there is no icon and the tile shows the app's first letter.
 */
object IconPack {

    /** TV package -> Lawnicons glyph. */
    private val GLYPHS = mapOf(
        "com.netflix.ninja" to R.drawable.lw_netflix,
        "com.netflix.mediaclient" to R.drawable.lw_netflix,
        "com.amazon.amazonvideo.livingroom" to R.drawable.lw_amazon_prime_video,
        "com.amazon.avod.thirdpartyclient" to R.drawable.lw_amazon_prime_video,
        "in.startv.hotstar" to R.drawable.lw_jiohotstar,
        "com.google.android.youtube.tv" to R.drawable.lw_youtube,
        "com.google.android.youtube.tvkids" to R.drawable.lw_youtube_kids,
        "com.google.android.youtube.tvmusic" to R.drawable.lw_generic_player,
        "com.google.android.apps.youtube.music" to R.drawable.lw_generic_player,
        "org.jellyfin.androidtv" to R.drawable.lw_jellyfin,
        "com.plexapp.android" to R.drawable.lw_plex,
        "com.android.vending" to R.drawable.lw_google_play_store,
        "com.spotify.tv.android" to R.drawable.lw_spotify,
        "com.disney.disneyplus" to R.drawable.lw_disney_plus,
        "com.google.android.videos" to R.drawable.lw_google_tv,
        "com.jio.jioplay.tv" to R.drawable.lw_jiotv,
        "com.sonyliv" to R.drawable.lw_sony_liv,
        "com.graymatrix.did" to R.drawable.lw_zee5,
        "tv.twitch.android.app" to R.drawable.lw_twitch,
        "org.videolan.vlc" to R.drawable.lw_vlc,
        "org.xbmc.kodi" to R.drawable.lw_kodi,
        "com.google.android.apps.photos" to R.drawable.lw_google_photos,
        "com.android.chrome" to R.drawable.lw_google_chrome,
        "com.google.android.play.games" to R.drawable.lw_google_play_games,
        "com.android.tv.settings" to R.drawable.lw_generic_settings,
        "com.apple.atve.androidtv.appletv" to R.drawable.lw_apple_tv,
        "com.wbd.stream" to R.drawable.lw_hbo_max,
        "com.hbo.hbonow" to R.drawable.lw_hbo_max,
        "com.wbd.hbomax" to R.drawable.lw_hbo_max,
        "com.crunchyroll.crunchyroid" to R.drawable.lw_crunchyroll,
        "com.stremio.one" to R.drawable.lw_stremio,
        "ar.tvplayer.tv" to R.drawable.lw_tivimate,
        "com.ionitech.airscreen" to R.drawable.lw_airscreen,
        "com.esaba.downloader" to R.drawable.lw_downloader,
        "com.yablio.sendfilestotv" to R.drawable.lw_send_files_to_tv,
        "com.lonelycatgames.Xplore" to R.drawable.lw_x_plore,
        "cm.aptoide.pt" to R.drawable.lw_aptoide,
        "cm.aptoidetv.pt" to R.drawable.lw_aptoide,
    )

    private const val SIZE = 128

    /** Made icons per package; a missing entry means not worked out yet, NONE means show the letter. */
    private val made = ConcurrentHashMap<String, Any>()
    private val NONE = Any()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** The Lawnicons glyph for a package, or 0. */
    fun glyph(pkg: String): Int = GLYPHS[pkg] ?: 0

    /**
     * The made icon for an app not in the pack: given at once when known, else worked out on a background thread
     * and handed to [ready] on the main thread (null = show the letter).
     */
    fun made(pkg: String, icon: Drawable, ready: (Drawable?) -> Unit): Drawable? {
        val known = made[pkg]
        if (known != null) return known as? Drawable
        worker.execute {
            val d = made.getOrPut(pkg) { make(icon) ?: NONE } as? Drawable
            main.post { ready(d) }
        }
        return null
    }

    private fun make(live: Drawable): Drawable? = try {
        // A private copy: changing the bounds of a drawable an ImageView shows would blank it.
        val icon = live.constantState?.newDrawable()?.mutate() ?: live
        if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable && icon.monochrome != null) {
            icon.monochrome!!.mutate()
        } else {
            silhouette(icon)?.let { android.graphics.drawable.BitmapDrawable(null, it) }
        }
    } catch (e: RuntimeException) {
        null
    }

    /**
     * The icon's shape as a white mask. An adaptive icon's front layer is drawn cropped to the part a launcher shows;
     * where the art is opaque to the edge (old square icons), pixels close to the edge colour count as background.
     */
    private fun silhouette(icon: Drawable): Bitmap? {
        val src = if (icon is AdaptiveIconDrawable && icon.foreground != null) icon.foreground!! else icon
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        // The front layer is 108 units with the middle 72 on show: draw it 1.5x so that middle fills the bitmap.
        val pad = if (src !== icon) SIZE / 4 else 0
        src.setBounds(-pad, -pad, SIZE + pad, SIZE + pad)
        src.draw(Canvas(bmp))
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)

        var opaqueEdge = 0
        var edge = 0
        var r = 0L
        var g = 0L
        var b = 0L
        for (i in 0 until SIZE) {
            for (p in intArrayOf(px[i], px[(SIZE - 1) * SIZE + i], px[i * SIZE], px[i * SIZE + SIZE - 1])) {
                edge++
                if (Color.alpha(p) > 200) {
                    opaqueEdge++
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                }
            }
        }
        val solid = opaqueEdge > edge / 2
        val bg = if (solid) Color.rgb((r / opaqueEdge).toInt(), (g / opaqueEdge).toInt(), (b / opaqueEdge).toInt()) else 0

        var cover = 0f
        for (i in px.indices) {
            val p = px[i]
            var a = Color.alpha(p) / 255f
            if (solid) {
                val dr = (Color.red(p) - Color.red(bg)).toFloat()
                val dg = (Color.green(p) - Color.green(bg)).toFloat()
                val db = (Color.blue(p) - Color.blue(bg)).toFloat()
                a *= ((sqrt(dr * dr + dg * dg + db * db) - 40f) / 70f).coerceIn(0f, 1f)
            }
            cover += a
            px[i] = Color.argb((a * 255).toInt(), 255, 255, 255)
        }
        cover /= px.size
        if (cover < 0.03f || cover > 0.6f) {
            bmp.recycle()
            return null
        }
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return bmp
    }
}
