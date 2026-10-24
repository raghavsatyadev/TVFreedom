@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.tvfreedom.flow.HomeDraft
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.LauncherConfig
import kotlinx.coroutines.launch

/**
 * Settings for plan phases 4 and 5: favourites in order, tiles per row, the screensaver's weather and calendars.
 * Saving only keeps them for the plan; nothing reaches the TV until the plan's typed GO.
 */
@Composable
fun HomeScreen(vm: TvFreedomViewModel) {
    val s = vm.state
    val draft = s.homeDraft
    Page("Home screen settings", s, onBack = { vm.go(Screen.PLAN) }) {
        if (draft != null) item(key = "form:${draft.hashCode()}") { HomeForm(vm, draft) }
    }
}

@Composable
private fun HomeForm(vm: TvFreedomViewModel, draft: HomeDraft) {
    val scope = rememberCoroutineScope()
    val c = vm.state.homeConfig ?: draft.config
    val favourites = remember { mutableStateListOf<String>().apply { addAll(c.favourites) } }
    var columns by remember { mutableStateOf(c.columns) }
    var place by remember { mutableStateOf(c.place) }
    var lat by remember { mutableStateOf(c.latitude) }
    var lon by remember { mutableStateOf(c.longitude) }
    var calendars by remember { mutableStateOf(c.calendars) }
    var black by remember { mutableStateOf(c.trueBlack) }
    var watching by remember { mutableStateOf(c.continueWatching) }
    var locating by remember { mutableStateOf("") }
    // An older home screen on the TV can't say what it uses: its favourites are kept unless changed here.
    val old = draft.olderLauncher

    fun locate() = scope.launch {
        locating = "Finding this phone's town…"
        val p = vm.phonePlace()
        locating = if (p == null) "This phone couldn't tell where it is. Type the town and its coordinates." else ""
        if (p != null) { place = p.name.ifEmpty { place }; lat = p.latitude; lon = p.longitude }
    }
    val permission = rememberPermission(Permission.LOCATION) { if (it) locate() }
    // Android asks once; after that the weather fills in by itself whenever it is still empty.
    LaunchedEffect(Unit) {
        if (lat.isEmpty()) if (permission.granted) locate() else permission.ask()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("A simple home screen with your favourite apps first, and a clock screensaver with the weather and " +
            "holidays. Restore brings the old home screen back.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        GroupLabel("Favourites, in order")
        if (old) Text("The home screen on the TV is an older version, so TVFreedom can't read its favourites. Leave this " +
            "list as it is to keep them, or change it to replace them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (favourites.isEmpty()) Text("No favourites: every app shows in one grid.", style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            favourites.forEachIndexed { i, pkg ->
                SegmentedListItem(onClick = {}, shapes = seg(i, favourites.size), colors = segColors(),
                    leadingContent = { Text("${i + 1}", style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { favourites.add(i - 1, favourites.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Up, "Move up") }
                            IconButton(onClick = { favourites.add(i + 1, favourites.removeAt(i)) }, enabled = i < favourites.size - 1) {
                                Icon(Icons.Expand, "Move down")
                            }
                            IconButton(onClick = { favourites.removeAt(i) }) { Icon(Icons.Close, "Remove") }
                        }
                    }) { Text(appName(pkg)) }
            }
        }
        val others = draft.apps.filter { it !in favourites }.sortedBy { appName(it).lowercase() }
        if (others.isNotEmpty()) {
            GroupLabel("Add a favourite")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                others.forEachIndexed { i, pkg ->
                    SegmentedListItem(onClick = { favourites.add(pkg) }, shapes = seg(i, others.size), colors = segColors(),
                        supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { Icon(Icons.Add, "Add") }) { Text(appName(pkg)) }
                }
            }
        }

        GroupLabel("Tiles per row")
        SegmentedListItem(onClick = {}, shapes = seg(0, 1), colors = segColors(),
            supportingContent = { Text(if (draft.reported) "What the TV uses now" else "Fits this TV's screen") },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { columns-- }, enabled = columns > 3) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    TextButton(onClick = { columns++ }, enabled = columns < 6) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }) { Text("$columns per row") }

        GroupLabel("Extras, off unless you turn them on")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SegmentedListItem(checked = black, onCheckedChange = { black = it }, shapes = seg(0, 2), colors = segColors(),
                supportingContent = { Text("Best on OLED TVs. App colours still show on the tiles.") },
                trailingContent = { Switch(black, null) }) { Text("Pure black background") }
            SegmentedListItem(checked = watching, onCheckedChange = { watching = it }, shapes = seg(1, 2), colors = segColors(),
                supportingContent = { Text("A row of shows you started, from apps that share them with the TV.") },
                trailingContent = { Switch(watching, null) }) { Text("Continue watching") }
        }

        GroupLabel("Screensaver")
        Card(shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weather for", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), label = { Text("Town") }, singleLine = true,
                    shape = MaterialTheme.shapes.large)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    OutlinedTextField(lat, { lat = it }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true,
                        shape = MaterialTheme.shapes.large, keyboardOptions = decimal)
                    OutlinedTextField(lon, { lon = it }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true,
                        shape = MaterialTheme.shapes.large, keyboardOptions = decimal)
                }
                if (locating.isNotEmpty()) Text(locating, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondAction("Use this phone's location", Icons.Place) {
                    if (permission.granted) locate() else permission.ask()
                }
                OutlinedTextField(calendars, { calendars = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Calendars to show") }, shape = MaterialTheme.shapes.large,
                    supportingText = {
                        Text((if (old) "TVFreedom can't read the ones the TV shows now: type them again. " else "") +
                            "Names as synced on the TV, separated by |. For example: Holidays in India")
                    })
            }
        }

        GroupLabel("Company branding")
        BrandCard(vm)

        MainAction("Use these settings", Icons.Done) {
            vm.saveHome(LauncherConfig(favourites.toList(), columns, place.trim(), lat.trim(), lon.trim(), calendars.trim(),
                    trueBlack = black, continueWatching = watching),
                keepTvFavourites = old && favourites.toList() == draft.config.favourites)
        }
    }
}

/** What phase 4 (home screen) or 5 (screensaver) would do on this TV, one line each, for the plan. */
fun homeLines(draft: HomeDraft, phase: Int): List<String> = buildList {
    val install = when {
        draft.installed == null -> "Install TVFreedom's home screen"
        draft.installed < draft.bundled -> "Update the home screen on the TV"
        else -> null
    }
    if (phase == 4) {
        add(install ?: "Keep the home screen already on the TV")
        add("Send it your favourites and tiles per row, then check it opens")
        draft.homeOff.forEach { add("Turn off ${it.pkg}: ${it.comment.substringBefore('(').trim()}") }
    } else {
        if (install != null) add("$install first (the clock lives in it)")
        add("Screensaver: the clock, with weather and holidays (now ${draft.oldScreensaver.substringBefore('/').ifEmpty { "none" }})")
        draft.dreamOff.forEach { add("Turn off ${it.pkg}: ${it.comment.substringBefore('(').trim()}") }
    }
    add("Then restart the TV and check; undone at once if the check fails")
    if (!draft.fromLists) add("This TV has no tested list, so TVFreedom picked these from what the TV reports. Check them.")
}

/** A readable name for a TV app from its package (adb can't read app labels). */
fun appName(pkg: String): String = KNOWN_APPS[pkg] ?: pkg.split('.')
    .lastOrNull { it.lowercase() !in GENERIC && it.length > 2 }?.replaceFirstChar { it.uppercase() } ?: pkg

private val GENERIC = setOf("com", "org", "android", "androidtv", "tv", "app", "apps", "google", "livingroom", "leanback", "atv")

private val KNOWN_APPS = mapOf(
    "com.netflix.ninja" to "Netflix",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "in.startv.hotstar" to "JioHotstar",
    "com.mitv.tvhome.mitvplus" to "Mi TV+",
    "com.google.android.youtube.tv" to "YouTube",
    "com.google.android.youtube.tvmusic" to "YouTube Music",
    "org.jellyfin.androidtv" to "Jellyfin",
    "com.plexapp.android" to "Plex",
    "com.android.vending" to "Play Store",
    "com.spotify.tv.android" to "Spotify",
    "com.disney.disneyplus" to "Disney+",
    "com.google.android.apps.tv.launcherx" to "Google TV",
    HomeOps.LAUNCHER to "TVFreedom home",
)
