@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gathin.tvgo.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.UNINSTALL
import com.gathin.tvgo.flow.ExtrasChoice
import com.gathin.tvgo.flow.ExtrasDraft
import com.gathin.tvgo.flow.HomeDraft

/** One phase of a debloat in everyday words: what it does to the TV, and what happens after. */
data class PlainStep(val phase: Int, val title: String, val detail: String)

/** The chosen [phases], in order, as the user should hear them. */
fun plainSteps(plan: Plan, draft: HomeDraft?, phases: List<Int>, hasLogo: Boolean,
               extras: Pair<ExtrasDraft, ExtrasChoice>? = null): List<PlainStep> = phases.map { p ->
    val rows = plan.remove.filter { it.phase == p }
    val gone = rows.count { it.action == UNINSTALL }
    val apps = "${rows.size} app${if (rows.size == 1) "" else "s"}" + if (gone > 0) " ($gone of them uninstalled)" else ""
    val check = "Then the TV restarts and TVFreedom checks that its home screen opens."
    when (p) {
        1 -> PlainStep(p, "Stop tracking and ads", "Turns off $apps that report what you watch or show ads. $check")
        2 -> PlainStep(p, "Remove apps you don't use", "Turns off $apps nobody picked: demos, games, streaming apps. $check")
        3 -> PlainStep(p, "Clear out the maker's extras", "Turns off $apps the TV maker added: galleries, tips, cleaners. $check")
        4 -> PlainStep(p, "A simpler home screen",
            (if (draft?.installed == null) "Puts a simple home screen on the TV" else "Updates the TV's simple home screen") +
                " with your favourite apps first" + (if (hasLogo) ", your logo and colour" else "") +
                ", and turns off the old one. $check If it doesn't, the old home screen comes back at once.")
        5 -> PlainStep(p, "A clock screensaver", "Makes a clock with the weather and holidays" + (if (hasLogo) " and your logo" else "") + " the screensaver, and turns " +
            "off the old one. $check If it doesn't, the old screensaver comes back at once.")
        6 -> PlainStep(p, "Extras", extrasParts(extras).joinToString("; ").replaceFirstChar { it.uppercase() } +
            ". $check If it doesn't, all of this is put back at once.")
        else -> PlainStep(p, "Phase $p", "Turns off $apps. $check")
    }
}

/** What phase 6 will do, one short part per switched-on extra that has something to do. */
fun extrasParts(extras: Pair<ExtrasDraft, ExtrasChoice>?): List<String> {
    val (d, c) = extras ?: return listOf("nothing")
    return listOfNotNull(
        c.lockTargets(d).size.takeIf { it > 0 }?.let { "stops $it kept app${if (it == 1) "" else "s"} running in the background" },
        "speeds up animations and stops crash reports and background scans".takeIf { c.settingsDue(d) },
        "sends the TV's lookups to ${c.dnsHost} (Private DNS)".takeIf { c.dnsDue(d) },
        "puts SmartTube in place of YouTube (YouTube is turned off, not removed)".takeIf { c.tubeDue(d) },
    ).ifEmpty { listOf("nothing") }
}

/**
 * The steps as a numbered list. While a run goes, [now] is the phase being done and [done] the ones that checked out;
 * [failed] marks the one that stopped the run. Before the run all three are empty, and every step shows its number.
 */
fun LazyListScope.stepList(steps: List<PlainStep>, now: Int? = null, done: List<Int> = emptyList(), failed: Int? = null) {
    steps.forEachIndexed { i, step ->
        item(key = "step:${step.phase}") {
            val state = when (step.phase) {
                failed -> "Stopped here"
                in done -> "Done and checked"
                now -> "Doing this now"
                else -> null
            }
            SegmentedListItem(onClick = {}, shapes = seg(i, steps.size), colors = segColors(),
                leadingContent = {
                    when (step.phase) {
                        failed -> Icon(Icons.Close, null, tint = MaterialTheme.colorScheme.error)
                        in done -> Icon(Icons.Done, null, tint = MaterialTheme.colorScheme.primary)
                        now -> LoadingIndicator(Modifier.size(28.dp))
                        else -> Text("${i + 1}", style = MaterialTheme.typography.titleMedium)
                    }
                },
                supportingContent = { Text(if (state == null) step.detail else "$state. ${step.detail}") },
            ) { Text(step.title) }
        }
    }
}

/** The apps someone installed on the TV, always shown on the plan: which stay, and which a phase takes off. */
fun LazyListScope.userApps(plan: Plan) {
    val apps = plan.userApps.sortedBy { appName(it).lowercase() }
    item(key = "user:label") { GroupLabel("Apps someone installed (${apps.size})") }
    if (apps.isEmpty()) item(key = "user:none") {
        Text("None: every app on this TV came with it.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    apps.forEachIndexed { i, pkg ->
        val row = plan.remove.firstOrNull { it.pkg == pkg }
        item(key = "user:$pkg") {
            SegmentedListItem(onClick = {}, shapes = seg(i, apps.size), colors = segColors(),
                leadingContent = { Icon(if (row == null) Icons.Done else Icons.Close, null,
                    tint = if (row == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
                supportingContent = {
                    Text(when {
                        row == null -> "Stays" + (plan.kept[pkg]?.let { ": $it" } ?: "")
                        row.action == UNINSTALL -> "Uninstalled in phase ${row.phase} (skip that phase to keep it)"
                        else -> "Turned off in phase ${row.phase} (skip that phase to keep it)"
                    } + " · $pkg")
                },
            ) { Text(appName(pkg)) }
        }
    }
}
