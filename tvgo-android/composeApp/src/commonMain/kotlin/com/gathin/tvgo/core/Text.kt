package com.gathin.tvgo.core

import kotlin.math.roundToInt

/** Small text helpers that work on every platform (no String.format, no java.util). */
object Text {
    private const val HEX = "0123456789abcdef"

    /** Two lowercase hex digits per byte. */
    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { b -> val v = b.toInt() and 0xff; append(HEX[v shr 4]); append(HEX[v and 0xf]) }
    }

    /** #RRGGBB from 0..255 channels. */
    fun rgb(r: Int, g: Int, b: Int): String = "#" + hex(byteArrayOf(r.toByte(), g.toByte(), b.toByte())).uppercase()

    /** Replace each %s in order with the next of [args]. */
    fun fill(template: String, vararg args: Any?): String {
        var i = 0
        return Regex("%s").replace(template) { args.getOrNull(i++).toString() }
    }

    /** [v] with one decimal, rounded half up ("1.5"). */
    fun oneDecimal(v: Double): String {
        val t = (v * 10).roundToInt()
        return "${t / 10}.${t % 10}"
    }

    /** What went wrong, for the log: the message, else the exception's class name. */
    fun why(t: Throwable): String = t.message ?: t::class.simpleName ?: "error"
}

/** The map's entries sorted by key, as an ordered map (the common stand-in for toSortedMap()). */
fun <V> Map<String, V>.sortedByKey(): Map<String, V> = entries.sortedBy { it.key }.associate { it.key to it.value }
