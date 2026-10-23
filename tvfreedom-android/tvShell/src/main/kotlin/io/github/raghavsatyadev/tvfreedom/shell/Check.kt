package io.github.raghavsatyadev.tvfreedom.shell

import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tries a design in a throwaway engine before the TV keeps it. The answer is short and plain, so the
 * phone (or its AI) can fix the design: "ok", or what is wrong.
 */
object Check {

    /** Null when [design] can be shown with [apps]; else why not. */
    suspend fun problem(design: String, apps: List<Map<String, String>> = emptyList()): String? = coroutineScope {
        val engine = Engine()
        val sent = ArrayList<String>()
        // Listen for errors before messages flow, so the engine's own reason is never missed.
        val jobs = listOf(
            launch(start = CoroutineStart.UNDISPATCHED) {
                engine.processor.outboundEvents.collect {
                    sent += if (it is A2uiClientErrorMessage) {
                        val at = (it.context["path"] as? String)?.let { p -> " (at $p)" }.orEmpty()
                        it.message + at + if ("not found in catalog" in it.message) ". The TV only knows ${TvCatalog.NAMES.joinToString()}." else ""
                    } else it.toString()
                }
            },
            launch { engine.processor.collectMessages() },
        )
        try {
            runCatching { engine.load(design) }.exceptionOrNull()?.let {
                return@coroutineScope "Not a design: ${it.message}"
            }
            val surface = withTimeoutOrNull(3_000) {
                engine.processor.activeSurfaces.first { it.isNotEmpty() }.first() as A2uiCoreSurfaceModel
            } ?: return@coroutineScope "No screen: the design needs a createSurface message first."
            engine.put(surface.id, "/apps", apps)
            if (!engine.settle(surface)) return@coroutineScope "The design took too long to load."
            sent.firstOrNull()?.let { return@coroutineScope "The design has an error: $it" }
            tree(surface)
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    /** Walks the components from "root": every child must exist, every type must be known, and the remote needs a tile. */
    fun tree(surface: A2uiCoreSurfaceModel): String? {
        val reg = surface.componentRegistry as Components
        reg.errors.entries.firstOrNull()?.let { (id, e) -> return "Part \"$id\" has an error: $e" }
        if ("root" !in reg.byId) return "No part has the id \"root\"; the screen starts there."
        val installed = packagesOf((surface.dataModel as Data).byPath["/apps"]).toSet()
        val seen = HashSet<String>()
        var tiles = 0
        fun walk(id: String, from: String): String? {
            val c = reg.byId[id] ?: return "Part \"$from\" lists \"$id\", but no part has that id."
            if (!seen.add(id)) return "Part \"$id\" is used twice or inside itself."
            if (c.type !in TvCatalog.NAMES) return "Part \"$id\" is a ${c.type}; the TV only knows ${TvCatalog.NAMES.joinToString()}."
            if (c.type == "AppRow" || c.type == "AppGrid") tiles += packagesOf(surface.prop(id, "apps")).count { it in installed }
            for (child in children(c.properties)) walk(child, id)?.let { return it }
            return null
        }
        walk("root", "root")?.let { return it }
        if (tiles == 0) return "Nothing to pick with the remote: add an AppRow or AppGrid with apps the TV has."
        return null
    }

    /** A component's child ids: "children" (a list) and "child" (one). */
    fun children(props: Map<String, Any?>): List<String> =
        (props["children"] as? List<*>).orEmpty().filterIsInstance<String>() + listOfNotNull(props["child"] as? String)
}
