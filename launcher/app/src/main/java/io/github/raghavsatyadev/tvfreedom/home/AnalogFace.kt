package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The "analog" clock of a design ([Look.CLOCK]): a watch face drawn on one canvas (no pictures), hour marks, two hands
 * and a dot in the screen's accent colour. Redraws every 15 seconds while it is on screen.
 */
class AnalogFace(ctx: Context) : View(ctx) {

    private val density = resources.displayMetrics.density
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80FFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val dial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
    private val tick = Runnable { tick() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The accent colour, from the home screen's palette. */
    fun setAccent(color: Int) {
        dot.color = color
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun tick() {
        invalidate()
        removeCallbacks(tick)
        postDelayed(tick, TICK_MS)
    }

    override fun onDraw(c: Canvas) {
        val r = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        c.drawCircle(cx, cy, r, dial)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val long = i % 3 == 0
            mark.strokeWidth = (if (long) 4f else 2f) * density
            val inner = r * (if (long) 0.76f else 0.84f)
            val outer = r * 0.92f
            c.drawLine(
                cx + (inner * sin(a)).toFloat(), cy - (inner * cos(a)).toFloat(),
                cx + (outer * sin(a)).toFloat(), cy - (outer * cos(a)).toFloat(), mark,
            )
        }
        val now = Calendar.getInstance()
        val m = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f
        val h = now.get(Calendar.HOUR) + m / 60f
        hand(c, cx, cy, h * 30f, r * 0.5f, 6f)
        hand(c, cx, cy, m * 6f, r * 0.74f, 4f)
        c.drawCircle(cx, cy, 7f * density, dot)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float) {
        val a = Math.toRadians(deg.toDouble())
        hand.strokeWidth = w * density
        c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), hand)
    }

    private companion object {
        const val TICK_MS = 15_000L
    }
}
