package io.github.raghavsatyadev.tvfreedom.store

import io.github.raghavsatyadev.tvfreedom.core.ListFiles
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.Looks
import io.github.raghavsatyadev.tvfreedom.design.TvLook
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path

/** A look the TV's home screen took: its name, when, and the look as JSON ([Looks.toJson]). */
data class Take(val name: String, val stamp: String, val json: String) {
    /** The look, or null for a take from before looks (a design for the old designs app). */
    val look: TvLook? get() = Looks.fromJson(json).getOrNull()
}

/** The looks each TV took, oldest first, so Undo can go back one. One file per TV (model and serial, like backups). */
class DesignStore(private val fs: FileSystem, private val dir: Path) {

    fun takes(info: TvInfo): List<Take> = runCatching {
        Json.parseToJsonElement(fs.readText(file(info))).jsonArray.map {
            val o = it as JsonObject
            Take(o.str("name"), o.str("stamp"), o.str("json"))
        }.filter { it.look != null }
    }.getOrDefault(emptyList())

    fun add(info: TvInfo, take: Take) = write(info, pushed(takes(info), take))

    /** Drop the newest take and return the one the TV should show now (null: none left). */
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
