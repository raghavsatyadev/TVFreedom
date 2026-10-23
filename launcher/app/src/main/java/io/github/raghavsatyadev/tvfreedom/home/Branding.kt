package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import java.io.File

/**
 * A company's look on the home screen, set by the TVFreedom phone app: its logo at the top left and a background colour
 * picked from that logo ([Config.brandColor]). A square logo stands left of the clock; a wide one takes the
 * greeting's line above it. A dark logo with see-through parts sits on a light plate, so it shows on the dark background.
 */
object Branding {

    private const val WIDE = 1.6f

    /** Show the logo in [square] or [wide] by its shape (a wide one takes the [greeting]'s line), and the colour. */
    fun apply(ctx: Context, root: View, square: ImageView, wide: ImageView, greeting: View) {
        root.setBackgroundColor(Config.brandColor(ctx))
        val b = load(LogoProvider.file(ctx))
        val isWide = b != null && isWide(b)
        show(ctx, square, if (!isWide) b else null, 200)
        show(ctx, wide, if (isWide) b else null, 480)
        greeting.visibility = if (isWide) View.GONE else View.VISIBLE
    }

    /**
     * Keep the clock clear of the buttons: a logo beside the clock, or a long Wi-Fi name on its button, can make them
     * meet. Then the date (and its divider) steps aside; it comes back when there is room again.
     */
    fun fitTopBar(left: View, buttons: View, date: View, divider: View) {
        date.visibility = View.VISIBLE
        divider.visibility = View.VISIBLE
        val fit = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val gap = (16 * v.resources.displayMetrics.density).toInt()
            val shown = date.visibility == View.VISIBLE
            val need = left.right + if (shown) 0 else date.width + divider.width + 2 * gap
            val fits = need + gap <= buttons.left
            if (fits != shown && (fits || left.right + gap > buttons.left)) {
                val vis = if (fits) View.VISIBLE else View.GONE
                left.post {
                    date.visibility = vis
                    divider.visibility = vis
                }
            }
        }
        (left.getTag(R.id.leftBlock) as? View.OnLayoutChangeListener)?.let { old ->
            left.removeOnLayoutChangeListener(old)
            buttons.removeOnLayoutChangeListener(old)
        }
        left.setTag(R.id.leftBlock, fit)
        left.addOnLayoutChangeListener(fit)
        buttons.addOnLayoutChangeListener(fit)
    }

    /** The screensaver's logo above its clock: a square one taller, a wide one lower and longer; hidden without one. */
    fun applyDream(ctx: Context, view: ImageView) {
        val b = load(LogoProvider.file(ctx))
        val isWide = b != null && isWide(b)
        view.layoutParams.height = ((if (isWide) 80 else 120) * ctx.resources.displayMetrics.density).toInt()
        show(ctx, view, b, if (isWide) 560 else 220)
    }

    private fun isWide(b: Bitmap): Boolean = b.width.toFloat() / b.height >= WIDE

    /** [b] in [view] at the view's height and at most [maxWidthDp] wide, or the view hidden for null. */
    private fun show(ctx: Context, view: ImageView, b: Bitmap?, maxWidthDp: Int) {
        if (b == null) {
            view.setImageDrawable(null)
            view.visibility = View.GONE
            return
        }
        val density = ctx.resources.displayMetrics.density
        view.maxWidth = (maxWidthDp * density).toInt()
        view.adjustViewBounds = true
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.setImageBitmap(b)
        if (needsPlate(b)) {
            view.background = GradientDrawable().apply {
                setColor(0xF2FFFFFF.toInt())
                cornerRadius = 12 * density
            }
            val pad = (8 * density).toInt()
            view.setPadding(pad, pad / 2, pad, pad / 2)
        } else {
            view.background = null
            view.setPadding(0, 0, 0, 0)
        }
        view.visibility = View.VISIBLE
    }

    /** The logo, decoded at no more than about 1024 px on its long side; null when there is none. */
    private fun load(f: File): Bitmap? {
        if (!f.isFile || f.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** True for a mostly dark logo with see-through parts: on the dark background it would all but vanish. */
    private fun needsPlate(b: Bitmap): Boolean {
        val step = maxOf(1, maxOf(b.width, b.height) / 64)
        var seeThrough = 0
        var opaque = 0
        var light = 0.0
        for (y in 0 until b.height step step) {
            for (x in 0 until b.width step step) {
                val c = b.getPixel(x, y)
                if (Color.alpha(c) < 128) {
                    seeThrough++
                    continue
                }
                opaque++
                light += (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255
            }
        }
        return opaque > 0 && seeThrough > (seeThrough + opaque) / 20 && light / opaque < 0.4
    }
}
