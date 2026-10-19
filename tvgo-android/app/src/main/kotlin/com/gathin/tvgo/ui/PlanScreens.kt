package com.gathin.tvgo.ui

import com.gathin.tvgo.store.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.gathin.tvgo.core.PHASES

/** The plan, phase by phase, then a typed GO before anything touches the TV. */
@Composable
fun PlanScreen(vm: TvGoViewModel) {
    val s = vm.state
    val plan = s.plan
    var confirm by remember { mutableStateOf(false) }
    var showKept by remember { mutableStateOf(false) }
    Page("Plan", s) {
        if (plan == null) {
            LogView(s.log)
            if (!s.busy) OutlinedButton(onClick = { vm.go(Screen.TV) }) { Text("Back") }
            return@Page
        }
        Text("${plan.info.brand} ${plan.info.model}: ${plan.remove.size} to remove, ${plan.kept.size} kept, " +
            "${plan.alreadyOff.size} already off.")
        if (plan.remove.isEmpty()) Text("Nothing to remove.")
        plan.phases.forEach { phase ->
            val rows = plan.remove.filter { it.phase == phase }
            Section("Phase $phase: ${PHASES[phase] ?: "other"} (${rows.size})") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(phase in s.phases, { vm.togglePhase(phase) })
                    Text(if (phase in s.phases) "Apply this phase" else "Skip this phase")
                }
                rows.forEach { r ->
                    Text("${r.action} ${r.pkg}", fontWeight = FontWeight.Medium)
                    Text("${r.reason} [${r.source}]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (plan.conflicts.isNotEmpty()) Section("Kept by the safety guard although an earlier list removed them") {
            plan.conflicts.forEach { Text("${it.pkg} (${it.keptAs}; earlier: ${it.earlier})", style = MaterialTheme.typography.bodySmall) }
        }
        if (plan.unanswered.isNotEmpty()) Text(
            if (vm.settings.ai == Settings.Ai.NONE) "No AI chosen: ${plan.unanswered.size} packages no list covers were kept."
            else "The AI gave no answer for ${plan.unanswered.size} packages: kept.")
        TextButton(onClick = { showKept = !showKept }) { Text(if (showKept) "Hide kept packages" else "Show kept packages") }
        if (showKept) Section("Kept") {
            plan.kept.toSortedMap().forEach { (p, why) -> Text("$p: $why", style = MaterialTheme.typography.bodySmall) }
        }
        val count = plan.remove.count { it.phase in s.phases }
        Button(onClick = { confirm = true }, enabled = !s.busy && count > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Apply $count changes")
        }
        OutlinedButton(onClick = { vm.go(Screen.TV) }, enabled = !s.busy) { Text("Back (nothing changed)") }
        if (confirm) TypedConfirm(
            title = "Change $count packages?",
            body = "On ${plan.info.model} at ${plan.info.host}: phases ${s.phases.sorted().joinToString(" ")}. " +
                "Only pm disable-user / uninstall --user 0, then a 30 s wait, a reboot and a home screen check. " +
                "Undo any time from Restore.",
            word = "GO",
            onConfirm = { confirm = false; vm.apply() },
            onDismiss = { confirm = false },
        )
    }
}

/** A run in progress (apply, restore, reboot) and its result. */
@Composable
fun RunScreen(vm: TvGoViewModel) {
    val s = vm.state
    Page("Working on the TV", s) {
        if (s.busy) Text("Keep this app open until it finishes.")
        LogView(s.log)
        if (s.result.isNotEmpty()) Section("Result") { Text(s.result) }
        if (!s.busy) Buttons {
            Button(onClick = { vm.go(Screen.TV) }) { Text("Back to the TV") }
            OutlinedButton(onClick = vm::openRestore) { Text("Restore") }
        }
    }
}
