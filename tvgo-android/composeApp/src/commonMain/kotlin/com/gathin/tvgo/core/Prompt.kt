package com.gathin.tvgo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The classification prompt and a forgiving parser for the answer (port of tvgo_core/agent.py). */
object Prompt {
    private const val RULES = """You classify packages on an Android TV (%s %s, Android %s) for debloating.
For each package decide "remove" or "keep". When unsure, keep.
Never remove anything needed for boot, the system UI, input or the keyboard, Wi-Fi/network, the remote
and Bluetooth, Cast, voice/Assistant, firmware updates (OTA), the home screen/launcher or setup,
HDMI/Live TV inputs, Google Play services or the Play Store. A code guard re-checks this anyway.
Brands differ (Sony, TCL, Hisense, Philips, Xiaomi, Nvidia, Chromecast, set-top boxes): vendor services
for the panel, picture/sound, tuner, CEC, remote pairing or the vendor's settings must stay. Typical bloat on
any brand: ad/ACR tracking (Alphonso, Samba TV), telemetry/analytics, promo rows and "free channels",
store demo mode, preinstalled games and streaming apps the user did not ask to keep.
The user always keeps: %s.
Phases for removals: %s.
"""
    private const val ANSWER = """
Answer with ONLY a JSON array, one object per package, nothing else:
[{"pkg": "<name>", "verdict": "remove" or "keep", "phase": 1, 2 or 3, "reason": "<= 8 words"}]
"""

    /**
     * The full prompt. [withExamples] adds every decision from the user's other TVs (large models);
     * small on-device models get only the per-package hints to stay inside their context.
     */
    fun build(info: TvInfo, items: List<AskItem>, examples: Map<String, Example>, keep: Set<String>,
              withExamples: Boolean): String = buildString {
        val phases = PHASES.entries.joinToString("; ") { "${it.key} = ${it.value}" }
        append(Text.fill(RULES, info.brand, info.model, info.android, keep.sorted().joinToString(", ").ifEmpty { "-" }, phases))
        if (withExamples) {
            append("\nDecisions made on the user's other TVs (pkg: action, phase, note), use them as examples:\n")
            examples.sortedByKey().forEach { (p, e) -> append("$p: ${e.action}, phase ${e.phase}, ${e.comment.ifEmpty { "-" }} [${e.model}]\n") }
        }
        append("\nPackages to classify, one JSON object per line (apk = install path; user_app = installed by the user\n")
        append("or the vendor as a normal app; hint = what another TV's list did with it):\n")
        items.forEach { append(itemJson(it)).append('\n') }
        append(ANSWER)
    }

    private fun itemJson(i: AskItem): String = buildJsonObject {
        put("pkg", i.pkg)
        put("apk", i.apk)
        put("user_app", i.userApp)
        i.hint?.let { put("hint", it) }
    }.toString()

    private val json = Json { isLenient = true }

    /** {pkg: verdict} for packages in [asked]; anything malformed is dropped (= keep). */
    fun parse(text: String, asked: Set<String>): Map<String, Verdict> {
        var found: List<JsonElement> = emptyList()
        Regex("""\[.*]""", RegexOption.DOT_MATCHES_ALL).find(text)?.let { m ->
            found = runCatching { json.parseToJsonElement(m.value) as? JsonArray }.getOrNull().orEmpty()
        }
        if (found.isEmpty()) { // small models: salvage one object at a time
            found = Regex("""\{[^{}]*}""").findAll(text).mapNotNull { runCatching { json.parseToJsonElement(it.value) }.getOrNull() }.toList()
        }
        val out = linkedMapOf<String, Verdict>()
        for (d in found) {
            if (d !is JsonObject) continue
            val pkg = (d["pkg"] as? JsonPrimitive)?.contentOrNull ?: continue
            if (pkg !in asked) continue
            val verdict = ((d["verdict"] as? JsonPrimitive)?.contentOrNull ?: "keep").lowercase()
            if (verdict != "remove" && verdict != "keep") continue
            val p = (d["phase"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 3
            val reason = ListFiles.oneLine((d["reason"] as? JsonPrimitive)?.contentOrNull ?: "")
            out[pkg] = Verdict(verdict == "remove", if (p in PHASES) p else 3, reason)
        }
        return out
    }
}
