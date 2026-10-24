@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pick a look for the TV's designs screen, or ask the AI for one; every design the TV took can be undone. */
@Composable
fun DesignsScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    val d = s.designs
    Page("Designs", s, subtitle = "How the TV's designs screen looks", onBack = { vm.go(Screen.TV) }) {
        item { TvScreenshot(d.shot) }
        if (d.takes.isEmpty()) item {
            Text("The first design you send also puts TVFreedom's designs screen on the TV. It is an app you can open from " +
                "the TV's app list; it doesn't change the TV's home screen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { GroupLabel("Ask the AI") }
        item { AskField(vm, hasDesign = d.takes.lastOrNull()?.builtIn == false) }
        if (d.ready.isNotEmpty()) {
            item { GroupLabel("Ready-made, with this TV's apps") }
            d.ready.forEachIndexed { i, design ->
                item(key = "ready-${design.name}") {
                    SegmentedListItem(onClick = { vm.sendDesign(design) }, shapes = seg(i, d.ready.size), colors = segColors(),
                        enabled = !s.busy, leadingContent = { Icon(Icons.Home, null) }, trailingContent = { Icon(Icons.Next, null) },
                        supportingContent = { Text(design.about) }) { Text(design.name) }
                }
            }
        }
        if (d.takes.isNotEmpty()) {
            item { GroupLabel("Sent to this TV") }
            val takes = d.takes.asReversed()
            takes.forEachIndexed { i, take ->
                item(key = "take-${d.takes.size - i}") {
                    SegmentedListItem(onClick = { vm.sendTake(take) }, shapes = seg(i, takes.size), colors = segColors(),
                        enabled = !s.busy && i > 0, leadingContent = { Icon(if (i == 0) Icons.Tv else Icons.History, null) },
                        supportingContent = { Text(if (i == 0) "On the TV now" else "Tap to send it again · ${shown(take.stamp)}") }) {
                        Text(take.name)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondAction("Undo", Icons.Back, enabled = !s.busy, modifier = Modifier.weight(1f), onClick = vm::undoDesign)
                    SecondAction("Built-in", Icons.Refresh, enabled = !s.busy && !d.takes.last().builtIn,
                        modifier = Modifier.weight(1f), onClick = vm::builtInDesign)
                }
            }
        }
    }
}

@Composable
private fun AskField(vm: TvFreedomViewModel, hasDesign: Boolean) {
    val s = vm.state
    var text by remember { mutableStateOf("") }
    if (!vm.aiWritesDesigns()) {
        Text("AI designs need Claude: in Termux, or with an API key.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { vm.go(Screen.SETTINGS) }) {
            Icon(Icons.Settings, null, Modifier.padding(end = 8.dp))
            Text("Pick Claude in Settings")
        }
        return
    }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), enabled = !s.busy, minLines = 2,
        label = { Text(if (hasDesign) "What to change" else "What you'd like") },
        placeholder = { Text(if (hasDesign) "Bigger clock, warmer colours" else "Calm and dark, Netflix and YouTube first") },
        shape = MaterialTheme.shapes.extraLarge,
        trailingIcon = {
            FilledIconButton(onClick = { vm.writeDesign(text) }, enabled = !s.busy && text.isNotBlank(),
                modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Next, "Make it") }
        })
}

/** "2026-10-24 14:05" from a take's yyyy-MM-dd-HHmmss stamp. */
private fun shown(stamp: String): String =
    if (stamp.length >= 15) "${stamp.take(10)} ${stamp.substring(11, 13)}:${stamp.substring(13, 15)}" else stamp
