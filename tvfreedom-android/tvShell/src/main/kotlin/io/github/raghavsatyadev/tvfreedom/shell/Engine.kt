package io.github.raghavsatyadev.tvfreedom.shell

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinition
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinitionCollection
import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.engine.processor.A2uiCoreMessageProcessor
import androidx.a2ui.model.catalog.A2uiFunctionCollection
import androidx.a2ui.model.processor.A2uiJsonMessageParser
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** The components a TVFreedom design may use. Their properties are checked by the renderer, not by schema. */
object TvCatalog : A2uiCoreCatalog {
    const val ID = "https://tvfreedom.app/a2ui/tv/v1"
    override val id = ID
    override val title = "TVFreedom TV"

    private class Def(override val name: String, override val description: String) : A2uiCoreComponentDefinition {
        override val propertySchema: A2uiSchema = A2uiObjectSchema()
    }

    override val componentDefinitions = A2uiCoreComponentDefinitionCollection(listOf(
        Def("Column", "Children one under another"),
        Def("Row", "Children side by side"),
        Def("Text", "A line of text"),
        Def("Clock", "The time"),
        Def("AppRow", "A row of app tiles the remote moves through"),
    ))
    override val functions = A2uiFunctionCollection(emptyList())
}

/** The components of one surface. [onChange] runs after each update so the screen can redraw. */
class Components : A2uiCoreComponentRegistry {
    val byId = HashMap<String, A2uiComponentPayload>()
    val errors = HashMap<String, String>()
    var onChange: (() -> Unit)? = null
    override fun update(components: List<A2uiComponentPayload>) {
        components.forEach { byId[it.id] = it }
        onChange?.invoke()
    }
    override fun reportError(id: String, exception: A2uiException) { errors[id] = exception.message ?: "error" }
    override fun close() = byId.clear()
}

/** The surface's data, by JSON pointer. A read goes down from the nearest stored parent. */
class Data : A2uiCoreDataModel {
    val byPath = HashMap<String, Any?>()
    var onChange: (() -> Unit)? = null
    override fun update(path: A2uiDataPath, value: Any?) {
        byPath[path.path] = value
        onChange?.invoke()
    }

    override fun get(path: A2uiDataPath): Any? {
        val p = path.path
        if (p in byPath) return byPath[p]
        var parent = p
        while ('/' in parent) {
            parent = parent.substringBeforeLast('/')
            val root = byPath[parent.ifEmpty { "/" }] ?: continue
            return p.removePrefix(parent).trim('/').split('/').fold(root as Any?) { v, key ->
                when (v) {
                    is Map<*, *> -> v[key]
                    is List<*> -> key.toIntOrNull()?.let { v.getOrNull(it) }
                    else -> null
                }
            }
        }
        return null
    }

    override fun close() = byPath.clear()
}

/** One engine: feed it a design (a JSON array of A2UI messages) and the TV's own data. */
class Engine {
    val processor = A2uiCoreMessageProcessor(listOf(TvCatalog), { Data() }, { Components() })
    private val parser = A2uiJsonMessageParser { JsonTokens(it) }

    /** Parse and queue every message of [design]. Throws on JSON or protocol errors, so a bad design is not half shown. */
    fun load(design: String) {
        val messages = (Json.parseToJsonElement(design) as? JsonArray ?: error("a design is a JSON list of messages"))
            .map { parser.parse(it.toString()) }
        messages.forEach { processor.processMessage(it) }
    }

    fun put(surfaceId: String, path: String, value: Any?) =
        processor.processMessage(A2uiUpdateDataModelMessage(surfaceId, path, value))
}
