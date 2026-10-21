@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.store.Settings

private val TV_STEPS = listOf(
    "Turn on Developer options" to
        "Google TV and most Android TVs: Settings > System > About > select \"Android TV OS build\" 7 times. " +
        "Older Android TV, Sony, Nvidia Shield, Philips: Settings > Device Preferences > About > \"Build\" 7 times. " +
        "Fire TV: Settings > My Fire TV > About > select the device name 7 times.",
    "Turn on debugging over the network" to
        "In Developer options. Android 11 and newer: \"Wireless debugging\" ON. " +
        "Android 9/10, Nvidia Shield, many Xiaomi and TCL TVs: \"USB debugging\" and \"Network debugging\" ON. " +
        "Fire TV: \"ADB debugging\" ON.",
    "Same Wi-Fi" to "Keep this phone and the TV on the same Wi-Fi.",
    "Allow debugging" to "When the TV asks \"Allow debugging?\", tick \"Always allow\" and press OK.",
)

/** First run: the TV settings, the phone permission if it is still missing, and which AI plans. */
@Composable
fun SetupScreen(vm: TvFreedomViewModel) {
    val firstRun = !vm.settings.onboarded
    Page("Set up your TV", vm.state, onBack = if (firstRun) null else ({ vm.go(Screen.SETTINGS) }),
        bottomBar = { MainAction(if (firstRun) "Find my TV" else "Done", Icons.Tv, onClick = vm::finishSetup) }) {
        item { LocalNetworkPermission() }
        // The AI comes first, so the TV's first plan is made by the model the user wants.
        if (firstRun) aiSettings(vm)
        item { GroupLabel("On the TV") }
        TV_STEPS.forEachIndexed { i, (title, body) ->
            item(key = "step:$i") {
                SegmentedListItem(onClick = {}, shapes = seg(i, TV_STEPS.size), colors = segColors(),
                    leadingContent = { StepNumber(i + 1) }, supportingContent = { Text(body) }) { Text(title) }
            }
        }
    }
}

@Composable
private fun StepNumber(n: Int) {
    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Shown only while Android 17's local network permission is missing; once granted there is nothing to say. */
@Composable
private fun LocalNetworkPermission() {
    var granted by remember { mutableStateOf(false) }
    val permission = rememberPermission(Permission.LOCAL_NETWORK) { granted = it }
    if (granted || permission.granted) return
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text("TVFreedom needs to reach devices on your Wi-Fi to talk to the TV.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Box(Modifier.padding(top = 12.dp)) {
                SecondAction("Allow local network access", Icons.Link) { permission.ask() }
            }
        }
    }
}

/** Which AI plans the packages the guard and the lists leave open, and how user apps are removed. */
fun LazyListScope.aiSettings(vm: TvFreedomViewModel) {
    item(key = "ai:label") { GroupLabel("AI that suggests what to remove") }
    item(key = "ai:about") {
        Text("The safety guard and your lists always come first. The AI only decides the rest, and you confirm every change.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
    item(key = "ai:pick") { AiPicker(vm) }
    item(key = "ai:uninstall") { UninstallSwitch(vm.settings) }
    item(key = "updates") { UpdatesSwitch(vm.settings) }
}

@Composable
private fun UpdatesSwitch(s: Settings) {
    var stop by remember { mutableStateOf(s.stopUpdates) }
    SegmentedListItem(onClick = { stop = !stop; s.stopUpdates = stop }, shapes = seg(0, 1), colors = segColors(),
        supportingContent = { Text("Turns off the TV's update service where this TV's list names it. Restore turns it back on.") },
        trailingContent = { Switch(stop, null) }) { Text("Stop firmware updates") }
}

@Composable
private fun AiPicker(vm: TvFreedomViewModel) {
    val s = vm.settings
    var ai by remember { mutableStateOf(s.ai) }
    var key by remember { mutableStateOf(s.claudeKey) }
    var model by remember { mutableStateOf(s.claudeModel) }
    Column {
        RadioGroup(Settings.Ai.entries, ai, { it.label }, { it.hint }) { ai = it; vm.pickAi(it) }
        if (ai == Settings.Ai.TERMUX) {
            TermuxSetup(vm)
            TermuxModelPicker(s)
        }
        if (ai == Settings.Ai.CLAUDE) {
            OutlinedTextField(key, { key = it; s.claudeKey = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Claude API key") }, singleLine = true, shape = MaterialTheme.shapes.large,
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(model, { model = it; s.claudeModel = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Model") }, singleLine = true, shape = MaterialTheme.shapes.large)
        }
        Column(Modifier.padding(top = 12.dp)) {
            if (ai == Settings.Ai.NANO) NanoStatus(vm)
            if (ai == Settings.Ai.CLAUDE && key.isNotBlank()) AiStatusLine("Claude API key saved", ready = true)
        }
    }
}

/** Gemini Nano is checked as soon as it's picked (a one-time download if the phone needs it); a button only on failure. */
@Composable
private fun NanoStatus(vm: TvFreedomViewModel) {
    val status = vm.state.aiStatus
    LaunchedEffect(Unit) { if (status.isEmpty()) vm.checkAi() }
    when {
        status.isEmpty() -> AiStatusLine("Checking Gemini Nano…")
        status == AI_READY -> AiStatusLine("Gemini Nano is ready", ready = true)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiStatusLine(status.replaceFirstChar { it.uppercase() })
            SecondAction("Try again", Icons.Refresh, onClick = vm::checkAi)
        }
    }
}

@Composable
private fun TermuxModelPicker(s: Settings) {
    var model by remember { mutableStateOf(s.termuxModel) }
    Column(Modifier.padding(top = 12.dp)) {
        GroupLabel("Claude model")
        Box(Modifier.padding(top = 8.dp)) {
            RadioGroup(Settings.TermuxModel.entries, model, { it.label }, { it.hint }) { model = it; s.termuxModel = it }
        }
    }
}

@Composable
private fun UninstallSwitch(s: Settings) {
    var uninstall by remember { mutableStateOf(s.uninstallUserApps) }
    SegmentedListItem(onClick = { uninstall = !uninstall; s.uninstallUserApps = uninstall }, shapes = seg(0, 1), colors = segColors(),
        supportingContent = { Text("Instead of turning them off. Restoring may need the Play Store.") },
        trailingContent = { Switch(uninstall, null) }) { Text("Uninstall user apps") }
}

@Composable
fun SettingsScreen(vm: TvFreedomViewModel) {
    Page("Settings", vm.state, onBack = { vm.go(if (vm.state.info != null) Screen.TV else Screen.CONNECT) }) {
        aiSettings(vm)
        item { GroupLabel("Company branding") }
        item { BrandCard(vm) }
        item { GroupLabel("Help") }
        item {
            SegmentedListItem(onClick = { vm.go(Screen.SETUP) }, shapes = seg(0, 1), colors = segColors(),
                leadingContent = { Icon(Icons.Tv, null) }, trailingContent = { Icon(Icons.Next, null) },
                supportingContent = { Text("Developer options and network debugging") }) { Text("TV setup steps") }
        }
    }
}
