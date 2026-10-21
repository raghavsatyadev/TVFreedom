package io.github.raghavsatyadev.tvfreedom.core

/** What identify() reads from a TV. */
data class TvInfo(
    val host: String,
    val model: String,
    val device: String = "",
    val brand: String = "",
    val android: String = "",
    val sdk: String = "",
    val serial: String = "",
    val home: String = "",
    val ime: String = "",
)

/** Read-only facts the planner needs (pm list packages -f / -d / -3, HOME resolvers, uids). */
data class Facts(
    val paths: Map<String, String>,
    val disabled: Set<String>,
    val user: Set<String>,
    val home: Set<String>,
    val uids: Map<String, Int>,
)

/** One "disable <pkg>  # comment" line of a list file. */
data class ListEntry(val action: String, val pkg: String, val comment: String)

/** A decision taken on another list: action, phase, comment, model slug. */
data class Example(val action: String, val phase: Int, val comment: String, val model: String)

data class Removal(val pkg: String, val action: String, val phase: Int, val reason: String, val source: String)

data class Conflict(val pkg: String, val keptAs: String, val earlier: String)

/** A package the guard and the lists left open: the AI decides. */
data class AskItem(val pkg: String, val apk: String, val userApp: Boolean, val hint: String? = null)

data class Verdict(val remove: Boolean, val phase: Int, val reason: String)

data class Plan(
    val info: TvInfo,
    val remove: List<Removal>,
    val kept: Map<String, String>,
    val conflicts: List<Conflict>,
    val alreadyOff: List<String>,
    val unanswered: List<String>,
    /** Apps the user installed (pm list packages -3), shown on the plan whether they stay or go. */
    val userApps: Set<String> = emptySet(),
) {
    val phases: List<Int> get() = remove.map { it.phase }.distinct().sorted()
}

/** The decision maker. It only classifies: no tools, no TV. Missing answers mean keep. */
interface Classifier {
    val name: String
    suspend fun classify(
        info: TvInfo,
        items: List<AskItem>,
        examples: Map<String, Example>,
        keep: Set<String>,
        log: (String) -> Unit,
    ): Map<String, Verdict>

    /** Free what the classifier holds (an on-device model). */
    fun close() {}
}

const val DISABLE = "disable"
const val UNINSTALL = "uninstall"

val PHASES = mapOf(
    1 to "telemetry, ads, tracking, promos",
    2 to "apps the user does not use (streaming apps, games, demos)",
    3 to "vendor extras (galleries, players, tips, cleaners, store demo)",
)
