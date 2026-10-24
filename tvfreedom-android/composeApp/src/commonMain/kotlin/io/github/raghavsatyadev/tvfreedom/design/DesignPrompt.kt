package io.github.raghavsatyadev.tvfreedom.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * What the AI gets to write or fix a TV design, and how its answer is read. The phone only checks that the answer is
 * a JSON array; the TV checks the design itself and says what is wrong, which goes into the next try.
 */
object DesignPrompt {

    fun build(catalog: String, apps: List<String>, request: String, current: String?, bad: String?, why: String?) =
        buildString {
            appendLine("You design the home screen of an Android TV. Answer with only the design: one JSON array,")
            appendLine("no words or code fences before or after it.")
            appendLine()
            appendLine("How to write a design for this TV:")
            appendLine(catalog.trim())
            appendLine()
            appendLine("The TV's apps (package names, favourites first):")
            appendLine(apps.joinToString(", ").ifEmpty { "unknown: bind {\"path\":\"/apps\"}" })
            appendLine()
            appendLine("What the user wants:")
            appendLine(request.trim())
            if (!current.isNullOrEmpty()) {
                appendLine()
                appendLine("The design on the TV now (change it as asked, keep the rest):")
                appendLine(current)
            }
            if (bad != null && why != null) {
                appendLine()
                appendLine("Your last answer did not work: $why")
                appendLine("It was:")
                appendLine(bad)
                appendLine("Fix that and answer with the whole design again.")
            }
        }

    /** The design in [answer]: from its first [ to its last ], as compact JSON. Fails with why it isn't one. */
    fun extract(answer: String): Result<String> = runCatching {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        require(start in 0..<end) { "the answer has no JSON array" }
        val parsed = runCatching { Json.parseToJsonElement(answer.substring(start, end + 1)) }
            .getOrElse { throw IllegalArgumentException("the JSON does not parse: ${it.message?.lineSequence()?.first()}") }
        require(parsed is JsonArray && parsed.isNotEmpty()) { "the design must be a non-empty JSON array" }
        parsed.toString()
    }

    /** A short name for a take, from what the user asked. */
    fun title(request: String): String {
        val line = request.trim().lineSequence().first().trim()
        return if (line.length <= 40) line else line.take(39).trimEnd() + "…"
    }
}
