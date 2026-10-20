@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gathin.tvgo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.core.PHASES
import com.gathin.tvgo.flow.HomeDraft
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.UNINSTALL
import com.gathin.tvgo.store.Settings

/** The plan, phase by phase, then a typed GO before anything touches the TV. */
@Composable
fun PlanScreen(vm: TvGoViewModel) {
    val s = vm.state
    val plan = s.plan
    var confirm by remember { mutableStateOf(false) }
    var showKept by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val count = plan?.remove?.count { it.phase in s.phases } ?: 0
    val draft = s.homeDraft
    val chosen = vm.chosenPhases()
    Page("Plan", s, onBack = { vm.go(Screen.TV) }, bottomBar = plan?.takeIf { it.remove.isNotEmpty() || draft != null || s.extrasDraft != null }?.let {
        {
            MainAction(if (chosen.isEmpty()) "Pick a phase" else "Start the debloat", Icons.Clean,
                enabled = !s.busy && chosen.isNotEmpty()) { confirm = true }
        }
    }) {
        if (plan == null) {
            if (!s.busy) details(s.log, showLog) { showLog = !showLog }
            return@Page
        }
        item { Summary(plan, count) }
        val steps = plainSteps(plan, draft, chosen, vm.brand.file.isFile, s.extrasDraft?.let { it to s.extras })
        item(key = "steps:label") { GroupLabel(if (steps.isEmpty()) "Nothing to do yet" else "What happens, step by step") }
        if (steps.isNotEmpty()) item(key = "steps:intro") {
            Text("One tap does it all, in this order. Each step is checked before the next starts, and the first one " +
                "that goes wrong stops the rest. Restore undoes any of it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        stepList(steps)
        userApps(plan)
        item(key = "details") { GroupLabel("The details, phase by phase") }
        plan.phases.forEach { phase -> phase(vm, plan, phase) }
        if (draft != null) listOf(4, 5).forEach { homePhase(vm, draft, it) }
        s.extrasDraft?.let { extrasPhase(vm, it) }
        if (plan.unanswered.isNotEmpty()) item {
            Text(if (vm.settings.ai == Settings.Ai.NONE) "${plan.unanswered.size} apps no list knows were kept (no AI chosen)."
                else "The AI had no answer for ${plan.unanswered.size} apps, so they were kept.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            TextButton(onClick = { showKept = !showKept }) { Text(if (showKept) "Hide kept apps" else "Show kept apps (${plan.kept.size})") }
        }
        if (showKept) {
            val kept = plan.kept.toSortedMap().toList()
            kept.forEachIndexed { i, (p, why) ->
                item(key = "kept:$p") {
                    SegmentedListItem(onClick = {}, shapes = seg(i, kept.size), colors = segColors(),
                        supportingContent = { Text(why) }) { Text(p, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    if (confirm && plan != null) TypedConfirm(
        title = "Start the debloat?",
        body = "On ${plan.info.model}, ${chosen.size} step${if (chosen.size == 1) "" else "s"}, one after another: " +
            plainSteps(plan, draft, chosen, vm.brand.file.isFile, s.extrasDraft?.let { it to s.extras }).joinToString("; ") { it.title.lowercase() } +
            ". The TV restarts after each, and TVFreedom checks it before going on. Keep this app open until it finishes. " +
            "You can undo it any time from Restore.",
        word = "GO",
        onConfirm = { confirm = false; vm.apply() },
        onDismiss = { confirm = false },
    )
}

@Composable
private fun Summary(plan: Plan, count: Int) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("$count", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Column {
                Text(if (plan.remove.isEmpty()) "Nothing to remove" else "apps to turn off or remove",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("${plan.kept.size} kept safe by the guard and your lists",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** One phase: a switch to include it, then its apps, as one connected group. */
private fun LazyListScope.phase(vm: TvGoViewModel, plan: Plan, phase: Int) {
    val rows = plan.remove.filter { it.phase == phase }
    val on = phase in vm.state.phases
    val n = rows.size + 1
    item(key = "label:$phase") { GroupLabel("Phase $phase · ${PHASES[phase] ?: "other"}") }
    item(key = "phase:$phase") {
        SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(phase) }, shapes = seg(0, n), colors = segColors(),
            enabled = !vm.state.busy,
            supportingContent = { Text("${rows.size} apps") },
            trailingContent = { Switch(on, null) }) { Text(if (on) "Included" else "Skipped") }
    }
    rows.forEachIndexed { i, r ->
        item(key = "row:$phase:${r.pkg}") {
            SegmentedListItem(onClick = {}, shapes = seg(i + 1, n), colors = segColors(), enabled = on,
                supportingContent = { Text("${if (r.action == UNINSTALL) "Uninstall" else "Turn off"} · ${r.reason}") },
            ) { Text(r.pkg, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Phase 4 or 5: a switch, then what it does on this TV; phase 4 also opens the home screen settings. */
private fun LazyListScope.homePhase(vm: TvGoViewModel, draft: HomeDraft, phase: Int) {
    val on = phase in vm.state.phases
    val due = if (phase == 4) draft.launcherDue else draft.screensaverDue
    val lines = homeLines(draft, phase)
    val n = lines.size + 2
    item(key = "label:$phase") { GroupLabel("Phase $phase · ${if (phase == 4) "new home screen" else "clock screensaver"}") }
    item(key = "phase:$phase") {
        SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(phase) }, shapes = seg(0, n), colors = segColors(),
            enabled = !vm.state.busy,
            supportingContent = { Text(if (due) "Not done on this TV yet" else "Already done; include it to send new settings") },
            trailingContent = { Switch(on, null) }) { Text(if (on) "Included" else "Skipped") }
    }
    item(key = "home:$phase:preview") {
        // What the TV will look like, before anything is sent.
        val logo = rememberLogo(vm)
        val c = vm.state.homeConfig ?: draft.config
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (phase == 4) "The home screen will look like this" else "The screensaver will look like this",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (phase == 4) HomePreview(logo, vm.brand.color, c.favourites, c.columns)
            else DreamPreview(logo, c.place)
        }
    }
    lines.forEachIndexed { i, line ->
        item(key = "home:$phase:$i") {
            SegmentedListItem(onClick = {}, shapes = seg(i + 1, n), colors = segColors(), enabled = on) {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
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

/** A run in progress (apply, restore, restart) and how it ended. */
@Composable
fun RunScreen(vm: TvGoViewModel) {
    val s = vm.state
    var showLog by rememberSaveable { mutableStateOf(false) }
    val outcome = s.outcome
    Page(if (s.busy) "Working" else outcome?.headline ?: "Done", s, onBack = { vm.go(Screen.TV) }.takeIf { !s.busy }) {
        if (s.busy) item {
            Text("Keep this app open until it finishes. It can take a few minutes.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.runSteps.isNotEmpty()) {
            val failed = s.runPhase.takeIf { !s.busy && outcome?.ok == false }
            stepList(s.runSteps, now = s.runPhase.takeIf { s.busy }, done = s.runDone, failed = failed)
        }
        if (outcome != null && !s.busy) {
            item { OutcomeView(outcome.ok, outcome.headline, outcome.body) }
            outcome.ramAfter?.let { after -> item { RamCard(outcome.ramBefore, after) } }
            outcome.screen?.let { file -> item { TvScreenshot(file) } }
            item { MainAction("Back to the TV", Icons.Tv) { vm.go(Screen.TV) } }
            if (!outcome.ok) item { SecondAction("Restore", Icons.History, modifier = Modifier.fillMaxWidth(), onClick = vm::openRestore) }
        }
        details(s.log, showLog) { showLog = !showLog }
    }
}
