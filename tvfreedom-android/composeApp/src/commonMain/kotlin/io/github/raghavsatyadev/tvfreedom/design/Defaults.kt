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

    private fun simple(apps: List<String>) = design("Simple", "Clock, greeting and a row of your apps",
        part("root", "Column", "gap" to 40, "children" to ids("top", "apps")),
        part("top", "Row", "gap" to 32, "children" to ids("clock", "when")),
        part("clock", "Clock"),
        part("when", "Column", "gap" to 4, "children" to ids("hello", "date")),
        part("hello", "Greeting", "style" to "title"),
        part("date", "Date", "style" to "body", "color" to "#C4C6CF"),
        part("apps", "AppRow", "labels" to true, "apps" to apps(apps)),
    )

    private fun bigTiles(apps: List<String>) = design("Big tiles", "Large, easy-to-see app tiles in a grid",
        part("root", "Column", "gap" to 24, "children" to ids("hello", "grid")),
        part("hello", "Greeting", "style" to "title"),
        part("grid", "AppGrid", "columns" to 4, "size" to "large", "labels" to true, "apps" to apps(apps)),
    )

    private fun liveTv(live: List<String>, rest: List<String>) = design("Live TV", "Live TV first, then your apps",
        part("root", "Column", "gap" to 32, "children" to ids("top", "watch", "more", "apps")),
        part("top", "Row", "gap" to 24, "children" to ids("clock", "date")),
        part("clock", "Clock", "style" to "headline"),
        part("date", "Date", "style" to "title", "color" to "#C4C6CF"),
        part("watch", "Panel", "color" to "#1F2A44", "child" to "tv"),
        part("tv", "AppRow", "size" to "large", "labels" to true, "focusColor" to "#A8C7FA", "apps" to apps(live)),
        part("more", "Text", "style" to "title", "text" to "Apps"),
        part("apps", "AppRow", "size" to "small", "labels" to true, "apps" to apps(rest)),
    )

    private fun streamingFirst(watch: List<String>, rest: List<String>) =
        design("Streaming", "Your streaming apps up front, the rest below",
            part("root", "Column", "gap" to 28, "background" to "#0E1117", "children" to ids("hello", "watch", "more", "apps")),
            part("hello", "Greeting", "style" to "headline"),
            part("watch", "Panel", "color" to "#1A1F2B", "child" to "stream"),
            part("stream", "AppRow", "size" to "large", "shape" to "square", "apps" to apps(watch)),
            part("more", "Text", "style" to "title", "text" to "More apps", "color" to "#C4C6CF"),
            part("apps", "AppRow", "size" to "small", "shape" to "square", "labels" to true, "apps" to apps(rest)),
        )

    private fun kids(apps: List<String>) = design("Bright", "Colourful round tiles with names",
        part("root", "Column", "gap" to 28, "background" to "#1B1446", "children" to ids("hi", "panel")),
        part("hi", "Text", "style" to "headline", "text" to "Hello!", "color" to "#FFD166"),
        part("panel", "Panel", "color" to "#2D2266", "radius" to 40, "child" to "grid"),
        part("grid", "AppGrid", "columns" to 5, "shape" to "circle", "labels" to true,
            "tileColor" to "#3A2D7A", "focusColor" to "#FFD166", "apps" to apps(apps)),
    )

    private fun minimal(apps: List<String>) = design("Minimal", "Just the time and small app icons",
        part("root", "Column", "gap" to 48, "align" to "center", "background" to "#000000", "children" to ids("clock", "apps")),
        part("clock", "Clock", "color" to "#FFFFFF"),
        part("apps", "AppRow", "size" to "small", "shape" to "circle", "tileColor" to "#16181C", "apps" to apps(apps)),
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
