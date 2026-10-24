package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.core.Text
import io.github.raghavsatyadev.tvfreedom.flow.ChangeRestore
import io.github.raghavsatyadev.tvfreedom.flow.Extras
import io.github.raghavsatyadev.tvfreedom.flow.ExtrasChoice
import io.github.raghavsatyadev.tvfreedom.flow.HomeDraft
import io.github.raghavsatyadev.tvfreedom.flow.HomeSetup
import io.github.raghavsatyadev.tvfreedom.flow.PhaseRun
import io.github.raghavsatyadev.tvfreedom.flow.RunResult
import io.github.raghavsatyadev.tvfreedom.flow.Rebloat
import io.github.raghavsatyadev.tvfreedom.flow.Runner
import io.github.raghavsatyadev.tvfreedom.store.RestoreOption
import io.github.raghavsatyadev.tvfreedom.store.Settings
import io.github.raghavsatyadev.tvfreedom.tv.ConnectResult
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.LauncherConfig
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.TvLink
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import io.github.raghavsatyadev.tvfreedom.tv.Undo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvFreedomViewModel(internal val platform: AppPlatform) : ViewModel() {
    internal val storage = platform.storage
    val settings: Settings = storage.settings
    val brand = platform.brand
    private val link: TvLink = platform.link
    internal var tv: Tv? = null

    var state by mutableStateOf(UiState(screen = if (settings.onboarded) Screen.CONNECT else Screen.SETUP))
        private set

    /** The Connect screen's address box. Kept here, so a failed connect or a list redraw keeps what was typed. */
    var typedAddress by mutableStateOf(settings.lastAddress)

    internal fun update(f: UiState.() -> UiState) {
        state = state.f()
    }

    internal fun log(line: String) = update { copy(log = (log + line).takeLast(400), status = line) }

    /** Log a line the user must see once the step ends. */
    internal fun tell(line: String) {
        log(line)
        update { copy(notice = line) }
    }

    /** Gemini Nano (or Termux) is waiting for TVFreedom to be on screen again. */
    private var askOpen by mutableStateOf(false)

    internal suspend fun waitOpen() {
        if (platform.open.value > 0) return
        askOpen = true
        try {
            platform.open.first { it > 0 }
        } finally {
            askOpen = false
        }
    }

    /** The notification while a job runs: its headline, the latest line, and the steps done. */
    private fun progress() = Progress(state.headline.ifEmpty { "TVFreedom" },
        if (askOpen) "Open TVFreedom to go on: the AI on this phone only answers while TVFreedom is open." else state.status.ifEmpty { "Working" },
        state.runDone.size, state.runSteps.size, askOpen)

    private val rebloat by lazy { Rebloat(storage, runner, ::log) }
    private val runner by lazy { Runner(storage, link, ::log) { update { copy(headline = it) } } }
    private val changeRestore by lazy { ChangeRestore(storage, runner, ::log) }
    private val homeSetup by lazy { HomeSetup(storage, runner, ::log) }
    private val extras by lazy { Extras(storage, platform.net, runner, ::log) }
    private val phaseRun by lazy { PhaseRun(runner, homeSetup, extras, ::log) }

    /** One job at a time: nothing starts while another step runs. [headline] names it on screen; empty = silent. */
    internal fun job(headline: String = "", block: suspend () -> Unit) {
        if (state.busy) return
        update { copy(busy = true, headline = headline, status = "", notice = "") }
        // Named jobs talk to the TV for a while: keep TVFreedom alive and show their progress if the user leaves the app.
        val long = headline.isNotEmpty()
        if (long) platform.startJob(progress())
        viewModelScope.launch {
            val watch = if (long) launch { snapshotFlow { progress() }.collect { platform.updateJob(it) } } else null
            try {
                block()
            } catch (e: Exception) {
                val why = "Something went wrong: ${Text.why(e)}"
                log(why)
                if (state.screen == Screen.RUN && state.outcome == null)
                    update { copy(outcome = Outcome(false, "Stopped", "$why. Check the TV; Restore can undo this run.")) }
                else update { copy(notice = why) }
            } finally {
                watch?.cancel()
                if (long) platform.stopJob()
                update { copy(busy = false, headline = "") }
            }
        }
    }

    fun go(screen: Screen) = update { copy(screen = screen, notice = "") }

    fun finishSetup() {
        settings.onboarded = true
        go(if (tv?.connected == true) Screen.TV else Screen.CONNECT)
    }

    private var checking: kotlinx.coroutines.Job? = null

    /** Check the picked AI. Runs beside other work (a TV search must not hold it up), and only one at a time. */
    fun checkAi() {
        if (checking?.isActive == true) return
        update { copy(aiStatus = "") }
        checking = viewModelScope.launch {
            val status = runCatching { platform.checkAi(settings.ai, settings, ::log) }.getOrElse { "check failed: ${Text.why(it)}" }
            update { copy(aiStatus = status) }
        }
    }

    fun pickAi(ai: Settings.Ai) {
        checking?.cancel() // a check still running is for the old choice
        settings.ai = ai
        update { copy(aiStatus = "") }
    }

    private var probing: kotlinx.coroutines.Job? = null

    /** Find the next Termux setup step. Quiet and separate from [job], so it never blocks or flashes the screen. */
    fun probeTermux() {
        if (probing?.isActive == true) return
        probing = viewModelScope.launch {
            val step = runCatching { platform.termuxStep(::log) }.getOrDefault(TermuxStep.OPEN_TERMUX)
            update { copy(termuxStep = step) }
        }
    }

    fun discover() = job("Looking for TVs") { search() }

    private suspend fun search() {
        update { copy(candidates = emptyList(), log = emptyList()) }
        if (link.localIp() == null) return tell("This phone is not on Wi-Fi. Join the TV's Wi-Fi, or type the TV's address.")
        log("looking for TVs on this Wi-Fi")
        val found = link.find()
        update { copy(candidates = found, searched = true) }
        log("found ${found.size}")
    }

    fun showPair(address: String = "") = update { copy(showPair = true, pairAddress = address.ifEmpty { pairAddress }) }

    fun hidePair() = update { copy(showPair = false) }

    fun connect(address: String) = job("Connecting") { typedAddress = address.trim(); open(address) }

    private suspend fun open(address: String) {
        val (host, port) = TvLink.parseAddress(address) ?: return tell("\"$address\" is not an address. Use ip:port, e.g. 192.168.1.20:5555.")
        tv?.close()
        val t = link.tv(host, port)
        log("If the TV asks \"Allow debugging?\", tick Always allow and press OK")
        when (val r = t.connect()) {
            ConnectResult.Ok -> Unit
            ConnectResult.PairingRequired -> {
                update { copy(showPair = true) }
                return tell("This TV needs pairing first: on the TV open Wireless debugging > Pair device with pairing code.")
            }
            is ConnectResult.Failed -> {
                log("connect ${t.address}: ${r.why}")
                return tell("Could not reach ${t.address}. Check the TV is on, on the same Wi-Fi, with debugging on.")
            }
        }
        val ops = TvOps(t, t.address)
        if (!ops.isTv()) {
            t.close()
            return tell("${t.address} is not a TV.")
        }
        ops.key("KEYCODE_WAKEUP")
        val info = ops.identify()
        tv = t
        settings.lastAddress = t.address
        typedAddress = t.address
        log("TV: ${info.brand} ${info.model} (${info.device}), Android ${info.android}")
        update { copy(info = info, plan = null, showPair = false, screen = Screen.TV) }
    }

    /** Pair, then find the TV's own debugging port and connect to it, so the user doesn't have to. */
    fun pair(address: String, code: String) = job("Pairing") {
        val (host, port) = TvLink.parseAddress(address) ?: return@job tell("\"$address\" is not an address. Use the ip:port the TV shows.")
        update { copy(pairAddress = address) }
        val r = link.pair(host, port, code)
        if (r != "paired") return@job tell("Pairing failed. Check the code on the TV and try again. ($r)")
        update { copy(showPair = false, headline = "Finding the TV") }
        search()
        val same = state.candidates.firstOrNull { !it.pairing && it.address.substringBeforeLast(':') == host }
        if (same != null) {
            update { copy(headline = "Connecting") }
            open(same.address)
        } else tell("Paired. Now pick the TV below, or type the address shown on its Wireless debugging screen.")
    }

    fun disconnect() {
        tv?.close()
        tv = null
        update { copy(info = null, plan = null, notice = "", screen = Screen.CONNECT) }
    }

    internal fun classifier(): Classifier? = platform.classifier(settings.ai, settings, ::waitOpen, ::log)

    /** Backup, then plan. Changes nothing on the TV. */
    fun makePlan() = job("Making a plan") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        forgetScroll()
        update { copy(log = emptyList(), screen = Screen.PLAN, plan = null) }
        runner.backup(t, info)
        val ai = classifier()
        update { copy(headline = if (ai == null) "Making a plan" else "Asking the AI about the rest") }
        val (plan, dir) = try {
            runner.plan(t, info, ai)
        } finally {
            ai?.close()
        }
        // Phases 4 and 5 come from the launcher and screensaver on the TV, not from the package lists.
        val draft = runCatching { homeSetup.draft(t, info, launcher().second) }
            .onFailure { log("could not read the home screen: ${Text.why(it)}") }.getOrNull()
        val extra = runCatching { extras.draft(t, info, plan) }
            .onFailure { log("could not read the extras: ${Text.why(it)}") }.getOrNull()
        // Every phase with something to do starts on; phase 4 also when the TV's logo or colour isn't the company's.
        val home = listOfNotNull(4.takeIf { draft != null && (draft.launcherDue || brandDue(draft)) },
            5.takeIf { draft?.screensaverDue == true }, 6.takeIf { extra != null && ExtrasChoice().due(extra) })
        // The screensaver's weather: if the TV has none yet, use this phone's town (Android asked once, on the form).
        val config = draft?.config?.let { c ->
            if (c.latitude.isNotEmpty() || !platform.locationGranted()) c
            else platform.phonePlace()?.let { p -> c.copy(place = p.name, latitude = p.latitude, longitude = p.longitude) } ?: c
        }
        update {
            copy(plan = plan, runDir = dir, phases = plan.phases.toSet() + home, homeDraft = draft, homeConfig = config,
                extrasDraft = extra, extras = ExtrasChoice(),
                keepTvFavourites = draft?.olderLauncher == true)
        }
    }

    /** The settings from the home-screen form, used if phase 4 or 5 runs. */
    fun saveHome(config: LauncherConfig, keepTvFavourites: Boolean) =
        update { copy(homeConfig = config, keepTvFavourites = keepTvFavourites, screen = Screen.PLAN) }

    fun togglePhase(p: Int) = update { copy(phases = if (p in phases) phases - p else phases + p) }

    /** Switching an extra on that has something to do includes phase 6 too. */
    fun setExtras(c: ExtrasChoice) = update {
        copy(extras = c, phases = if (extrasDraft?.let { c.due(it) && !extras.due(it) } == true) phases + 6 else phases)
    }

    /** The switched-on phases that have something to do, in order. */
    fun chosenPhases(): List<Int> {
        val plan = state.plan ?: return emptyList()
        val extra = state.extrasDraft
        return state.phases.filter {
            it < 4 && plan.remove.any { r -> r.phase == it } || it in 4..5 && state.homeDraft != null ||
                it == 6 && extra != null && state.extras.due(extra)
        }.sorted()
    }

    /** Only called after the user typed GO: the chosen phases, then one restart and check at the end. */
    fun apply() = job("Changing the TV") {
        val t = tv ?: return@job
        val plan = state.plan ?: return@job
        val dir = state.runDir ?: return@job
        val draft = state.homeDraft
        val phases = chosenPhases()
        if (phases.isEmpty()) return@job tell("Nothing chosen.")
        val choice = if (phases.any { it in 4..5 }) HomeSetup.Choice(
            (state.homeConfig ?: draft!!.config).copy(brandColor = brand.color), state.keepTvFavourites,
            launcher().first, withContext(Dispatchers.IO) { brand.logo }) else null
        update {
            copy(screen = Screen.RUN, log = emptyList(), outcome = null, runPhase = null, runDone = emptyList(),
                runSteps = plainSteps(plan, draft, phases, brand.hasLogo, state.extrasDraft?.let { it to state.extras }))
        }
        val o = phaseRun.run(t, plan.info, plan, dir, phases, draft, choice, state.extrasDraft, state.extras) { now, done ->
            update { copy(runPhase = now, runDone = done) }
        }
        o.last.tv?.let { tv = it }
        val undone = listOfNotNull("home screen".takeIf { 4 in o.done }, "screensaver".takeIf { 5 in o.done },
            "extras".takeIf { 6 in o.done })
        val back = if (undone.isEmpty()) "" else "The old " + undone.joinToString(" and ") + " were put back. "
        val restore = if (o.done.any { it < 4 }) "To bring back the apps, open Restore and undo this run (the newest one)." else ""
        update {
            copy(outcome = when {
                o.failed == null -> outcome(o.last, true, "All done",
                    "The TV restarted once and its home screen opened. Undo any time from Restore.")
                o.last.tv == null -> outcome(o.last, false, "The TV didn't come back",
                    "It didn't reconnect after the restart. Check the TV, then undo from Restore.")
                else -> outcome(o.last, false, "The TV didn't check out after the restart",
                    (back + restore).trim())
            })
        }
    }

    fun openRestore() = job {
        val info = state.info ?: return@job
        val options = withContext(Dispatchers.IO) { storage.restoreOptions(info) }
        update { copy(restoreOptions = options, restoreChoice = null, restorePreview = "", screen = Screen.RESTORE,
            restoreTab = 0, changes = null, picked = emptySet()) }
    }

    fun restoreTab(tab: Int) {
        update { copy(restoreTab = tab) }
        if (tab == 1 && state.changes == null) readChanges()
    }

    private fun readChanges() = job("Checking what is still changed") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        val groups = changeRestore.list(t, info)
        update { copy(changes = groups, picked = picked.filterTo(mutableSetOf()) { id -> groups.any { g -> g.changes.any { it.id == id } } }) }
    }

    fun pickChange(id: String) = update { copy(picked = if (id in picked) picked - id else picked + id) }

    /** Only called after the user typed RESTORE. */
    fun undoChanges() = job("Restoring") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        val picked = state.picked.takeIf { it.isNotEmpty() } ?: return@job
        update { copy(screen = Screen.RUN, log = emptyList(), outcome = null, runSteps = emptyList(), changes = null) }
        val r = changeRestore.undo(t, info, picked)
        val home = if (r.keptHome) "The TVFreedom home screen stayed, because the TV's own home screen is off. " +
            "Turn it back on first (it is in the list), then remove ours." else ""
        update {
            copy(outcome = if (r.left.isEmpty()) Outcome(true, "Undone", "Restart the TV and check the home screen if you like.")
            else Outcome(false, "Some changes stayed", (listOf(home) + r.left.filter { it.id != ChangeRestore.HOME_ID }
                .map { "Still changed: ${it.title}" }).filter { it.isNotEmpty() }.joinToString("\n")))
        }
    }

    fun previewRestore(option: RestoreOption) = job {
        val t = tv ?: return@job
        val preview = if (option.kind == RestoreOption.Kind.FACTORY) rebloat.plan(t, state.info ?: return@job).describe()
        else if (option.kind == RestoreOption.Kind.BACKUP) {
            val (enable, reinstall) = runner.diffToBackup(t, option.dir!!)
            listOfNotNull(
                enable.takeIf { it.isNotEmpty() }?.let { "Turn back on (${it.size}): ${it.joinToString(", ")}" },
                reinstall.takeIf { it.isNotEmpty() }?.let { "Reinstall (${it.size}): ${it.joinToString(", ")}" },
            ).joinToString("\n\n")
        } else (option.entries.map { "${if (it.action == "disable") "Turn back on" else "Reinstall"} ${it.pkg}" } +
            option.dir?.let { storage.savedSettings(it) }.orEmpty().filterKeys { it == HomeOps.SCREENSAVER }
                .map { "Screensaver back to ${it.value}" } +
            option.dir?.let { storage.undoLines(it) }.orEmpty().asReversed().map(Undo::describe)).joinToString("\n")
        update { copy(restoreChoice = option, restorePreview = preview) }
    }

    /** Only called after the user typed RESTORE. */
    fun restore() = job("Restoring") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        val option = state.restoreChoice ?: return@job
        update { copy(screen = Screen.RUN, log = emptyList(), outcome = null, runSteps = emptyList()) }
        if (option.kind == RestoreOption.Kind.FACTORY) {
            val r = rebloat.run(t, info)
            r.tv?.let { tv = it }
            return@job update {
                copy(outcome = if (r.ok) outcome(r, true, "Back to how it came", "Every app is back and the TV's own home screen opened.")
                else outcome(r, false, "Home screen not reached", "Everything was put back, but check the TV's home screen."))
            }
        }
        if (option.kind == RestoreOption.Kind.BACKUP) runner.restoreBackup(t, info, option.dir!!) else runner.restoreList(t, option)
        update { copy(outcome = Outcome(true, "Restored", "Restart the TV and check the home screen if you like.")) }
    }

    fun rebootAndCheck() = job("Restarting the TV") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        update { copy(screen = Screen.RUN, log = emptyList(), outcome = null, runSteps = emptyList()) }
        val r = runner.rebootAndCheck(t, info, storage.shotDir())
        r.tv?.let { tv = it }
        update {
            copy(outcome = if (r.ok) outcome(r, true, "Home screen OK", "The TV restarted and its home screen opened.")
            else outcome(r, false, "Home screen not reached", "Check the TV. If it's stuck, open Restore and undo the newest run."))
        }
    }

    /** The TV's home screen doesn't show the company's logo and colour yet. */
    private fun brandDue(draft: HomeDraft) = !draft.config.brandColor.equals(brand.color, ignoreCase = true) ||
        draft.config.logoBytes != brand.logoSize

    fun pickLogo(image: ByteArray) = job {
        val colors = withContext(Dispatchers.IO) { brand.import(image) }
        update { copy(brandColors = colors, brandStamp = brandStamp + 1) }
    }

    fun loadBrand() = viewModelScope.launch {
        val colors = withContext(Dispatchers.IO) { brand.suggestions() }
        update { copy(brandColors = colors) }
    }

    fun setBrandColor(c: String) {
        brand.color = c
        update { copy(brandStamp = brandStamp + 1) }
    }

    fun clearBrand() {
        brand.clear()
        update { copy(brandColors = emptyList(), brandStamp = brandStamp + 1) }
    }

    private fun outcome(r: RunResult, ok: Boolean, headline: String, body: String) =
        Outcome(ok, headline, body, r.ramBefore, r.ramAfter, r.screen)

    /** The home screen APK for the TV, from the newest GitHub release, and its versionCode. */
    internal suspend fun launcher(): Pair<ByteArray, Long> = platform.launcherApk(::log)

    /** This phone's town for the weather, when Android allows it. */
    suspend fun phonePlace(): Place? = withContext(Dispatchers.IO) { platform.phonePlace() }

    /** A file the run left, such as the TV's screenshot. */
    fun readBytes(p: okio.Path): ByteArray? = storage.readBytes(p)

    override fun onCleared() {
        tv?.close()
    }
}
