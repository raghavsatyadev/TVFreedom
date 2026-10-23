package io.github.raghavsatyadev.tvfreedom.shell

import androidx.a2ui.model.processor.A2uiJsonReader
import androidx.a2ui.model.processor.A2uiJsonToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** A2UI's pull reader over a kotlinx-serialization tree (no android.util.JsonReader, so it runs in host tests). */
class JsonTokens(element: JsonElement) : A2uiJsonReader {

    private class Tok(val type: A2uiJsonToken, val value: Any? = null)

    private val toks = ArrayList<Tok>()
    private var i = 0

    init {
        flatten(element)
    }

    constructor(text: String) : this(Json.parseToJsonElement(text))

    private fun flatten(e: JsonElement) {
        when (e) {
            is JsonObject -> {
                toks += Tok(A2uiJsonToken.BEGIN_OBJECT)
                for ((k, v) in e) {
                    toks += Tok(A2uiJsonToken.NAME, k)
                    flatten(v)
                }
                toks += Tok(A2uiJsonToken.END_OBJECT)
            }
            is JsonArray -> {
                toks += Tok(A2uiJsonToken.BEGIN_ARRAY)
                e.forEach { flatten(it) }
                toks += Tok(A2uiJsonToken.END_ARRAY)
            }
            JsonNull -> toks += Tok(A2uiJsonToken.NULL)
            is JsonPrimitive -> toks += when {
                e.isString -> Tok(A2uiJsonToken.STRING, e.content)
                e.booleanOrNull != null -> Tok(A2uiJsonToken.BOOLEAN, e.booleanOrNull)
                else -> Tok(A2uiJsonToken.NUMBER, e.content)
            }
        }
    }

    override fun peek(): A2uiJsonToken = toks.getOrNull(i)?.type ?: A2uiJsonToken.END_DOCUMENT

    private fun take(type: A2uiJsonToken): Tok {
        val t = toks.getOrNull(i) ?: throw IllegalStateException("expected $type, got the end")
        check(t.type == type) { "expected $type, got ${t.type}" }
        i++
        return t
    }

    override fun beginObject() { take(A2uiJsonToken.BEGIN_OBJECT) }
    override fun endObject() { take(A2uiJsonToken.END_OBJECT) }
    override fun beginArray() { take(A2uiJsonToken.BEGIN_ARRAY) }
    override fun endArray() { take(A2uiJsonToken.END_ARRAY) }
    override fun hasNext(): Boolean = peek() != A2uiJsonToken.END_OBJECT && peek() != A2uiJsonToken.END_ARRAY &&
        peek() != A2uiJsonToken.END_DOCUMENT
    override fun nextName(): String = take(A2uiJsonToken.NAME).value as String
    override fun nextString(): String {
        val t = toks.getOrNull(i)
        if (t?.type == A2uiJsonToken.NUMBER) { i++; return t.value as String }
        return take(A2uiJsonToken.STRING).value as String
    }
    override fun nextBoolean(): Boolean = take(A2uiJsonToken.BOOLEAN).value as Boolean
    override fun nextDouble(): Double = (take(A2uiJsonToken.NUMBER).value as String).toDouble()
    override fun nextInt(): Int = (take(A2uiJsonToken.NUMBER).value as String).toDouble().toInt()
    override fun nextLong(): Long = (take(A2uiJsonToken.NUMBER).value as String).toDouble().toLong()
    override fun nextNull() { take(A2uiJsonToken.NULL) }

    override fun skipValue() {
        var depth = 0
        do {
            when (toks[i++].type) {
                A2uiJsonToken.BEGIN_OBJECT, A2uiJsonToken.BEGIN_ARRAY -> depth++
                A2uiJsonToken.END_OBJECT, A2uiJsonToken.END_ARRAY -> depth--
                A2uiJsonToken.NAME -> continue
                else -> {}
            }
        } while (depth > 0)
    }

    override fun close() {}
}
