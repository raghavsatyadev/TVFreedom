@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gathin.tvgo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.store.RestoreOption

/** Undo a run, a repo phase list, or everything back to a backup. A typed RESTORE confirms. */
@Composable
fun RestoreScreen(vm: TvGoViewModel) {
    val s = vm.state
    var confirm by remember { mutableStateOf(false) }
    val choice = s.restoreChoice
    val nothing = choice == null || s.restorePreview.isEmpty()
    Page("Restore", s, onBack = { vm.go(Screen.TV) }, bottomBar = choice?.let {
        { MainAction(if (nothing) "Nothing to undo" else "Restore", Icons.History, enabled = !s.busy && !nothing) { confirm = true } }
    }) {
        if (s.restoreOptions.isEmpty()) item {
            Text("No runs, lists or backups for this TV yet.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val groups = s.restoreOptions.groupBy { it.kind }
        listOf(RestoreOption.Kind.LIST to "Undo a run or a list", RestoreOption.Kind.BACKUP to "Go back to a backup").forEach { (kind, label) ->
            val options = groups[kind].orEmpty()
            if (options.isEmpty()) return@forEach
            item(key = "label:$kind") { GroupLabel(label) }
            item(key = "opts:$kind") { RadioGroup(options, choice, { it.label }, enabled = !s.busy, onPick = vm::previewRestore) }
        }
        if (choice != null && s.restorePreview.isNotEmpty()) item {
            Card(shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(20.dp)) {
                    Text("This will", style = MaterialTheme.typography.titleMedium)
                    Text(s.restorePreview, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (confirm && choice != null) TypedConfirm(
        title = "Restore?",
        body = "${choice.label} on ${s.info?.model}. The apps are turned back on or reinstalled, then the TV gets 30 s to save.",
        word = "RESTORE",
        onConfirm = { confirm = false; vm.restore() },
        onDismiss = { confirm = false },
    )
}
