package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.flow.Change

/** "By run" / "By change" at the top of Restore. */
@Composable
fun RestoreTabs(tab: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("By run", "By change").forEachIndexed { i, label ->
            SegmentedButton(selected = tab == i, onClick = { onPick(i) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
        }
    }
}

/** Every single change still in effect, a card per run, newest first; tick the ones to undo. */
fun LazyListScope.changeTab(s: UiState, onPick: (String) -> Unit) {
    val groups = s.changes ?: return
    if (groups.isEmpty()) item(key = "nochanges") {
        Text("Nothing TVFreedom changed is still in effect on this TV.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    groups.forEach { g ->
        val key = g.changes.first().id
        item(key = "glabel:$key") { GroupLabel(g.title) }
        item(key = "group:$key") { CheckGroup(g.changes, s.picked, enabled = !s.busy, onPick = onPick) }
    }
}

@Composable
private fun CheckGroup(changes: List<Change>, picked: Set<String>, enabled: Boolean, onPick: (String) -> Unit) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column {
            changes.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .toggleable(c.id in picked, enabled, Role.Checkbox) { onPick(c.id) }
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(c.id in picked, null, enabled = enabled, modifier = Modifier.padding(12.dp))
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                        c.detail.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
