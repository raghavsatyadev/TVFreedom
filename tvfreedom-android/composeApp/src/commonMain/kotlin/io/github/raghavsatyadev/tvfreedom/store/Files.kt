package io.github.raghavsatyadev.tvfreedom.store

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import kotlin.time.Clock

/** Key-value settings kept by the platform (SharedPreferences on Android). */
interface Prefs {
    fun string(key: String, default: String): String
    fun putString(key: String, value: String)
    fun bool(key: String, default: Boolean): Boolean
    fun putBool(key: String, value: Boolean)
}

/** Files bundled with the app at build time: lists/ and termux-setup.sh. */
interface Assets {
    fun read(path: String): ByteArray
    fun list(dir: String): List<String>
}

/** HTTPS downloads. */
fun interface Net {
    suspend fun get(url: String): ByteArray
}

/** Image work the platform does: shrink a picked image to PNG, and sample its pixels as ARGB. */
interface Images {
    fun shrinkToPng(bytes: ByteArray, maxSide: Int): ByteArray
    fun samplePixels(bytes: ByteArray, maxSamples: Int): IntArray
}

/** Local time as yyyy-MM-dd-HHmmss, the stamp run and backup folders are named by. */
fun stampNow(): String {
    val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${t.year}-${p(t.month.ordinal + 1)}-${p(t.day)}-${p(t.hour)}${p(t.minute)}${p(t.second)}"
}

fun FileSystem.readText(p: Path): String = read(p) { readUtf8() }
fun FileSystem.writeText(p: Path, text: String) { p.parent?.let { createDirectories(it) }; write(p) { writeUtf8(text) } }
fun FileSystem.appendText(p: Path, text: String) { appendingSink(p).use { s -> okio.Buffer().writeUtf8(text).let { s.write(it, it.size) } } }
fun FileSystem.readBytes(p: Path): ByteArray = read(p) { readByteArray() }
fun FileSystem.writeBytes(p: Path, bytes: ByteArray) { p.parent?.let { createDirectories(it) }; write(p) { write(bytes) } }
fun FileSystem.dirs(p: Path): List<Path> = listOrNull(p).orEmpty().filter { metadataOrNull(it)?.isDirectory == true }
fun FileSystem.isFile(p: Path): Boolean = metadataOrNull(p)?.isRegularFile == true
fun FileSystem.modified(p: Path): Long = metadataOrNull(p)?.lastModifiedAtMillis ?: 0L
