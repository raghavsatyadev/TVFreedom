package com.gathin.tvgo.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import com.gathin.tvgo.store.Settings

private val TV_STEPS = listOf(
    "Turn on Developer options" to
        "Google TV and most Android TVs: Settings > System > About > select \"Android TV OS build\" 7 times. " +
        "Older Android TV, Sony, Nvidia Shield, Philips: Settings > Device Preferences > About > \"Build\" 7 times. " +
        "Fire TV: Settings > My Fire TV > About > select the device name 7 times.",
    "Turn on debugging over the network" to
        "In Developer options. Android 11 and newer: \"Wireless debugging\" ON, then \"Pair device with pairing code\": " +
        "the TV shows an ip:port and a 6-digit code for the Pair box on the next screen. " +
        "Android 9/10, Nvidia Shield, many Xiaomi and TCL TVs: \"USB debugging\" and \"Network debugging\" ON " +
        "(the TV listens on port 5555). Fire TV: \"ADB debugging\" ON.",
    "Same Wi-Fi" to "Keep this phone and the TV on the same Wi-Fi. The TV's IP is under Settings > Network & Internet.",
    "Allow debugging" to "When the TV asks \"Allow debugging?\", tick \"Always allow\" and press OK.",
)

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** First run: the phone permission, the TV settings, and which AI plans. */
@Composable
fun SetupScreen(vm: TvGoViewModel) {
    Page("Set up tvgo", vm.state) {
        Section("1. This phone") {
            LocalNetworkPermission()
        }
        TV_STEPS.forEachIndexed { i, (title, body) ->
            Section("${i + 2}. $title") { Text(body) }
        }
        AiSettings(vm)
        Button(onClick = vm::finishSetup, Modifier.fillMaxWidth()) { Text("Done: find my TV") }
    }
}

@Composable
private fun LocalNetworkPermission() {
    val ctx = LocalContext.current
    val needed = Build.VERSION.SDK_INT >= 37
    var granted by remember {
        mutableStateOf(!needed || ContextCompat.checkSelfPermission(ctx, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) Text("Local network access: allowed")
    else {
        Text("Android 17 asks before an app talks to devices on your Wi-Fi. tvgo needs it to reach the TV.")
        Button(onClick = { launcher.launch(LOCAL_NETWORK) }) { Text("Allow local network access") }
    }
}

/** Which AI plans the packages the guard and the lists leave open. */
@Composable
fun AiSettings(vm: TvGoViewModel) {
    val s = vm.settings
    var ai by remember { mutableStateOf(s.ai) }
    var key by remember { mutableStateOf(s.claudeKey) }
    var model by remember { mutableStateOf(s.claudeModel) }
    var uninstall by remember { mutableStateOf(s.uninstallUserApps) }
    Section("AI that suggests what to remove") {
        Text("The safety guard and your lists always come first; the AI only decides the rest, and you confirm every change.")
        Settings.Ai.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().selectable(ai == option) { ai = option; s.ai = option },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(ai == option, onClick = { ai = option; s.ai = option })
                Text(option.label)
            }
        }
        if (ai == Settings.Ai.CLAUDE) {
            OutlinedTextField(key, { key = it; s.claudeKey = it }, label = { Text("Claude API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it; s.claudeModel = it }, label = { Text("Model") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = vm::checkAi, enabled = !vm.state.busy) { Text("Check the AI") }
        if (vm.state.aiStatus.isNotEmpty()) Text("AI: ${vm.state.aiStatus}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(uninstall, { uninstall = it; s.uninstallUserApps = it })
            Text("  Uninstall user apps instead of disabling them (restoring may need the Play Store)")
        }
    }
}

@Composable
fun SettingsScreen(vm: TvGoViewModel) {
    Page("Settings", vm.state) {
        AiSettings(vm)
        OutlinedButton(onClick = { vm.go(Screen.SETUP) }) { Text("Show the TV setup steps") }
        Button(onClick = { vm.go(if (vm.state.info != null) Screen.TV else Screen.CONNECT) }) { Text("Back") }
    }
}
