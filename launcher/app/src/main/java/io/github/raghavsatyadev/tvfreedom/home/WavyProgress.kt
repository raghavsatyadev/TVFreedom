// Ported from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 Expressive's wavy linear progress indicator, as Pixel shows for media: the played part is a gently
 * moving wave, the rest a flat track. The wave flattens when playback pauses. Progress is computed from the media
 * session's position and speed, so it advances smoothly between updates. Draws only while shown and playing.
 */
class WavyProgress(c: Context) : View(c) {
    private val active = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Path()
    private val d = c.resources.displayMetrics.density
    private val wavelength = 22 * d
    private val maxAmp = 2.6f * d
    private val gap = 5 * d
    private var position = 0L
    private var updatedAt = 0L
    private var duration = 0L
    private var speed = 0f
    private var playing = false
    private var amp = 0f          // current amplitude, eases toward maxAmp (playing) or 0 (paused)
    private var phase = 0f
    private var lastFrame = 0L

    init {
        for (p in arrayOf(active, track)) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 3.2f * d
        }
    }

    fun setColors(activeColor: Int, trackColor: Int) {
        active.color = activeColor
        track.color = trackColor
        invalidate()
    }

    /** From the media session: position (ms) at updatedAt (elapsedRealtime ms), playback speed, duration (ms). */
    fun setState(position: Long, updatedAt: Long, speed: Float, duration: Long, playing: Boolean) {
        this.position = position
        this.updatedAt = updatedAt
        this.speed = speed
        this.duration = duration
        this.playing = playing
        lastFrame = 0
        postInvalidateOnAnimation()
    }

    private fun progress(): Float {
        if (duration <= 0) return 0f
        var pos = position
        if (playing && updatedAt > 0) pos += ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
        return (pos / duration.toFloat()).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else minOf(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        val target = if (playing) maxAmp else 0f
        amp += (target - amp) * minOf(1f, dt * 6f)
        if (playing) phase = (phase + dt * 2.4f) % (2 * PI).toFloat()

        val w = width.toFloat()
        val mid = height / 2f
        val inset = active.strokeWidth / 2f
        val end = inset + (w - 2 * inset) * progress()
        wave.reset()
        wave.moveTo(inset, mid)
        var x = inset
        while (x <= end) {
            // The wave grows in over the first wavelength, so a just-started song shows a flat line, not a hook.
            val grow = minOf(1f, (x - inset) / wavelength)
            wave.lineTo(x, mid + amp * grow * sin(2 * PI * (x / wavelength) - phase).toFloat())
            x += 2f
        }
        canvas.drawPath(wave, active)
        if (end + gap < w - inset) canvas.drawLine(end + gap, mid, w - inset, mid, track)

        if ((playing || abs(amp - target) > 0.05f) && isShown) postInvalidateOnAnimation()
    }

    companion object {
        fun dp(c: Context, v: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).roundToInt()
    }
}
