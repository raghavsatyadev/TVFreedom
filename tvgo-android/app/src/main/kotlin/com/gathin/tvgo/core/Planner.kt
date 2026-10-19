package com.gathin.tvgo.core

/**
 * Build the removal plan (port of tvgo_core/plan.py): facts from the TV, then the guard (always wins),
 * then this model's known lists, then the AI for the rest. Bad or missing AI answers mean keep.
 */
class Planner(private val known: Known, private val keep: Set<String>) {

    suspend fun build(
        info: TvInfo,
        f: Facts,
        classifier: Classifier?,
        uninstallUserApps: Boolean = false,
        log: (String) -> Unit = {},
    ): Plan {
        val own = known.forModel(info.model)
        val examples = known.examples()
        val remove = mutableListOf<Removal>()
        val kept = linkedMapOf<String, String>()
        val conflicts = mutableListOf<Conflict>()
        val ask = mutableListOf<AskItem>()
        val alreadyOff = f.paths.keys.filter { it in f.disabled }.sorted()

        fun actionFor(pkg: String, suggested: String): String =
            if (pkg in f.user && uninstallUserApps && suggested == UNINSTALL) UNINSTALL
            else DISABLE // always reversible with pm enable

        for ((pkg, apk) in f.paths.toSortedMap()) {
            if (pkg in f.disabled) continue
            val why = Guard.keepReason(pkg, apk, f.home, info.ime, keep, f.uids[pkg])
            if (why.isNotEmpty()) {
                kept[pkg] = why
                examples[pkg]?.let { conflicts += Conflict(pkg, why, "${it.action} on ${it.model}") }
                continue
            }
            val mine = own[pkg]
            if (mine != null) {
                remove += Removal(pkg, actionFor(pkg, mine.action), mine.phase,
                    mine.comment.ifEmpty { "in this model's list" }, "known list")
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
                v == null -> kept[item.pkg] = if (classifier == null) "no AI: kept" else "agent: agent gave no answer"
                v.remove -> remove += Removal(item.pkg, actionFor(item.pkg, UNINSTALL), v.phase,
                    ListFiles.oneLine(v.reason), "agent")
                else -> kept[item.pkg] = "agent: ${v.reason}"
            }
        }
        return Plan(info, remove, kept, conflicts, alreadyOff, ask.map { it.pkg }.filter { it !in answers })
    }
}
