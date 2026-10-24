package io.github.raghavsatyadev.tvfreedom.core

/**
 * Build the removal plan (port of tvfreedom_core/plan.py): facts from the TV, then the guard (always wins),
 * then this model's known lists, then the community lists (only for a model without its own), then the AI for the
 * rest. Bad or missing AI answers mean keep.
 * Phases 4 and 5 of a model's lists (home screen and screensaver) are left to the home-screen setup, which swaps
 * them safely; they never appear here or as AI examples.
 */
class Planner(private val known: Known, private val keep: Set<String>, private val community: Community? = null) {

    suspend fun build(
        info: TvInfo,
        f: Facts,
        classifier: Classifier?,
        uninstallUserApps: Boolean = false,
        log: (String) -> Unit = {},
        /** Let this model's own list turn off what the guard keeps as OTA (firmware updates), and nothing else. */
        stopUpdates: Boolean = false,
    ): Plan {
        val ownAll = known.forModel(info.model)
        val allExamples = known.examples()
        val own = ownAll.filterValues { it.phase in PHASES }
        val examples = allExamples.filterValues { it.phase in PHASES }
        val homeOwned = (ownAll + allExamples).filterValues { it.phase !in PHASES }.keys
        val remove = mutableListOf<Removal>()
        val kept = linkedMapOf<String, String>()
        val conflicts = mutableListOf<Conflict>()
        val ask = mutableListOf<AskItem>()
        val alreadyOff = f.paths.keys.filter { it in f.disabled }.sorted()
        val shared = if (ownAll.isEmpty()) community?.pick(info) else null
        shared?.let { log("using the ${it.source}") }
        val keepAll = keep + shared?.never.orEmpty()

        fun actionFor(pkg: String, suggested: String): String =
            if (pkg in f.user && uninstallUserApps && suggested == UNINSTALL) UNINSTALL
            else DISABLE // always reversible with pm enable

        for ((pkg, apk) in f.paths.sortedByKey()) {
            if (pkg in f.disabled) continue
            val why = Guard.keepReason(pkg, apk, f.home, info.ime, keepAll, f.uids[pkg])
            if (why.isNotEmpty() && !(why == Guard.OTA && stopUpdates && pkg in own)) {
                kept[pkg] = why
                examples[pkg]?.let { conflicts += Conflict(pkg, why, "${it.action} on ${it.model}") }
                continue
            }
            if (pkg in homeOwned) {
                kept[pkg] = HOME_OWNED
                continue
            }
            val mine = own[pkg]
            if (mine != null) {
                remove += Removal(pkg, actionFor(pkg, mine.action), mine.phase,
                    mine.comment.ifEmpty { "in this model's list" }, "known list")
                continue
            }
            val row = shared?.rows?.get(pkg)
            if (row != null) {
                remove += Removal(pkg, DISABLE, Community.PHASE_OF.getValue(row.category),
                    ListFiles.oneLine(row.note.ifEmpty { Community.title(row.category) }), shared.source)
                continue
            }
            val hint = examples[pkg]?.let { "${it.action} phase ${it.phase} on ${it.model}: ${it.comment}" }
            ask += AskItem(pkg, apk, pkg in f.user, hint)
        }

        val answers = if (ask.isEmpty()) emptyMap()
        else classifier?.classify(info, ask, examples, keep, log) ?: emptyMap()
        for (item in ask) {
            val v = answers[item.pkg]
            when {
                v == null -> kept[item.pkg] = if (classifier == null) "kept: no AI picked" else "kept: the AI wasn't sure"
                v.remove -> remove += Removal(item.pkg, actionFor(item.pkg, UNINSTALL), v.phase,
                    ListFiles.oneLine(v.reason), "agent")
                else -> kept[item.pkg] = "agent: ${v.reason}"
            }
        }
        return Plan(info, remove, kept, conflicts, alreadyOff, ask.map { it.pkg }.filter { it !in answers },
            userApps = f.user)
    }

    companion object {
        const val HOME_OWNED = "home screen: phase 4 or 5 swaps it safely"
    }
}
