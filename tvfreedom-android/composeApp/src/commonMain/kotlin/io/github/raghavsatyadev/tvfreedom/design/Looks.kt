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
    /** "" favourites docked at the bottom, "wall" every app from the top, "banner" wide app pictures. Launcher 2.19+. */
    val layout: String = "",
    /** "" a small clock in the corner, "big" a huge one, "analog" a clock face. */
    val clock: String = "",
    /** "" a shape that follows the app, "glow" a colour wash from the top, "plain" one flat colour. */
    val background: String = "",
)

/** A look with a name and one line about it. */
data class Design(val name: String, val about: String, val look: TvLook)

object Looks {

    val SHAPES = listOf("", "circle", "square", "flower")
    val NAMES = listOf("", "focus", "off")
    val LAYOUTS = listOf("", "wall", "banner")
    val CLOCKS = listOf("", "big", "analog")
    val BACKGROUNDS = listOf("", "glow", "plain")

    /** The home screen as TVFreedom puts it on the TV. */
    val DEFAULT = Design("Default", "The TVFreedom home screen as it comes", TvLook())

    /** The ready-made looks, the default first. */
    val ALL = listOf(
        DEFAULT,
        Design("Big clock", "A huge clock, your apps below it",
            TvLook(shape = "circle", names = "focus", clock = "big", background = "glow")),
        Design("Banners", "Wide app pictures, like Google TV",
            TvLook(columns = 4, layout = "banner", background = "plain")),
        Design("App wall", "Every app at once, from the top",
            TvLook(columns = 6, shape = "square", names = "focus", layout = "wall", background = "glow")),
        Design("Watch", "A clock face on pure black",
            TvLook(trueBlack = true, shape = "circle", names = "off", clock = "analog")),
        Design("Calm", "One soft blue, flat and quiet",
            TvLook(shape = "circle", names = "focus", accent = "#7FA7E0", background = "plain")),
        Design("Warm", "Orange glow, flower tiles, big clock",
            TvLook(shape = "flower", accent = "#F2A65A", clock = "big", background = "glow")),
    )

    fun toJson(l: TvLook): String = buildJsonObject {
        l.columns?.let { put("columns", it) }
        put("true_black", l.trueBlack)
        put("tile_shape", l.shape)
        put("names", l.names)
        put("accent", l.accent)
        put("layout", l.layout)
        put("clock", l.clock)
        put("background", l.background)
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
        fun pick(k: String, from: List<String>) = str(k).also { require(it in from) { "$k must be one of ${from.joinToString { "\"$it\"" }}" } }
        val layout = pick("layout", LAYOUTS)
        val clock = pick("clock", CLOCKS)
        val background = pick("background", BACKGROUNDS)
        TvLook(columns, (o["true_black"] as? JsonPrimitive)?.booleanOrNull ?: false, shape, names, accent.uppercase(), layout, clock, background)
    }

    /** The JSON object in an AI [answer]: from its first { to its last }. */
    fun extract(answer: String): Result<TvLook> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start !in 0..<end) return Result.failure(IllegalArgumentException("the answer has no JSON object"))
        return fromJson(answer.substring(start, end + 1))
    }
}
