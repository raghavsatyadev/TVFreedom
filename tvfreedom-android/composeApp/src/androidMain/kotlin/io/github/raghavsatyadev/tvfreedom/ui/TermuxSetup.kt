package io.github.raghavsatyadev.tvfreedom.ui

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.raghavsatyadev.tvfreedom.ai.TermuxClaude
import io.github.raghavsatyadev.tvfreedom.ai.TermuxInstaller
import kotlinx.coroutines.launch
import io.github.raghavsatyadev.tvfreedom.ui.TermuxStep as Step

/**
 * Setting up Claude in Termux, one step at a time. Each time TVFreedom comes back to the front it checks Termux again,
 * so steps already done never show and the user never has to tap "check".
 */
@Composable
actual fun TermuxSetup(vm: TvFreedomViewModel) {
    val ctx = LocalContext.current
    val termux = TermuxClaude(ctx)
    val installer = remember { TermuxInstaller(ctx) }
    val scope = rememberCoroutineScope()
    var get by remember { mutableStateOf<Get>(Get.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    fun getTermux() {
        if (!installer.canInstall()) { get = Get.Settings; ctx.startActivity(installer.unknownSources()); return }
        get = Get.Downloading
        progress = 0f
        scope.launch {
            get = when (val r = installer.install { progress = it }) {
                TermuxInstaller.Result.Installed -> Get.Idle.also { vm.probeTermux() }
                TermuxInstaller.Result.NeedsPermission -> Get.Settings.also { ctx.startActivity(installer.unknownSources()) }
                is TermuxInstaller.Result.Failed -> Get.Failed(r.why)
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.probeTermux()
        // Back from "Install unknown apps": carry on by itself once it is on.
        if (get == Get.Settings && installer.canInstall()) getTermux()
        onPauseOrDispose {}
    }
    // Android shows no dialog once the user said no (twice on Android 11+), so a "no" leads to App info instead.
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        vm.probeTermux()
    }
    val clipboard = LocalClipboardManager.current
    val step = vm.state.termuxStep
    Column(Modifier.padding(top = 12.dp)) {
        when (step) {
            null -> AiStatusLine("Checking Termux…")
            Step.READY -> AiStatusLine("Claude is ready", ready = true)
            Step.INSTALL_TERMUX -> when (val g = get) {
                Get.Idle -> StepCard(step, "Install Termux", "Claude runs inside a free app called Termux. TVFreedom gets it for you.",
                    "Install Termux", Icons.Next, note = PLAY_PROTECT) { getTermux() }
                Get.Downloading -> StepCard(step, "Getting Termux", "Downloading, about 110 MB. Then tap Install when Android asks.",
                    "", Icons.Next, progress = progress, note = PLAY_PROTECT) {}
                Get.Settings -> StepCard(step, "Allow TVFreedom to install Termux",
                    "Turn on the switch for TVFreedom, then come back.", "Open settings", Icons.Settings) {
                    ctx.startActivity(installer.unknownSources())
                }
                is Get.Failed -> StepCard(step, "Termux did not install", g.why, "Try again", Icons.Refresh,
                    second = "Get it from F-Droid instead" to { openFdroid(ctx) }) { getTermux() }
            }
            // A Termux from elsewhere (like the Play Store one) may take no commands from other apps; F-Droid's does.
            Step.PERMISSION -> if (!TermuxClaude.offersPermission(ctx)) StepCard(step, "Swap Termux for the F-Droid one",
                "This Termux doesn't let other apps run commands in it. Uninstall it, come back, and TVFreedom installs " +
                    "the F-Droid Termux.", "Uninstall Termux", Icons.Settings,
                note = "On Termux's page, tap Uninstall. Anything you keep in Termux is deleted.") {
                ctx.startActivity(TermuxClaude.termuxSettings())
            } else if (refused) StepCard(step, "Let TVFreedom use Termux",
                "Android won't ask again. On TVFreedom's page, tap Permissions, then Additional permissions, " +
                    "then Run commands in Termux environment, and allow it. Then come back.", "Open settings", Icons.Settings,
                second = "Ask again" to { permission.launch(TermuxClaude.PERMISSION) }) {
                ctx.startActivity(TermuxClaude.appSettings(ctx))
            } else StepCard(step, "Let TVFreedom use Termux",
                "TVFreedom asks Termux to run Claude for it. Android asks you first.", "Allow", Icons.Shield) {
                permission.launch(TermuxClaude.PERMISSION)
            }
            // A new Termux can't answer until its first open sets it up; the paste step opens it anyway, so it is one step.
            Step.OPEN_TERMUX, Step.ALLOW_APPS -> StepCard(step, "Set up Claude in Termux",
                "Tap below. In Termux, wait for the \$ sign, then long-press, tap Paste and press Enter.",
                "Copy and open Termux", Icons.Next,
                note = "If Termux asks about notifications, tap Allow. Setup takes a few minutes. Log in when Claude asks, then come back to TVFreedom.") {
                clipboard.setText(AnnotatedString(termux.pasteLine()))
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

/** Where getting Termux from F-Droid is. */
private sealed interface Get {
    data object Idle : Get
    data object Downloading : Get
    data object Settings : Get
    data class Failed(val why: String) : Get
}

private const val PLAY_PROTECT = "If Play Protect asks, tap More details, then Install anyway."

private fun openFdroid(ctx: Context) =
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TermuxClaude.GET_TERMUX)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
