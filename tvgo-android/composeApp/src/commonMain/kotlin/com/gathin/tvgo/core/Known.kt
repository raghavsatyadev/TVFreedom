package com.gathin.tvgo.core

/**
 * The per-model lists as known-good examples (port of tvgo_core/known.py).
 *
 * [files] maps a path relative to lists/ to its text: "phase1.txt" is the Xiaomi Mi TV (MiTV-MOOR0),
 * "mibox4/phase1.txt" is another model, and "<model>/ai-<stamp>/applied.txt" is a verified earlier run.
 * Only phase files and verified applied.txt files should be passed in.
 */
class Known(private val files: Map<String, String>) {

    /** {model slug: [paths]}: the root model first, then the other models sorted, like known.all_models(). */
    fun allModels(): Map<String, List<String>> {
        val byModel = mutableMapOf<String, MutableList<String>>()
        val root = mutableListOf<String>()
        for (path in files.keys) {
            val parts = path.split('/')
            val usable = when (parts.size) {
                1 -> ListFiles.isPhaseFile(path)
                2 -> if (parts[0].startsWith("ai-")) parts[1] == "applied.txt" else ListFiles.isPhaseFile(path)
                3 -> parts[1].startsWith("ai-") && parts[2] == "applied.txt"
                else -> false
            }
            if (!usable) continue
            if (parts.size == 1 || parts[0].startsWith("ai-")) root += path
            else byModel.getOrPut(parts[0]) { mutableListOf() } += path
        }
        val out = linkedMapOf<String, List<String>>()
        out[ListFiles.ROOT_MODEL] = ordered(root)
        byModel.sortedByKey().forEach { (model, paths) -> out[model] = ordered(paths) }
        return out
    }

    /** Phase files sorted, then learned runs sorted (known.model_files order). */
    private fun ordered(paths: List<String>): List<String> {
        val (learned, phases) = paths.partition { it.endsWith("applied.txt") }
        return phases.sorted() + learned.sorted()
    }

    /** {pkg: example} across every model's lists; the first one seen wins. */
    fun examples(): Map<String, Example> {
        val ex = linkedMapOf<String, Example>()
        allModels().forEach { (model, paths) ->
            paths.forEach { path ->
                ListFiles.readEntries(files.getValue(path)).forEach { e ->
                    ex.getOrPut(e.pkg) { Example(e.action, ListFiles.phaseOf(path), e.comment, model) }
                }
            }
        }
        return ex
    }

    /** {pkg: (action, phase, comment)} from this model's own lists; the last one seen wins. */
    fun forModel(model: String): Map<String, Example> {
        val slug = ListFiles.slug(model)
        val own = linkedMapOf<String, Example>()
        allModels()[slug].orEmpty().forEach { path ->
            ListFiles.readEntries(files.getValue(path)).forEach { e ->
                own[e.pkg] = Example(e.action, ListFiles.phaseOf(path), e.comment, slug)
            }
        }
        return own
    }
}
