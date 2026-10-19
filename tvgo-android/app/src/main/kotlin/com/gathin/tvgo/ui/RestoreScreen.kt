package com.gathin.tvgo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.store.RestoreOption

/** Undo a run, a repo phase list, or everything back to a backup. A typed RESTORE confirms. */
@Composable
fun RestoreScreen(vm: TvGoViewModel) {
    val s = vm.state
    var confirm by remember { mutableStateOf(false) }
    Page("Restore", s) {
        if (s.restoreOptions.isEmpty()) Text("No runs, lists or backups for this TV yet.")
        Section("What to undo") {
            s.restoreOptions.forEach { o ->
                HorizontalDivider()
                Text(o.label, Modifier.fillMaxWidth().clickable(enabled = !s.busy) { vm.previewRestore(o) }.padding(vertical = 8.dp),
                    fontWeight = if (o == s.restoreChoice) FontWeight.Bold else FontWeight.Normal)
            }
        }
        val choice = s.restoreChoice
        if (choice != null) {
            Section("This will") {
                Text(s.restorePreview.ifEmpty { "nothing to undo" }, style = MaterialTheme.typography.bodySmall)
            }
            val nothing = choice.kind == RestoreOption.Kind.BACKUP && s.restorePreview.startsWith("enable 0:") &&
                "reinstall 0:" in s.restorePreview
            Button(onClick = { confirm = true }, enabled = !s.busy && !nothing, modifier = Modifier.fillMaxWidth()) {
                Text("Restore")
            }
        }
        OutlinedButton(onClick = { vm.go(Screen.TV) }, enabled = !s.busy) { Text("Back") }
        if (confirm && choice != null) TypedConfirm(
            title = "Restore?",
            body = "${choice.label} on ${s.info?.model} (pm enable / install-existing), then a 30 s wait.",
            word = "RESTORE",
            onConfirm = { confirm = false; vm.restore() },
            onDismiss = { confirm = false },
        )
    }
}
