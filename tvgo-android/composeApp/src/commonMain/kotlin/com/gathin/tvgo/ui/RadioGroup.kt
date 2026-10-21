package com.gathin.tvgo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Pick one of [options]: one raised card with a row per option, the radio alone marks the choice,
 * so the card keeps one shape and colour whichever row is picked.
 */
@Composable
fun <T> RadioGroup(options: List<T>, selected: T?, label: (T) -> String, hint: (T) -> String = { "" },
                   enabled: Boolean = true, onPick: (T) -> Unit) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .selectable(o == selected, enabled, Role.RadioButton) { onPick(o) }
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(o == selected, null, enabled = enabled, modifier = Modifier.padding(12.dp))
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                        hint(o).takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
