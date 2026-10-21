package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One Termux setup step: how many are left, one sentence, one button. */
@Composable
fun StepCard(step: TermuxStep, title: String, body: String, action: String,
             icon: ImageVector, onClick: () -> Unit) {
    val left = TermuxStep.READY.ordinal - step.ordinal
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (left == 1) "Last step" else "Up to $left steps left", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.padding(top = 4.dp)) { MainAction(action, icon, onClick = onClick) }
        }
    }
}

/** One line under the AI choice, the same for every AI: a tick and "… is ready", or what is happening now. */
@Composable
fun AiStatusLine(text: String, ready: Boolean = false) {
    Row(Modifier.padding(start = 4.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ready) Icon(Icons.Done, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
