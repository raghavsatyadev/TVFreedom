@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.core.PHASES
import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.UNINSTALL
import io.github.raghavsatyadev.tvfreedom.core.sortedByKey
import io.github.raghavsatyadev.tvfreedom.flow.HomeDraft
import io.github.raghavsatyadev.tvfreedom.store.Settings

/** Manage: everything the short plan leaves out. Each step, each app and why, the kept apps and the log. */
fun LazyListScope.planDetails(vm: TvFreedomViewModel, plan: Plan, showKept: Boolean, toggleKept: () -> Unit,
                              showLog: Boolean, toggleLog: () -> Unit) {
    val s = vm.state
    val draft = s.homeDraft
    val chosen = vm.chosenPhases()
    val steps = if (chosen.isEmpty()) emptyList() else plainSteps(plan, draft, chosen, vm.brand.hasLogo, s.extrasDraft?.let { it to s.extras })
    item(key = "steps:label") { GroupLabel(if (steps.isEmpty()) "Nothing to do yet" else "Step by step") }
    stepList(steps)
    userApps(plan)
    plan.phases.forEach { phase(vm, plan, it) }
    if (draft != null) listOf(4, 5).forEach { homePhase(vm, draft, it) }
    s.extrasDraft?.let { extrasPhase(vm, it) }
    if (plan.unanswered.isNotEmpty()) item {
        Text(if (vm.settings.ai == Settings.Ai.NONE) "${plan.unanswered.size} apps no list knows were kept."
            else "The AI had no answer for ${plan.unanswered.size} apps, so they were kept.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
        TextButton(onClick = toggleKept) { Text(if (showKept) "Hide kept apps" else "Kept apps (${plan.kept.size})") }
    }
    if (showKept) {
        val kept = plan.kept.sortedByKey().toList()
        kept.forEachIndexed { i, (p, why) ->
            item(key = "kept:$p") {
                SegmentedListItem(onClick = {}, shapes = seg(i, kept.size), colors = segColors(),
                    supportingContent = { Text(why) }) { Text(appName(p), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    details(s.log, showLog, toggleLog)
}

/** One phase: a switch to include it, then its apps, as one connected group. */
private fun LazyListScope.phase(vm: TvFreedomViewModel, plan: Plan, phase: Int) {
    val rows = plan.remove.filter { it.phase == phase }
    val on = phase in vm.state.phases
    val n = rows.size + 1
    item(key = "label:$phase") { GroupLabel(groupTitle(phase)) }
    item(key = "phase:$phase") {
        SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(phase) }, shapes = seg(0, n), colors = segColors(),
            enabled = !vm.state.busy,
            supportingContent = { Text("${rows.size} apps · ${PHASES[phase] ?: "other"}") },
            trailingContent = { Switch(on, null) }) { Text(if (on) "Included" else "Skipped") }
    }
    rows.forEachIndexed { i, r ->
        item(key = "row:$phase:${r.pkg}") {
            SegmentedListItem(onClick = {}, shapes = seg(i + 1, n), colors = segColors(), enabled = on,
                supportingContent = { Text("${if (r.action == UNINSTALL) "Uninstall" else "Turn off"} · ${r.reason}\n${r.pkg}") },
            ) { Text(appName(r.pkg), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Phase 4 or 5: a switch, then what it does on this TV; phase 4 also opens the home screen settings. */
private fun LazyListScope.homePhase(vm: TvFreedomViewModel, draft: HomeDraft, phase: Int) {
    val on = phase in vm.state.phases
    val due = if (phase == 4) draft.launcherDue else draft.screensaverDue
    val n = 2
    item(key = "label:$phase") { GroupLabel(groupTitle(phase)) }
    item(key = "phase:$phase") {
        SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(phase) }, shapes = seg(0, n), colors = segColors(),
            enabled = !vm.state.busy,
            supportingContent = { Text(if (due) "Not on this TV yet" else "Already on; include it to send new settings") },
            trailingContent = { Switch(on, null) }) { Text(if (on) "Included" else "Skipped") }
    }
    item(key = "home:$phase:preview") {
        // What the TV will look like, before anything is sent.
        val logo = rememberLogo(vm)
        val c = vm.state.homeConfig ?: draft.config
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (phase == 4) HomePreview(logo, vm.brand.color, c.favourites, c.columns)
            else DreamPreview(logo, c.place)
        }
    }
    item(key = "home:$phase:settings") {
        SegmentedListItem(onClick = { vm.go(Screen.HOME) }, shapes = seg(n - 1, n), colors = segColors(), enabled = !vm.state.busy,
            leadingContent = { Icon(if (phase == 4) Icons.Home else Icons.Place, null) },
            trailingContent = { Icon(Icons.Next, null) },
            supportingContent = { Text(if (phase == 4) "Favourites and tiles per row" else "Weather and calendars") }) {
            Text("Settings")
        }
    }
}

/** A plan group's name in everyday words. */
fun groupTitle(phase: Int) = when (phase) {
    1 -> "Tracking and ads"
    2 -> "Apps nobody asked for"
    3 -> "The maker's extras"
    4 -> "A simpler home screen"
    5 -> "A clock screensaver"
    6 -> "Extras"
    else -> PHASES[phase] ?: "Other"
}
