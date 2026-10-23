package io.github.raghavsatyadev.tvfreedom.design

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A design for the TV's designs screen: a name, one line about it, and the A2UI JSON the TV draws. */
data class Design(val name: String, val about: String, val json: String)

/**
 * The ready-made designs, built from what the TV kept: its apps (favourites first), and whether it has live TV
 * or several streaming apps. The block names and properties match tvShell's Guide (content call catalog).
 */
object Defaults {

    const val CATALOG = "https://tvfreedom.app/a2ui/tv/v1"

    /** Every design that suits a TV with [apps] (package names) and [favourites]. */
    fun all(apps: List<String>, favourites: List<String> = emptyList()): List<Design> {
        val ordered = (favourites.filter { it in apps || apps.isEmpty() } + apps).distinct()
        val streaming = ordered.filter { it in STREAMING }
        val live = ordered.filter { it in LIVE_TV }
        val list = mutableListOf(simple(ordered), bigTiles(ordered))
        if (live.isNotEmpty()) list += liveTv(live, ordered - live.toSet())
        if (streaming.size >= 2) list += streamingFirst(streaming, ordered - streaming.toSet())
        list += kids(ordered)
        list += minimal(ordered)
        return list
    }

    // Each design is Material 3 Expressive: a seed colour and scheme give its tones; tiles are M3E shapes with
    // one-colour icons that round into a circle on focus.

    private fun simple(apps: List<String>) = design("Simple", "The time in a soft shape, a greeting and your apps",
        part("root", "Column", "gap" to 40, "seed" to "#6750A4", "scheme" to "tonalSpot", "children" to ids("top", "apps")),
        part("top", "Row", "gap" to 36, "children" to ids("badge", "when")),
        part("badge", "Panel", "shape" to "cookie", "color" to "primaryContainer", "padding" to 32, "child" to "clock"),
        part("clock", "Clock", "style" to "headline", "color" to "onPrimaryContainer"),
        part("when", "Column", "gap" to 6, "children" to ids("hello", "date")),
        part("hello", "Greeting", "style" to "headline"),
        part("date", "Date", "style" to "title", "color" to "onSurfaceVariant", "weight" to 400),
        part("apps", "AppRow", "labels" to true, "apps" to apps(apps)),
    )

    private fun bigTiles(apps: List<String>) = design("Big tiles", "Large, easy-to-see app shapes in a grid",
        part("root", "Column", "gap" to 20, "seed" to "#006A6A", "scheme" to "vibrant", "children" to ids("top", "grid")),
        part("top", "Row", "gap" to 20, "children" to ids("hello", "clock")),
        part("hello", "Greeting", "style" to "title"),
        part("clock", "Clock", "style" to "title", "color" to "primary"),
        part("grid", "AppGrid", "columns" to 4, "size" to "large", "labels" to true, "apps" to apps(apps)),
    )

    private fun liveTv(live: List<String>, rest: List<String>) = design("Live TV", "Live TV on its own card, your apps beside it",
        part("root", "Column", "gap" to 28, "seed" to "#0B57D0", "scheme" to "vibrant", "children" to ids("top", "main")),
        part("top", "Row", "gap" to 24, "children" to ids("clock", "date")),
        part("clock", "Clock", "style" to "headline"),
        part("date", "Date", "style" to "title", "color" to "onSurfaceVariant", "weight" to 400),
        part("main", "Row", "gap" to 40, "children" to ids("watch", "apps")),
        part("watch", "Panel", "color" to "secondaryContainer", "radius" to 48, "child" to "watchCol"),
        part("watchCol", "Column", "gap" to 4, "align" to "center", "children" to ids("watchTitle", "tv")),
        part("watchTitle", "Text", "style" to "title", "text" to "Watch TV", "color" to "onSecondaryContainer"),
        part("tv", "AppRow", "shape" to "burst", "labels" to true, "tileColor" to "tertiaryContainer",
            "iconColor" to "onTertiaryContainer", "focusColor" to "tertiary", "focusIconColor" to "onTertiary",
            "labelColor" to "onSecondaryContainer", "focusLabelColor" to "onSecondaryContainer", "apps" to apps(live)),
        part("apps", "AppGrid", "columns" to 4, "size" to "small", "labels" to true, "apps" to apps(rest)),
    )

    private fun streamingFirst(watch: List<String>, rest: List<String>) =
        design("Streaming", "Your streaming apps on a big card, the rest below",
            part("root", "Column", "gap" to 24, "seed" to "#B3261E", "scheme" to "fidelity",
                "children" to ids("hello", "watch", "apps")),
            part("hello", "Greeting", "style" to "title"),
            part("watch", "Panel", "color" to "surfaceContainerHigh", "radius" to 40, "padding" to 20, "child" to "stream"),
            part("stream", "AppRow", "shape" to "square", "labels" to true, "apps" to apps(watch)),
            part("apps", "AppRow", "size" to "small", "shape" to "pillow", "labels" to true, "tileColor" to "secondaryContainer",
                "iconColor" to "onSecondaryContainer", "apps" to apps(rest)),
        )

    private fun kids(apps: List<String>) = design("Bright", "Big, playful flower shapes in sunny colours",
        part("root", "Column", "gap" to 24, "seed" to "#FF8A00", "scheme" to "vibrant", "children" to ids("top", "grid")),
        part("top", "Row", "gap" to 28, "children" to ids("sun", "hi")),
        part("sun", "Panel", "shape" to "burst", "color" to "tertiary", "padding" to 28, "child" to "clock"),
        part("clock", "Clock", "style" to "title", "color" to "onTertiary"),
        part("hi", "Text", "style" to "headline", "text" to "Hello!", "color" to "primary", "weight" to 800),
        part("grid", "AppGrid", "columns" to 5, "shape" to "flower", "labels" to true,
            "tileColor" to "primary", "iconColor" to "onPrimary", "labelColor" to "onSurface",
            "focusColor" to "tertiary", "focusIconColor" to "onTertiary", "apps" to apps(apps)),
    )

    private fun minimal(apps: List<String>) = design("Minimal", "A big, light clock and small round apps",
        part("root", "Column", "gap" to 48, "align" to "center", "seed" to "#5F6368", "scheme" to "monochrome",
            "background" to "surfaceContainerLowest", "children" to ids("time", "apps")),
        part("time", "Column", "align" to "center", "gap" to 0, "children" to ids("clock", "date")),
        part("clock", "Clock", "style" to "display"),
        part("date", "Date", "style" to "body"),
        part("apps", "AppRow", "size" to "small", "shape" to "circle", "tileColor" to "surfaceContainerHigh",
            "iconColor" to "onSurfaceVariant", "focusColor" to "onSurface", "focusIconColor" to "surface", "apps" to apps(apps)),
    )

    /** A literal app list, or the TV's own list when the phone could not read it. */
    private fun apps(pkgs: List<String>): JsonElement =
        if (pkgs.isEmpty()) buildJsonObject { put("path", "/apps") } else JsonArray(pkgs.map(::JsonPrimitive))

    private fun ids(vararg id: String) = JsonArray(id.map(::JsonPrimitive))

    private fun part(id: String, type: String, vararg props: Pair<String, Any>) = buildJsonObject {
        put("id", id)
        put("component", type)
        props.forEach { (k, v) ->
            put(k, when (v) {
                is JsonElement -> v
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> error("unsupported $v")
            })
        }
    }

    private fun design(name: String, about: String, vararg parts: JsonObject) = Design(name, about, buildJsonArray {
        add(buildJsonObject {
            put("version", "v0.9")
            put("createSurface", buildJsonObject { put("surfaceId", "home"); put("catalogId", CATALOG) })
        })
        add(buildJsonObject {
            put("version", "v0.9")
            put("updateComponents", buildJsonObject { put("surfaceId", "home"); put("components", JsonArray(parts.toList())) })
        })
    }.toString())

    /** Live TV apps on Google TV, AOSP, MediaTek and Amlogic TVs. */
    val LIVE_TV = setOf("com.google.android.tv", "com.android.tv", "com.mediatek.wwtv.tvcenter", "com.droidlogic.tvsource")

    val STREAMING = setOf(
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.google.android.youtube.tvmusic",
        "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "in.startv.hotstar.dplus.tv", "com.disney.disneyplus",
        "com.jio.media.stb.ondemand", "com.sonyliv", "com.graymatrix.did", "com.spotify.tv.android", "com.plexapp.android",
        "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta", "org.jellyfin.androidtv", "org.xbmc.kodi",
        "com.apple.atve.androidtv.appletv", "com.mxtech.videoplayer.television",
    )
}
