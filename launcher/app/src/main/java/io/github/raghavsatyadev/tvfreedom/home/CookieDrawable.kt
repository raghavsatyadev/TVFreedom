// Ported from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Material 3 Expressive's scalloped shapes ("cookie" and friends): a circle with soft, evenly spaced bumps.
 * It can morph from one shape to another by blending their outlines point by point.
 */
class CookieDrawable(color: Int, lobes: Int, depth: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()
    private var fromLobes = lobes
    private var toLobes = lobes
    private var fromDepth = depth
    private var toDepth = depth
    private var fromSharp = 1f
    private var toSharp = 1f
    private var t = 1f

    fun setColor(color: Int) {
        paint.color = color
        invalidateSelf()
    }

    /**
     * Start a morph to a new shape; drive it with [setProgress]. sharpness: 1 = cookie lobes, below 1 = rounder,
     * fuller lobes (clovers), above 1 = pointed (bursts).
     */
    fun morphTo(lobes: Int, depth: Float, sharpness: Float = 1f) {
        // Begin from wherever the current morph has reached.
        fromLobes = if (t < 0.5f) fromLobes else toLobes
        fromDepth += (toDepth - fromDepth) * t
        fromSharp += (toSharp - fromSharp) * t
        toLobes = lobes
        toDepth = depth
        toSharp = sharpness
        t = 0f
    }

    fun setProgress(progress: Float) {
        t = progress
        rebuild(bounds)
        invalidateSelf()
    }

    override fun onBoundsChange(b: Rect) {
        rebuild(b)
    }

    private fun rebuild(b: Rect) {
        path.reset()
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        // Sized by the depth reached so far: the outline stays inside the bounds, and a finished morph to a circle
        // (depth 0) fills them.
        val depth = fromDepth + (toDepth - fromDepth) * t.coerceIn(0f, 1f)
        val r = minOf(b.width(), b.height()) / 2f / (1f + depth)
        for (i in 0..STEPS) {
            val a = 2 * PI * i / STEPS
            val r1 = r * (1f + fromDepth * wave(a, fromLobes, fromSharp))
            val r2 = r * (1f + toDepth * wave(a, toLobes, toSharp))
            val rr = r1 + (r2 - r1) * t
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val STEPS = 240

        fun wave(a: Double, lobes: Int, sharp: Float): Float {
            val c = cos(lobes * a).toFloat()
            return if (sharp == 1f) c else c.sign * abs(c).pow(sharp)
        }
    }
}
