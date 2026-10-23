package io.github.raghavsatyadev.tvfreedom.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow.
 * Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Above the clock: the company logo, if TVFreedom sent one. Below the date: weather and the next calendar events
 * ([DreamInfo]), refreshed every 10 minutes.
 */
class ClockDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: View
    private val glow = GradientDrawable()
    private var colorIndex = 0
    private var glowColor = 0
    private var weatherView: TextView? = null
    private lateinit var eventsView: LinearLayout
    private var ticks = 0

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (++ticks % REFRESH_EVERY_TICKS == 0) refreshInfo()
            moveClock()
            shiftGlow()
            handler.postDelayed(this, MOVE_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // any key wakes the TV back to where it was
        isFullscreen = true
        isScreenBright = false
        setContentView(R.layout.dream_clock)

        content = findViewById(R.id.dreamContent)
        weatherView = findViewById(R.id.dreamWeather)
        eventsView = findViewById(R.id.dreamEvents)
        Branding.applyDream(this, findViewById(R.id.dreamLogo))
        val ambient = findViewById<View>(R.id.dreamAmbient)
        glow.gradientType = GradientDrawable.RADIAL_GRADIENT
        ambient.background = glow
        ambient.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> glow.gradientRadius = (r - l) * 0.55f }
        colorIndex = Random.nextInt(PALETTE.size)
        setGlow(PALETTE[colorIndex])
        // Weather and events load after the clock is placed and make it taller: pull it back on screen.
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val parent = content.parent as View
            if (parent.height <= 0) return@addOnLayoutChangeListener
            val maxY = maxOf(0, parent.height - content.height).toFloat()
            val maxX = maxOf(0, parent.width - content.width).toFloat()
            if (content.y > maxY) content.y = maxY * 0.9f
            if (content.x > maxX) content.x = maxX * 0.9f
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        content.alpha = 0f
        content.post {
            placeClock()
            content.animate().alpha(1f).setDuration(1200).start()
        }
        refreshInfo()
        handler.postDelayed(tick, MOVE_MS)
    }

    /** Fetch weather and events off the main thread, then show them. */
    private fun refreshInfo() {
        Thread({
            val weather = DreamInfo.weather(applicationContext)
            val events = DreamInfo.events(applicationContext)
            handler.post { showInfo(weather, events) }
        }, "dream-info").start()
    }

    private fun showInfo(weather: String?, events: List<String>) {
        val weatherView = weatherView ?: return
        weatherView.visibility = if (weather == null) View.GONE else View.VISIBLE
        weatherView.text = weather
        eventsView.removeAllViews()
        for (e in events) {
            val t = TextView(this)
            t.text = e
            t.setTextColor(0x99FFFFFF.toInt())
            t.textSize = 17f
            t.typeface = weatherView.typeface
            t.setPadding(0, 0, 0, (4 * resources.displayMetrics.density).toInt())
            eventsView.addView(t)
        }
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction {
            placeClock()
            content.animate().alpha(1f).setDuration(1200).start()
        }.start()
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private fun placeClock() {
        val parent = content.parent as View
        val maxX = maxOf(0, parent.width - content.width)
        val maxY = maxOf(0, parent.height - content.height)
        val x = maxX * (0.1f + 0.8f * Random.nextFloat())
        val y = maxY * (0.1f + 0.8f * Random.nextFloat())
        content.x = x
        content.y = y
        if (parent.width > 0 && parent.height > 0) {
            glow.setGradientCenter((x + content.width / 2f) / parent.width, (y + content.height / 2f) / parent.height)
        }
    }

    private fun shiftGlow() {
        val from = glowColor
        colorIndex = (colorIndex + 1) % PALETTE.size
        ValueAnimator.ofObject(ArgbEvaluator(), from, PALETTE[colorIndex]).apply {
            duration = 8000
            addUpdateListener { setGlow(it.animatedValue as Int) }
            start()
        }
    }

    private fun setGlow(color: Int) {
        glowColor = color
        val c = color and 0x00FFFFFF
        glow.colors = intArrayOf(0x40000000 or c, 0x14000000 or c, c)
    }

    private companion object {
        const val MOVE_MS = 60_000L
        const val REFRESH_EVERY_TICKS = 10

        /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
        val PALETTE = intArrayOf(
            0xFF2B5CFF.toInt(), 0xFF00A39A.toInt(), 0xFF8A3FFC.toInt(), 0xFFE0661B.toInt(), 0xFFD6336C.toInt(),
        )
    }
}
