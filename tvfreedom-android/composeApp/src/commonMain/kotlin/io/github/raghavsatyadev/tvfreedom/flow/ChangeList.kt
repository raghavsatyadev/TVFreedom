package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.DISABLE
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.ListFiles
import io.github.raghavsatyadev.tvfreedom.store.RunRecord
import io.github.raghavsatyadev.tvfreedom.tv.ExtraOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps

enum class ChangeKind { APP, EXTRA, SCREENSAVER, HOME }

/**
 * One thing TVFreedom changed that Restore can undo on its own. [lines] are the run's file lines that record it:
 * the applied.txt row (APP), the extras.txt undo lines (EXTRA; the two Private DNS lines are one change), or the
 * settings.txt line (SCREENSAVER). [run] is null for the made-up "remove the TVFreedom home screen" item.
 */
data class Change(
    val id: String,
    val run: RunRecord?,
    val kind: ChangeKind,
    val title: String,
    val detail: String,
    val lines: List<String>,
    val entry: ListEntry? = null,
)

data class ChangeGroup(val title: String, val changes: List<Change>)

/** Turns each run's files into single changes, grouped by run, newest first. Pure, so it is tested on the host. */
object ChangeList {

    fun build(records: List<RunRecord>): List<ChangeGroup> {
        val seen = mutableSetOf<Pair<String, String>>()
        return records.sortedByDescending { it.dir.name.substringAfter('-') }.mapNotNull { r ->
            val apps = r.applied.lines().mapNotNull { line ->
                val e = ListFiles.readEntries(line).firstOrNull() ?: return@mapNotNull null
                if (!seen.add(e.action to e.pkg)) return@mapNotNull null // a newer run has it already
                Change("${r.dir.name}:app:${e.action}:${e.pkg}", r, ChangeKind.APP, appTitle(e),
                    "${if (e.action == DISABLE) "Turn back on" else "Put back"} · ${e.pkg}", listOf(line), e)
            }
            val changes = extras(r) + apps + screensaver(r)
            changes.takeIf { it.isNotEmpty() }?.let { ChangeGroup(groupTitle(r.dir.name), it) }
        }
    }

    /** extras.txt as changes, in file order; the Private DNS host and mode lines make one. */
    private fun extras(r: RunRecord): List<Change> {
        val lines = r.extras.lines().filter { it.isNotBlank() }
        val dns = lines.filter { isDns(it) }
        return lines.mapIndexedNotNull { i, line ->
            if (isDns(line)) return@mapIndexedNotNull if (line == dns.first()) Change("${r.dir.name}:dns", r, ChangeKind.EXTRA,
                "Ad blocking off", "Private DNS as it was", dns) else null
            Change("${r.dir.name}:x:$i", r, ChangeKind.EXTRA, extraTitle(line), extraDetail(line), listOf(line))
        }
    }

    private fun screensaver(r: RunRecord): List<Change> = r.settings.lines()
        .filter { it.trim().split(' ').let { p -> p.size == 3 && p[0] == "secure" && p[1] == HomeOps.SCREENSAVER } }
        .map { Change("${r.dir.name}:screensaver", r, ChangeKind.SCREENSAVER, "Screensaver as it was", "", listOf(it)) }

    private fun isDns(line: String) = line.trim().split(' ').getOrNull(2)?.startsWith("private_dns_") == true

    private fun appTitle(e: ListEntry) = e.comment.substringBefore("; restore may need").substringBefore(" [").trim()
        .ifEmpty { e.pkg }

    private fun extraTitle(line: String): String {
        val p = line.trim().split(' ')
        return when (p[0]) {
            "appop" -> "Let it run in the background again"
            "setting" -> SETTING_TITLES[p.getOrElse(2) { "" }] ?: "A TV setting as it was"
            "installed" -> if (p.getOrNull(1) == ExtraOps.SMARTTUBE) "Remove SmartTube" else "Remove an added app"
            "favourites" -> "Home screen favourites as they were"
            else -> line
        }
    }

    private fun extraDetail(line: String): String {
        val p = line.trim().split(' ')
        return when (p[0]) {
            "appop", "installed" -> p.getOrElse(1) { "" }
            else -> ""
        }
    }

    private val SETTING_TITLES = mapOf(
        "window_animation_scale" to "Window animations at their old speed",
        "transition_animation_scale" to "Screen change animations at their old speed",
        "send_action_app_error" to "Crash reports to Google back on",
        "wifi_scan_always_enabled" to "Wi-Fi scanning back on",
    )

    /** "Removed apps, 22 Oct 10:11" from a run folder name such as ai-2026-10-22-101112. */
    fun groupTitle(run: String): String {
        val what = when (run.substringBefore('-')) {
            "home" -> "Home screen and screensaver"
            "extra" -> "Extras"
            else -> "Removed apps"
        }
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})""").find(run) ?: return what
        val (_, month, day, hh, mm) = m.destructured
        val name = MONTHS.getOrNull(month.toInt() - 1) ?: return what
        return "$what, ${day.toInt()} $name $hh:$mm"
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /**
     * [text] without [done] (each line removed once, matched after trimming). Comment lines such as the
     * `# Applied by ...` header stay.
     */
    fun without(text: String, done: List<String>): String {
        val left = done.map { it.trim() }.toMutableList()
        val kept = text.lines().filter { line -> !(line.isNotBlank() && !line.trimStart().startsWith("#") && left.remove(line.trim())) }
        return kept.joinToString("\n")
    }

    /** applied.txt without any row for these (action, package) pairs; the header stays. */
    fun withoutApps(text: String, done: Set<Pair<String, String>>): String = text.lines().filter { line ->
        val e = ListFiles.readEntries(line).firstOrNull()
        e == null || (e.action to e.pkg) !in done
    }.joinToString("\n")

    /** "null" and "" both mean "not set". */
    fun same(a: String, b: String) = a.trim().ifEmpty { "null" } == b.trim().ifEmpty { "null" }
}
