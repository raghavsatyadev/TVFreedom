@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.design.Design

/** Pick a look for the TV's home screen, drawn with this TV's apps, or ask the AI for one; then send it. */
@Composable
fun DesignsScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    val d = s.designs
    Page("Designs", s, subtitle = "How the TV's home screen looks", onBack = { vm.go(Screen.TV) }) {
        if (d.shot != null) item { TvScreenshot(d.shot) }
        item { GroupLabel("Ask the AI") }
        item { AskField(vm) }
        item { GroupLabel("Pick a look") }
        d.choices.forEach { design ->
            item(key = "look-${design.name}-${design === d.ai}") {
                LookCard(design, d, picked = design == d.picked, enabled = !s.busy, ai = design === d.ai) { vm.pickDesign(design) }
            }
        }
        item {
            MainAction(d.picked?.let { "Send \"${it.name}\" to the TV" } ?: "Pick a look first", Icons.Tv,
                enabled = !s.busy && d.picked != null, onClick = vm::sendDesign)
        }
        if (d.takes.size > 1) item {
            SecondAction("Undo: back to \"${d.takes[d.takes.size - 2].name}\"", Icons.Back, enabled = !s.busy,
                modifier = Modifier.fillMaxWidth(), onClick = vm::undoDesign)
        }
    }
}

@Composable
private fun LookCard(design: Design, d: DesignsUi, picked: Boolean, enabled: Boolean, ai: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        border = if (picked) BorderStroke(3.dp, c.primary) else null,
        colors = CardDefaults.cardColors(containerColor = if (picked) c.primaryContainer else c.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LookPreview(design.look, d.apps, d.columns)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ai) "${design.name} (AI)" else design.name, style = MaterialTheme.typography.titleMedium)
                    Text(design.about, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
                if (picked) Icon(Icons.Done, "Picked", tint = c.primary)
            }
        }
    }
}

@Composable
private fun AskField(vm: TvFreedomViewModel) {
    val s = vm.state
    var text by remember { mutableStateOf("") }
    if (!vm.aiWritesDesigns()) {
        Text("AI looks need Claude: in Termux, or with an API key.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { vm.go(Screen.SETTINGS) }) {
            Icon(Icons.Settings, null, Modifier.padding(end = 8.dp))
            Text("Pick Claude in Settings")
        }
        return
    }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), enabled = !s.busy, minLines = 2,
        label = { Text("What you'd like") },
        placeholder = { Text("Calm and dark, big tiles") },
        supportingText = { Text("The AI's look shows below first. Nothing changes on the TV until you send it.") },
        shape = MaterialTheme.shapes.extraLarge,
        trailingIcon = {
            FilledIconButton(onClick = { vm.writeDesign(text) }, enabled = !s.busy && text.isNotBlank(),
                modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Next, "Make it") }
        })
}
