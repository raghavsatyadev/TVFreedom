package io.github.raghavsatyadev.tvfreedom.store

import io.github.raghavsatyadev.tvfreedom.core.BrandColors
import okio.FileSystem
import okio.Path

/**
 * A company's logo and home-screen colour, kept on the phone so every TV it debloats gets the same look.
 * The logo is stored shrunk (at most [MAX_SIDE] px on its long side) as PNG, so transparency stays.
 */
class Brand(private val fs: FileSystem, filesDir: Path, private val images: Images, private val settings: Settings) {

    val file = filesDir / "brand" / "logo.png"

    val hasLogo: Boolean get() = fs.isFile(file)

    val logoSize: Long get() = fs.metadataOrNull(file)?.size ?: 0L

    val logo: ByteArray? get() = file.takeIf { fs.isFile(it) }?.let { fs.readBytes(it) }?.takeIf { it.isNotEmpty() }

    var color: String
        get() = settings.brandColor
        set(v) {
            settings.brandColor = v
        }

    /** Shrink and keep the picked image, and return the colours that go with it (the first is now in use). */
    fun import(picked: ByteArray): List<String> {
        val png = images.shrinkToPng(picked, MAX_SIDE)
        fs.writeBytes(file, png)
        return colours(png).also { color = it.first() }
    }

    /** The colours that go with the stored logo, or none without one. */
    fun suggestions(): List<String> = logo?.let { runCatching { colours(it) }.getOrNull() }.orEmpty()

    fun clear() {
        fs.delete(file, mustExist = false)
        color = ""
    }

    private fun colours(png: ByteArray): List<String> = BrandColors.suggest(images.samplePixels(png, 96))

    companion object {
        const val MAX_SIDE = 800
    }
}
