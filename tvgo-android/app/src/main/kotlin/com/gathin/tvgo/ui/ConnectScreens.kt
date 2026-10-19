package com.gathin.tvgo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/** Find the TV (or type it), pair when wireless debugging needs a code, then connect. */
@Composable
fun ConnectScreen(vm: TvGoViewModel) {
    val s = vm.state
    var address by remember { mutableStateOf(vm.settings.lastAddress) }
    var pairAddress by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Page("Connect to the TV", s) {
        Section("Find TVs on this Wi-Fi") {
            Text("Lists TVs with network debugging on. Nothing connects until you tap one.")
            Button(onClick = vm::discover, enabled = !s.busy) { Text("Find TVs") }
            s.candidates.forEach { c ->
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().clickable(enabled = !s.busy) {
                    if (c.pairing) pairAddress = c.address else vm.connect(c.address)
                }.padding(vertical = 6.dp)) {
                    Text(c.address)
                    Text(if (c.pairing) "${c.why}: tap to pair with the code the TV shows" else c.why)
                }
            }
        }
        Section("Or type its address") {
            OutlinedTextField(address, { address = it }, label = { Text("ip:port, e.g. 192.168.1.20:5555") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = { vm.connect(address) }, enabled = !s.busy && address.isNotBlank()) { Text("Connect") }
        }
        Section("Pair (Android 11+ wireless debugging)") {
            Text("On the TV: Wireless debugging > Pair device with pairing code. Enter the ip:port and code it shows. " +
                "Then connect to the ip:port shown on the Wireless debugging screen itself.")
            OutlinedTextField(pairAddress, { pairAddress = it }, label = { Text("Pairing ip:port") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = { vm.pair(pairAddress, code) }, enabled = !s.busy && code.length == 6) { Text("Pair") }
        }
        OutlinedButton(onClick = { vm.go(Screen.SETTINGS) }) { Text("Settings") }
        if (s.log.isNotEmpty()) LogView(s.log)
    }
}

/** The connected TV and what to do with it. */
@Composable
fun TvScreen(vm: TvGoViewModel) {
    val s = vm.state
    val info = s.info ?: return ConnectScreen(vm)
    Page("${info.brand} ${info.model}", s) {
        Section("This TV") {
            Text("${info.host} · ${info.device} · Android ${info.android}")
            Text("Home: ${info.home.ifEmpty { "?" }}")
            Text("Serial: ${info.serial.ifEmpty { "?" }}")
        }
        Button(onClick = vm::makePlan, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Plan a debloat (backs up, changes nothing)")
        }
        OutlinedButton(onClick = vm::openRestore, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Restore") }
        OutlinedButton(onClick = vm::rebootAndCheck, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Reboot and check the home screen")
        }
        Buttons {
            OutlinedButton(onClick = { vm.go(Screen.SETTINGS) }) { Text("Settings") }
            OutlinedButton(onClick = vm::disconnect, enabled = !s.busy) { Text("Other TV") }
        }
        if (s.log.isNotEmpty()) LogView(s.log)
    }
}
