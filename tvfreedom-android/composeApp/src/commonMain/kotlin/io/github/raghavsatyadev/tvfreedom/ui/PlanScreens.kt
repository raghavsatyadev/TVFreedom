@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.UNINSTALL

/** The plan in short: how many things go, in a few groups, and one button. Manage holds the rest. */
@Composable
fun PlanScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    val plan = s.plan
    var confirm by remember { mutableStateOf(false) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var showKept by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val chosen = vm.chosenPhases()
    val count = plan?.remove?.count { it.phase in s.phases } ?: 0
    Page(if (manage) "Manage" else "Plan", s, onBack = { if (manage) manage = false else vm.go(Screen.TV) },
        bottomBar = plan?.takeIf { it.remove.isNotEmpty() || s.homeDraft != null || s.extrasDraft != null }?.let {
            { MainAction("Clean", Icons.Clean, enabled = !s.busy && chosen.isNotEmpty()) { confirm = true } }
        }) {
        if (plan == null) {
            if (!s.busy) details(s.log, showLog) { showLog = !showLog }
            return@Page
        }
        if (manage) {
            planDetails(vm, plan, showKept, { showKept = !showKept }, showLog, { showLog = !showLog })
            return@Page
        }
        item(key = "count") { Headline(plan, count) }
        plan.phases.forEach { group(vm, plan, it) }
        moreRows(vm)
        item(key = "manage") {
            TextButton(onClick = { manage = true }, Modifier.fillMaxWidth()) { Text("Manage") }
        }
    }
    if (confirm && plan != null) TypedConfirm(
        title = "Clean ${plan.info.model}?",
        body = "Keep this app open until it's done. Restore undoes it.",
        word = "GO",
        onConfirm = { confirm = false; vm.apply() },
        onDismiss = { confirm = false },
    )
}

@Composable
private fun Headline(plan: Plan, count: Int) {
    // Apps someone installed are named here: a tap must never take one off unseen.
    val theirs = plan.remove.count { it.pkg in plan.userApps }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$count", style = MaterialTheme.typography.displayLarge)
        Text(if (count == 1) "thing to switch off" else "things to switch off",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (theirs > 0) Text("$theirs of them installed by someone", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error)
    }
}

/** One group: its name, how many, a switch; tap to see its apps. */
private fun LazyListScope.group(vm: TvFreedomViewModel, plan: Plan, phase: Int) {
    val rows = plan.remove.filter { it.phase == phase }
    item(key = "group:$phase") {
        var open by rememberSaveable { mutableStateOf(false) }
        val on = phase in vm.state.phases
        Column {
            SegmentedListItem(onClick = { open = !open }, shapes = seg(0, if (open) rows.size + 1 else 1), colors = segColors(),
                enabled = !vm.state.busy,
                supportingContent = { Text("${rows.size}") },
                trailingContent = { Switch(on, { vm.togglePhase(phase) }, enabled = !vm.state.busy) }) { Text(groupTitle(phase)) }
            if (open) rows.forEachIndexed { i, r ->
                SegmentedListItem(onClick = {}, shapes = seg(i + 1, rows.size + 1), colors = segColors(), enabled = on,
                    supportingContent = if (r.action == UNINSTALL || r.pkg in plan.userApps) ({
                        Text(listOfNotNull("Removed".takeIf { r.action == UNINSTALL },
                            "Someone installed it".takeIf { r.pkg in plan.userApps }).joinToString(" · "))
                    }) else null,
                ) { Text(appName(r.pkg), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** The home screen, screensaver and extras: not apps to switch off, so each gets its own row. */
private fun LazyListScope.moreRows(vm: TvFreedomViewModel) {
    val s = vm.state
    val rows = listOfNotNull(4.takeIf { s.homeDraft != null }, 5.takeIf { s.homeDraft != null }, 6.takeIf { s.extrasDraft != null })
    if (rows.isEmpty()) return
    item(key = "more:label") { GroupLabel("Also") }
    rows.forEachIndexed { i, p ->
        item(key = "more:$p") {
            val on = p in s.phases
            SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(p) }, shapes = seg(i, rows.size), colors = segColors(),
                enabled = !s.busy,
                leadingContent = { Icon(when (p) { 4 -> Icons.Home; 5 -> Icons.Place; else -> Icons.Settings }, null) },
                trailingContent = { Switch(on, null) }) { Text(groupTitle(p)) }
        }
    }
}

/** A run in progress (apply, restore, restart) and how it ended. */
@Composable
fun RunScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    var showLog by rememberSaveable { mutableStateOf(false) }
    val outcome = s.outcome
    Page(if (s.busy) "Working" else outcome?.headline ?: "Done", s, onBack = { vm.go(Screen.TV) }.takeIf { !s.busy }) {
        if (s.busy) item {
            Text("Keep this app open.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.runSteps.isNotEmpty()) {
            val failed = s.runPhase.takeIf { !s.busy && outcome?.ok == false }
            stepList(s.runSteps, now = s.runPhase.takeIf { s.busy }, done = s.runDone, failed = failed)
        }
        if (outcome != null && !s.busy) {
            item { OutcomeView(outcome.ok, outcome.headline, outcome.body) }
            outcome.ramAfter?.let { after -> item { RamCard(outcome.ramBefore, after) } }
            outcome.screen?.let { file -> item { TvScreenshot(remember(file) { vm.readBytes(file) }) } }
            item { MainAction("Done", Icons.Done) { vm.go(Screen.TV) } }
            if (!outcome.ok) item { SecondAction("Restore", Icons.History, modifier = Modifier.fillMaxWidth(), onClick = vm::openRestore) }
        }
        details(s.log, showLog) { showLog = !showLog }
    }
}
