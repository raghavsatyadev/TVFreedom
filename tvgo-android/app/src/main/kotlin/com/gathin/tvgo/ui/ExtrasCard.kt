@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.gathin.tvgo.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.flow.ExtrasChoice
import com.gathin.tvgo.flow.ExtrasDraft
import com.gathin.tvgo.tv.ExtraOps
import com.gathin.tvgo.tv.Parse
import com.gathin.tvgo.tv.hostname

/** Why Private DNS starts off, shown under its switch. */
const val DNS_WARNING = "Not recommended. Ads in YouTube and streaming apps come from the same servers as the video, so " +
    "it barely changes what you see. Blocked update or licence servers can stop apps or updates from working. Every " +
    "name the TV looks up goes to a third party. If that server is down, the TV has no internet at all. A blocker on " +
    "your router covers every device without touching the TV."

/** Phase 6: a switch for the phase, then one for each extra and what it would do on this TV. */
fun LazyListScope.extrasPhase(vm: TvGoViewModel, d: ExtrasDraft) {
    val s = vm.state
    val c = s.extras
    val on = 6 in s.phases
    val edit = { f: (ExtrasChoice) -> ExtrasChoice -> vm.setExtras(f(c)) }
    item(key = "label:6") { GroupLabel("Phase 6 · extras") }
    item(key = "phase:6") {
        SegmentedListItem(checked = on, onCheckedChange = { vm.togglePhase(6) }, shapes = seg(0, 2), colors = segColors(),
            enabled = !s.busy,
            supportingContent = { Text(if (c.due(d)) extrasParts(d to c).joinToString("; ").replaceFirstChar { it.uppercase() } else "Nothing to do with these switches") },
            trailingContent = { Switch(on, null) }) { Text(if (on) "Included" else "Skipped") }
    }

    val apps = d.lockApps.keys.sortedBy { appName(it).lowercase() }
    toggle("lock", "Stop kept apps running in the background",
        "They still open and play when started on the TV; once closed they don't sit in memory. Casting or remote " +
            "play into a closed app, and background sync, may stop working. Keyboards, VPNs, cast and voice apps are never touched. ${apps.size} app${if (apps.size == 1) "" else "s"}.",
        c.lockdown, !s.busy, apps.size + 1) { edit { it.copy(lockdown = !it.lockdown) } }
    apps.forEachIndexed { i, pkg ->
        val already = d.lockApps[pkg] == "ignore"
        val mine = pkg !in c.lockSkip
        item(key = "extra:lock:$pkg") {
            SegmentedListItem(checked = mine, onCheckedChange = {
                edit { it.copy(lockSkip = if (mine) it.lockSkip + pkg else it.lockSkip - pkg) }
            }, shapes = seg(i + 1, apps.size + 1), colors = segColors(), enabled = c.lockdown && !s.busy && !already,
                supportingContent = { Text(if (already) "Already stopped · $pkg" else pkg) },
                trailingContent = { Switch(mine || already, null) }) { Text(appName(pkg), style = MaterialTheme.typography.bodyMedium) }
        }
    }

    val changes = ExtraOps.SETTINGS.filter { (_, k, v) -> d.settings[k] != v }
    toggle("settings", "Faster and quieter",
        if (changes.isEmpty()) "Already set on this TV."
        else "Animations at half length, no crash reports to Google, no location scans while Wi-Fi is off. Restore puts the old values back.",
        c.settings, !s.busy, changes.size + 1) { edit { it.copy(settings = !it.settings) } }
    changes.forEachIndexed { i, (_, k, v) ->
        item(key = "extra:set:$k") {
            SegmentedListItem(onClick = {}, shapes = seg(i + 1, changes.size + 1), colors = segColors(), enabled = c.settings) {
                Text("$k: ${d.settings[k]} → $v", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    toggle("dns", "Block ads with Private DNS",
        if (!d.privateDns) "Needs Android 9 or later." else DNS_WARNING,
        c.dns && d.privateDns, !s.busy && d.privateDns, if (c.dns && d.privateDns) 2 else 1) { edit { it.copy(dns = !it.dns) } }
    if (c.dns && d.privateDns) item(key = "extra:dns:host") {
        val bad = runCatching { Parse.hostname(c.dnsHost) }.isFailure
        OutlinedTextField(c.dnsHost, { v -> edit { it.copy(dnsHost = v.trim()) } }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text("DNS server (hostname)") }, singleLine = true, isError = bad, enabled = !s.busy,
            supportingText = { Text(if (bad) "Not a hostname" else "Now: ${if (d.dnsMode == "hostname") d.dnsHost else "the TV's default"}. tvgo checks the TV can still look names up, or turns it off again.") })
    }

    toggle("tube", "Replace YouTube with SmartTube",
        if (d.smartTube && !d.youtubeOn) "Already done on this TV."
        else "Downloads SmartTube ${ExtraOps.SMARTTUBE_URL.substringAfter("download/").substringBefore('/')} " +
            "(${ExtraOps.SMARTTUBE_MB} MB, open source, no ad code) from its GitHub releases, checks it against a pinned " +
            "checksum, checks it opens, then turns YouTube off (not removed) and puts SmartTube in its place on the home " +
            "screen. You sign in again inside SmartTube.",
        c.smartTube, !s.busy, 1) { edit { it.copy(smartTube = !it.smartTube) } }
}

/** One extra's switch, the first row of a group of [n]. */
private fun LazyListScope.toggle(key: String, title: String, detail: String, checked: Boolean, enabled: Boolean, n: Int, flip: () -> Unit) {
    item(key = "extra:$key") {
        SegmentedListItem(checked = checked, onCheckedChange = { flip() }, shapes = seg(0, n), colors = segColors(), enabled = enabled,
            supportingContent = { Text(detail) }, trailingContent = { Switch(checked, null) }) { Text(title) }
    }
}
