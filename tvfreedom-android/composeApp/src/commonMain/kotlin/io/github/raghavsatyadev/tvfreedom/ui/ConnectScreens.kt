@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.store.Settings

/** Finds TVs by itself on open; the user taps one. Typing an address and pairing stay out of the way until needed. */
@Composable
fun ConnectScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    LaunchedEffect(Unit) { if (!s.searched && !s.busy) vm.discover() }
    Page("Pick your TV", s, actions = {
        IconButton(onClick = { vm.go(Screen.SETTINGS) }) { Icon(Icons.Settings, "Settings") }
    }) {
        // Which AI plans comes before the TV, so a TV's first debloat uses the model the user wants.
        item { GroupLabel("AI that plans the debloat") }
        item {
            val st = vm.settings
            SegmentedListItem(onClick = { vm.go(Screen.SETTINGS) }, shapes = seg(0, 1), colors = segColors(), enabled = !s.busy,
                leadingContent = { Icon(Icons.Clean, null) }, trailingContent = { Icon(Icons.Edit, "Change") },
                supportingContent = {
                    Text(when (st.ai) {
                        Settings.Ai.TERMUX -> "${st.termuxModel.label} · ${st.termuxModel.hint}"
                        Settings.Ai.CLAUDE -> st.claudeModel
                        else -> st.ai.hint
                    })
                }) { Text(st.ai.label) }
        }
        if (s.candidates.isNotEmpty()) {
            item { GroupLabel("On this Wi-Fi") }
            s.candidates.forEachIndexed { i, c ->
                item(key = c.address) {
                    SegmentedListItem(
                        onClick = { if (c.pairing) vm.showPair(c.address) else vm.connect(c.address) },
                        shapes = seg(i, s.candidates.size), colors = segColors(),
                        enabled = !s.busy,
                        leadingContent = { Icon(if (c.pairing) Icons.Link else Icons.Tv, null) },
                        trailingContent = { Icon(Icons.Next, null) },
                        supportingContent = {
                            Text(when {
                                c.pairing -> "Tap to pair with the code the TV shows"
                                c.why.startsWith("mDNS") -> "Wireless debugging"
                                else -> "Network debugging"
                            })
                        },
                    ) { Text(c.address) }
                }
            }
        }
        if (s.searched && s.candidates.isEmpty() && !s.busy) item { NothingFound() }
        if (s.searched && !s.busy) item {
            TextButton(onClick = vm::discover) {
                Icon(Icons.Refresh, null, Modifier.padding(end = 8.dp))
                Text("Search again")
            }
        }
        item { GroupLabel("Or enter its address") }
        item { AddressField(vm) }
        if (s.showPair) item { PairCard(vm) }
        else item {
            TextButton(onClick = { vm.showPair() }, enabled = !s.busy) {
                Icon(Icons.Link, null, Modifier.padding(end = 8.dp))
                Text("Pair with a code (Android 11+)")
            }
        }
    }
}

@Composable
private fun NothingFound() {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeBadge(Icons.Tv, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                cookie = false, size = 72)
            Text("No TVs found", style = MaterialTheme.typography.titleLarge)
            Text("Turn on network or wireless debugging on the TV and keep both on the same Wi-Fi. " +
                "Settings > Show the TV setup steps explains how.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddressField(vm: TvFreedomViewModel) {
    var address by remember { mutableStateOf(vm.settings.lastAddress) }
    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("ip:port") }, placeholder = { Text("192.168.1.20:5555") },
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = {
            FilledIconButton(onClick = { vm.connect(address) }, enabled = !vm.state.busy && address.isNotBlank(),
                modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Next, "Connect") }
        })
}

@Composable
private fun PairCard(vm: TvFreedomViewModel) {
    val s = vm.state
    var address by remember(s.pairAddress) { mutableStateOf(s.pairAddress) }
    var code by remember { mutableStateOf("") }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pair with a code", style = MaterialTheme.typography.titleLarge)
            Text("On the TV: Wireless debugging > Pair device with pairing code.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("ip:port the TV shows") }, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("6-digit code") }, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            MainAction("Pair and connect", Icons.Link, enabled = !s.busy && code.length == 6 && address.isNotBlank()) {
                vm.pair(address, code)
            }
            TextButton(onClick = vm::hidePair, enabled = !s.busy, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        }
    }
}

/** The connected TV and what to do with it. */
@Composable
fun TvScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    val info = s.info ?: return ConnectScreen(vm)
    Page("${info.brand} ${info.model}".trim(), s, subtitle = "Android ${info.android} · ${info.host}", actions = {
        IconButton(onClick = vm::disconnect, enabled = !s.busy) { Icon(Icons.Swap, "Other TV") }
        IconButton(onClick = { vm.go(Screen.SETTINGS) }) { Icon(Icons.Settings, "Settings") }
    }) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.Tv, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 120)
            }
        }
        item { MainAction("Plan a debloat", Icons.Clean, enabled = !s.busy, onClick = vm::makePlan) }
        item {
            Text("Backs up first. Nothing on the TV changes until you confirm.",
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { GroupLabel("More") }
        item {
            SegmentedListItem(onClick = vm::openRestore, shapes = seg(0, 2), colors = segColors(), enabled = !s.busy,
                leadingContent = { Icon(Icons.History, null) },
                supportingContent = { Text("Undo a run, or go back to a backup") }) { Text("Restore") }
        }
        item {
            SegmentedListItem(onClick = vm::rebootAndCheck, shapes = seg(1, 2), colors = segColors(), enabled = !s.busy,
                leadingContent = { Icon(Icons.Power, null) },
                supportingContent = { Text("Restart the TV and check its home screen") }) { Text("Restart and check") }
        }
    }
}
