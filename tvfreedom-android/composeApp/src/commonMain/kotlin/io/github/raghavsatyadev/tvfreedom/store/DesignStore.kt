package io.github.raghavsatyadev.tvfreedom.store

import io.github.raghavsatyadev.tvfreedom.core.ListFiles
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path

/** A design the TV took: its name, when, and the JSON. Empty [json] is the design built into the TV's designs screen. */
data class Take(val name: String, val stamp: String, val json: String) {
    val builtIn get() = json.isEmpty()
}

/** The designs each TV took, oldest first, so Undo can go back one. One file per TV (model and serial, like backups). */
class DesignStore(private val fs: FileSystem, private val dir: Path) {

    fun takes(info: TvInfo): List<Take> = runCatching {
        Json.parseToJsonElement(fs.readText(file(info))).jsonArray.map {
            val o = it as JsonObject
            Take(o.str("name"), o.str("stamp"), o.str("json"))
        }
    }.getOrDefault(emptyList())

    fun add(info: TvInfo, take: Take) = write(info, pushed(takes(info), take))

    /** Drop the newest take and return the one the TV should show now (null: none left, show the built-in one). */
    fun undo(info: TvInfo): Take? {
        val left = takes(info).dropLast(1)
        write(info, left)
        return left.lastOrNull()
    }

    private fun write(info: TvInfo, list: List<Take>) = fs.writeText(file(info), buildJsonArray {
        list.forEach { t -> add(buildJsonObject { put("name", t.name); put("stamp", t.stamp); put("json", t.json) }) }
    }.toString())

    private fun file(info: TvInfo) =
        dir / "${ListFiles.slug(info.model)}-${ListFiles.slug(info.serial.ifEmpty { "noserial" })}.json"

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content.orEmpty()

    companion object {
        const val KEEP = 20

        /** [list] with [take] last, the oldest dropped past [KEEP]. */
        fun pushed(list: List<Take>, take: Take): List<Take> = (list + take).takeLast(KEEP)
    }
}
