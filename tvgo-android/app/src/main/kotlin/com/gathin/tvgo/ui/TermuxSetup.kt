package com.gathin.tvgo.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gathin.tvgo.ai.TermuxClaude
import com.gathin.tvgo.ai.TermuxClaude.Step

/**
 * Setting up Claude in Termux, one step at a time. Each time TVFreedom comes back to the front it checks Termux again,
 * so steps already done never show and the user never has to tap "check".
 */
@Composable
fun TermuxSetup(vm: TvGoViewModel) {
    val ctx = LocalContext.current
    val termux = TermuxClaude(ctx)
    LifecycleResumeEffect(Unit) {
        vm.probeTermux()
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.probeTermux() }
    val clipboard = LocalClipboardManager.current
    val step = vm.state.termuxStep
    Column(Modifier.padding(top = 12.dp)) {
        when (step) {
            null -> AiStatusLine("Checking Termux…")
            Step.READY -> AiStatusLine("Claude is ready", ready = true)
            Step.INSTALL_TERMUX -> StepCard(step, "Install Termux",
                "Get Termux from F-Droid. The Play Store version is too old to run Claude.", "Get Termux", Icons.Link) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TermuxClaude.GET_TERMUX)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Step.PERMISSION -> StepCard(step, "Let TVFreedom use Termux",
                "TVFreedom asks Termux to run Claude for it. Android asks you first.", "Allow", Icons.Shield) {
                permission.launch(TermuxClaude.PERMISSION)
            }
            Step.OPEN_TERMUX -> StepCard(step, "Open Termux once",
                "Termux sets itself up the first time it opens. Wait for its prompt, then come back.", "Open Termux", Icons.Next) {
                termux.openTermux()
            }
            Step.ALLOW_APPS -> StepCard(step, "Let Termux take commands from TVFreedom",
                "Only you can switch this on. Tap below, then in Termux long-press, Paste, press Enter, and come back.",
                "Copy and open Termux", Icons.Next) {
                clipboard.setText(AnnotatedString(TermuxClaude.ALLOW_LINE))
                termux.openTermux()
            }
            Step.INSTALL_CLAUDE -> StepCard(step, "Install Claude Code",
                "TVFreedom installs it in Termux for you, in about 5 to 10 minutes. When it asks, log in to your Claude account, " +
                    "type /exit and come back.", "Install in Termux", Icons.Next) { termux.startInstall() }
            Step.LOGIN -> StepCard(step, "Log in to Claude",
                "Log in with your Claude account in Termux, then type /exit and come back.", "Log in in Termux", Icons.Next) {
                termux.startLogin()
            }
        }
    }
}

@Composable
private fun StepCard(step: Step, title: String, body: String, action: String,
                     icon: ImageVector, onClick: () -> Unit) {
    val left = Step.READY.ordinal - step.ordinal
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
