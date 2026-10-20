package com.gathin.tvgo.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.gathin.tvgo.core.BrandColors
import java.io.File

/**
 * A company's logo and home-screen colour, kept on the phone so every TV it debloats gets the same look.
 * The logo is stored shrunk (at most [MAX_SIDE] px on its long side) as PNG, so transparency stays.
 */
class Brand(private val ctx: Context, private val settings: Settings) {

    val file = File(File(ctx.filesDir, "brand"), "logo.png")

    val logo: ByteArray? get() = file.takeIf { it.isFile && it.length() > 0 }?.readBytes()

    var color: String
        get() = settings.brandColor
        set(v) {
            settings.brandColor = v
        }

    /** Read the picked image, shrink and keep it, and return the colours that go with it (the first is now in use). */
    fun import(uri: Uri): List<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "that file is not an image this phone can read" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not read the image")
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return colours(bitmap).also { color = it.first() }
    }

    /** The colours that go with the stored logo, or none without one. */
    fun suggestions(): List<String> {
        if (!file.isFile) return emptyList()
        val b = BitmapFactory.decodeFile(file.path) ?: return emptyList()
        return colours(b)
    }

    fun clear() {
        file.delete()
        color = ""
    }

    private fun colours(b: Bitmap): List<String> {
        val step = maxOf(1, maxOf(b.width, b.height) / 96)
        val px = buildList {
            for (y in 0 until b.height step step) for (x in 0 until b.width step step) add(b.getPixel(x, y))
        }.toIntArray()
        return BrandColors.suggest(px)
    }

    companion object {
        const val MAX_SIDE = 800
    }
}
