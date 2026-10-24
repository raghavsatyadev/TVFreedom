package io.github.raghavsatyadev.tvfreedom.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * How the TVFreedom home screen looks: the launcher's look settings (launcher 2.17+). Every default is the stock look.
 * [columns] null keeps the number of columns the TV has now.
 */
data class TvLook(
    val columns: Int? = null,
    val trueBlack: Boolean = false,
    /** "" mixed shapes, "circle", "square" or "flower". */
    val shape: String = "",
    /** "" always shown, "focus" only under the chosen app, "off". */
    val names: String = "",
    /** "" colours follow the chosen app, or one colour as #RRGGBB. */
    val accent: String = "",
)

/** A look with a name and one line about it. */
data class Design(val name: String, val about: String, val look: TvLook)

object Looks {

    val SHAPES = listOf("", "circle", "square", "flower")
    val NAMES = listOf("", "focus", "off")

    /** The home screen as TVFreedom puts it on the TV. */
    val DEFAULT = Design("Default", "The TVFreedom home screen as it comes", TvLook())

    /** The ready-made looks, the default first. */
    val ALL = listOf(
        DEFAULT,
        Design("Round", "Round tiles, colours follow the app you pick", TvLook(shape = "circle")),
        Design("Soft squares", "Rounded square tiles", TvLook(shape = "square")),
        Design("Flowers", "Flower-shaped tiles", TvLook(shape = "flower")),
        Design("Calm", "One soft blue, names only under the app you pick", TvLook(shape = "circle", names = "focus", accent = "#7FA7E0")),
        Design("Big tiles", "Three large tiles a row, easy to see from far away", TvLook(columns = 3, shape = "square")),
        Design("Pure black", "Black background for OLED TVs, no names", TvLook(trueBlack = true, shape = "circle", names = "off")),
        Design("Warm", "One warm orange for everything", TvLook(shape = "flower", accent = "#F2A65A")),
    )

    fun toJson(l: TvLook): String = buildJsonObject {
        l.columns?.let { put("columns", it) }
        put("true_black", l.trueBlack)
        put("tile_shape", l.shape)
        put("names", l.names)
        put("accent", l.accent)
    }.toString()

    /** A look from JSON like [toJson]'s; fails with what is wrong, in words the AI can fix. */
    fun fromJson(s: String): Result<TvLook> = runCatching {
        val o = runCatching { Json.parseToJsonElement(s) }.getOrNull() as? JsonObject
            ?: throw IllegalArgumentException("the answer is not one JSON object")
        fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        val columns = o["columns"]?.let { (it as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException("columns must be a number") }
        require(columns == null || columns in 3..6) { "columns must be 3 to 6" }
        val shape = str("tile_shape")
        require(shape in SHAPES) { "tile_shape must be one of ${SHAPES.joinToString { "\"$it\"" }}" }
        val names = str("names")
        require(names in NAMES) { "names must be one of ${NAMES.joinToString { "\"$it\"" }}" }
        val accent = str("accent")
        require(accent.isEmpty() || Regex("^#[0-9A-Fa-f]{6}$").matches(accent)) { "accent must be \"\" or a colour like \"#7FA7E0\"" }
        TvLook(columns, (o["true_black"] as? JsonPrimitive)?.booleanOrNull ?: false, shape, names, accent.uppercase())
    }

    /** The JSON object in an AI [answer]: from its first { to its last }. */
    fun extract(answer: String): Result<TvLook> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start !in 0..<end) return Result.failure(IllegalArgumentException("the answer has no JSON object"))
        return fromJson(answer.substring(start, end + 1))
    }
}
