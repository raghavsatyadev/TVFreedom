package com.gathin.tvgo.core

/** The repo's list format: "disable <pkg>  # comment" or "uninstall <pkg>", # comment lines. */
object ListFiles {
    const val ROOT_MODEL = "mitv-moor0" // the model whose lists sit directly in lists/

    fun slug(model: String): String =
        model.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "unknown" }

    fun readEntries(text: String): List<ListEntry> = text.lines().mapNotNull { line ->
        val body = line.substringBefore('#')
        val comment = if ('#' in line) line.substringAfter('#').trim() else ""
        val parts = body.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (parts.size == 2 && (parts[0] == DISABLE || parts[0] == UNINSTALL)) ListEntry(parts[0], parts[1], comment)
        else null
    }

    /** phase2b.txt is phase 2; applied.txt from a run has no phase: treat as vendor extras (3). */
    fun phaseOf(path: String): Int =
        Regex("""phase(\d+)""").find(path.substringAfterLast('/'))?.groupValues?.get(1)?.toInt() ?: 3

    fun isPhaseFile(path: String): Boolean = Regex("""^phase[^/]*\.txt$""").matches(path.substringAfterLast('/'))

    /** One line of text safe to put after a # in a list file: no newlines, no #, at most 80 chars. */
    fun oneLine(text: String, max: Int = 80): String =
        text.replace('#', ' ').split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ").take(max)

    /** A plan's phase file, in the same shape tvgo.py writes. */
    fun formatPhase(info: TvInfo, phase: Int, rows: List<Removal>): String = buildString {
        append("# ${oneLine(info.brand)} ${oneLine(info.model)} (${oneLine(info.device)}, Android ${oneLine(info.android)}).")
        append(" Phase $phase: ${PHASES[phase] ?: "other"}. Planned by the tvgo app.\n")
        append("# Undo: Restore in the tvgo app (or python tv.py restore <this file>)\n")
        rows.forEach { r ->
            val note = r.reason + if (r.action == UNINSTALL) "; restore may need the Play Store" else ""
            append("${r.action.padEnd(9)} ${r.pkg.padEnd(48)} # ${oneLine(note, 120)} [${r.source}]\n")
        }
    }

    fun formatApplied(info: TvInfo, phases: List<Int>, rows: List<Removal>): String = buildString {
        append("# Applied by the tvgo app to ${oneLine(info.model)} ${oneLine(info.serial)}: phases $phases\n")
        rows.forEach { append("${it.action} ${it.pkg}  # ${oneLine(it.reason)}\n") }
    }
}
