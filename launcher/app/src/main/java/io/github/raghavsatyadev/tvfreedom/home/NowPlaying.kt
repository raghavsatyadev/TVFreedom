// Adapted from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Now Playing card, top right under the top bar: art, title, artist and a wavy progress line
 * for the media session that is playing (or paused). It needs notification access for [MediaListener], which
 * TVFreedom grants over adb; without it the card stays hidden. It is not focusable: the remote's play/pause key
 * already reaches the session.
 */
class NowPlaying(root: FrameLayout) {

    private val ctx: Context = root.context
    private val density = ctx.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val card = LinearLayout(ctx)
    private val shape = GradientDrawable()
    private val art = ImageView(ctx)
    private val label: TextView
    private val title: TextView
    private val artist: TextView
    private val progress = WavyProgress(ctx)
    private val listener = ComponentName(ctx, MediaListener::class.java)
    private var sessions: MediaSessionManager? = null
    private var playing: MediaController? = null
    private var meta: MediaMetadata? = null
    private var state: PlaybackState? = null
    private var shownArt: Bitmap? = null
    private var listening = false
    private var labelPkg: String? = null
    private var labelText: CharSequence = ""
    private var palette = Palette.of(Palette.DEFAULT_SEED)

    // Kept from the callbacks: fetching them again from the controller re-parcels the whole bundle (art included).
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) {
            meta = m
            show()
        }

        override fun onPlaybackStateChanged(s: PlaybackState?) {
            state = s
            show()
        }

        override fun onSessionDestroyed() {
            pick()
        }
    }
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    init {
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(12f), dp(12f), dp(20f), dp(12f))
        // A pill at any height: text or font scaling may change it.
        card.addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ -> shape.cornerRadius = (b - t) / 2f }
        card.background = shape
        card.visibility = View.GONE
        card.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = GradientDrawable().apply {
            cornerRadius = dp(20f).toFloat()
            setColor(0x33FFFFFF)
        }
        art.clipToOutline = true
        card.addView(art, LinearLayout.LayoutParams(dp(64f), dp(64f)))

        val words = LinearLayout(ctx)
        words.orientation = LinearLayout.VERTICAL
        words.setPadding(dp(14f), 0, 0, 0)
        label = text(12, 700)
        label.setText(R.string.now_playing)
        label.letterSpacing = 0.2f
        label.isAllCaps = true
        words.addView(label)
        title = text(16, 700)
        title.setPadding(0, dp(3f), 0, 0)
        words.addView(title)
        artist = text(14, 500)
        words.addView(artist)
        val pl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12f))
        pl.topMargin = dp(6f)
        words.addView(progress, pl)
        card.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val lp = FrameLayout.LayoutParams(dp(380f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP)
        lp.setMargins(0, dp(124f), dp(56f), 0)
        root.addView(card, lp)
        style()
    }

    /** Home is shown: follow the media sessions (needs notification access, else the card stays hidden). */
    fun start() {
        try {
            val s = sessions ?: ctx.getSystemService(MediaSessionManager::class.java).also { sessions = it }
            if (!listening) {
                s!!.addOnActiveSessionsChangedListener(changed, listener, handler)
                listening = true
            }
            pick()
        } catch (notAllowed: SecurityException) {
            card.visibility = View.GONE
        } catch (notAllowed: NullPointerException) {
            card.visibility = View.GONE
        }
    }

    /** Home is hidden: let go of the sessions. */
    fun stop() {
        if (listening) {
            try {
                sessions?.removeOnActiveSessionsChangedListener(changed)
            } catch (ignored: RuntimeException) {
            }
            listening = false
        }
        playing?.unregisterCallback(callback)
        playing = null
        meta = null
        state = null
    }

    /** The backdrop's colors changed (a new app got focus). */
    fun setPalette(p: Palette) {
        palette = p
        style()
    }

    /** Follow the session that is playing, else the first one with something to show. */
    private fun pick() {
        if (!listening) return
        var best: MediaController? = null
        var bestMeta: MediaMetadata? = null
        var bestState: PlaybackState? = null
        try {
            for (c in sessions?.getActiveSessions(listener).orEmpty()) {
                val m = c.metadata ?: continue
                val s = c.playbackState
                if (best == null || (s != null && s.state == PlaybackState.STATE_PLAYING)) {
                    best = c
                    bestMeta = m
                    bestState = s
                }
            }
        } catch (e: SecurityException) {
            best = null
        }
        // Each call hands out a new controller for the same session: keep the one the callback is registered on.
        val current = playing
        if (current != null && best != null && best.sessionToken == current.sessionToken) {
            best = current
        } else {
            current?.unregisterCallback(callback)
            best?.registerCallback(callback, handler)
        }
        playing = best
        meta = bestMeta
        state = bestState
        show()
    }

    private fun show() {
        val st = state?.state ?: PlaybackState.STATE_NONE
        val running = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        val m = meta
        val p = playing
        if (p == null || m == null || !(running || st == PlaybackState.STATE_PAUSED)) {
            if (card.visibility == View.VISIBLE) {
                card.animate().alpha(0f).setInterpolator(Motion.DEFAULT_EFFECTS).setDuration(Motion.DEFAULT_EFFECTS_MS)
                    .withEndAction { card.visibility = View.GONE }.start()
            }
            return
        }
        var a = m.getText(MediaMetadata.METADATA_KEY_ARTIST)
        if (TextUtils.isEmpty(a)) a = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (TextUtils.isEmpty(a)) a = appLabel(p.packageName)
        title.text = m.getText(MediaMetadata.METADATA_KEY_TITLE)
        artist.text = a
        val bmp = m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (bmp !== shownArt) {
            shownArt = bmp
            art.setImageBitmap(bmp)
        }
        val duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
        progress.visibility = if (duration > 0) View.VISIBLE else View.GONE
        val s = state
        if (s != null) progress.setState(s.position, s.lastPositionUpdateTime, s.playbackSpeed, duration, running)
        // Cancel a pending fade-out first: its end action would hide the card again.
        card.animate().cancel()
        if (card.visibility != View.VISIBLE || card.alpha < 1f) {
            card.alpha = 0f
            card.translationY = dp(10f).toFloat()
            card.visibility = View.VISIBLE
            card.animate().alpha(1f).translationY(0f).setInterpolator(Motion.DEFAULT_SPATIAL)
                .setDuration(Motion.DEFAULT_SPATIAL_MS).start()
        }
    }

    private fun style() {
        shape.setColor((palette.container and 0x00FFFFFF) or 0xD9000000.toInt())
        label.setTextColor(palette.onSurfaceVariant)
        title.setTextColor(palette.onSurface)
        artist.setTextColor(palette.onSurfaceVariant)
        progress.setColors(palette.primary, (palette.onSurfaceVariant and 0x00FFFFFF) or 0x55000000)
    }

    /** The app's name stands in for a missing artist; looked up once per app, as sessions report many times a second. */
    private fun appLabel(pkg: String): CharSequence {
        if (pkg != labelPkg) {
            labelPkg = pkg
            labelText = try {
                ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0))
            } catch (e: Exception) {
                ""
            }
        }
        return labelText
    }

    private fun text(sp: Int, weight: Int): TextView = TextView(ctx).apply {
        textSize = sp.toFloat()
        Flex.use(this, weight)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun dp(v: Float): Int = (v * density).roundToInt()
}
