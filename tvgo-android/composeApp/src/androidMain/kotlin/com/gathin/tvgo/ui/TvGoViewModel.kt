package com.gathin.tvgo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gathin.tvgo.TvGoApp
import com.gathin.tvgo.ai.ClaudeClassifier
import com.gathin.tvgo.ai.NanoClassifier
import com.gathin.tvgo.ai.TermuxClaude
import com.gathin.tvgo.core.Classifier
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.TvInfo
import com.gathin.tvgo.flow.Extras
import com.gathin.tvgo.tv.HomeOps
import com.gathin.tvgo.tv.Undo
import com.gathin.tvgo.flow.ExtrasChoice
import com.gathin.tvgo.flow.ExtrasDraft
import com.gathin.tvgo.flow.HomeDraft
import com.gathin.tvgo.flow.HomeSetup
import com.gathin.tvgo.flow.PhaseRun
import com.gathin.tvgo.flow.RunService
import com.gathin.tvgo.flow.RunResult
import com.gathin.tvgo.flow.Runner
import com.gathin.tvgo.store.Brand
import com.gathin.tvgo.store.PhoneLocation
import com.gathin.tvgo.store.RestoreOption
import com.gathin.tvgo.store.Settings
import com.gathin.tvgo.tv.AdbKeys
import com.gathin.tvgo.tv.AdbTv
import com.gathin.tvgo.tv.Candidate
import com.gathin.tvgo.tv.ConnectResult
import com.gathin.tvgo.tv.Discovery
import com.gathin.tvgo.tv.LauncherConfig
import com.gathin.tvgo.tv.TvOps
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { SETUP, CONNECT, TV, PLAN, RUN, RESTORE, SETTINGS, HOME }

/** How a run ended, for the result screen: free RAM in MB before and after, and the TV's screen after the restart. */
data class Outcome(
    val ok: Boolean,
    val headline: String,
    val body: String,
    val ramBefore: Int? = null,
    val ramAfter: Int? = null,
    val screen: File? = null,
)

data class UiState(
    val screen: Screen = Screen.SETUP,
    val busy: Boolean = false,
    /** Plain name of the step running now; empty for quick steps, which show nothing. */
    val headline: String = "",
    /** The latest log line: the detail under the headline. */
    val status: String = "",
    /** What the user has to know from the last step (a failure or something to do); empty when all went well. */
    val notice: String = "",
    val log: List<String> = emptyList(),
    val searched: Boolean = false,
    val candidates: List<Candidate> = emptyList(),
    val showPair: Boolean = false,
    val pairAddress: String = "",
    val info: TvInfo? = null,
    val plan: Plan? = null,
    val runDir: File? = null,
    val phases: Set<Int> = emptySet(),
    val outcome: Outcome? = null,
    val restoreOptions: List<RestoreOption> = emptyList(),
    val restoreChoice: RestoreOption? = null,
    val restorePreview: String = "",
    val aiStatus: String = "",
    /** The next thing to do to use Claude in Termux; null while checking. */
    val termuxStep: TermuxClaude.Step? = null,
    /** What phases 4 and 5 (home screen, screensaver) would do on this TV; read with the plan. */
    val homeDraft: HomeDraft? = null,
    /** The launcher settings the user chose for phases 4 and 5. */
    val homeConfig: LauncherConfig? = null,
    /** Keep the favourites an older launcher on the TV saved (the user left the list as it was). */
    val keepTvFavourites: Boolean = false,
    /** Background colours that go with the company logo; bumps [brandStamp] when the logo or colour changes. */
    val brandColors: List<String> = emptyList(),
    val brandStamp: Int = 0,
    /** While a debloat runs: the phase on now and the ones done, for the step list. */
    val runPhase: Int? = null,
    val runDone: List<Int> = emptyList(),
    /** The steps of the debloat on the run screen, in everyday words; empty for a restore or a restart. */
    val runSteps: List<PlainStep> = emptyList(),
    /** What phase 6 (lockdown, settings, DNS, SmartTube) finds on this TV, and what the user switched on. */
    val extrasDraft: ExtrasDraft? = null,
    val extras: ExtrasChoice = ExtrasChoice(),
)

class TvGoViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = (app as TvGoApp).storage
    val settings: Settings = storage.settings
    val brand = Brand(app, settings)
    private val discovery = Discovery(app)
    private val keys by lazy { AdbKeys(storage.keysDir) }
    private var tv: AdbTv? = null

    var state by mutableStateOf(UiState(screen = if (settings.onboarded) Screen.CONNECT else Screen.SETUP))
        private set

    private fun update(f: UiState.() -> UiState) {
        state = state.f()
    }

    private fun log(line: String) = update { copy(log = (log + line).takeLast(400), status = line) }

    /** Log a line the user must see once the step ends. */
    private fun tell(line: String) {
        log(line)
        update { copy(notice = line) }
    }

    private val app get() = getApplication<TvGoApp>()

    /** Gemini Nano (or Termux) is waiting for TVFreedom to be on screen again. */
    private var askOpen by mutableStateOf(false)

    private suspend fun waitOpen() {
        if (app.open.value > 0) return
        askOpen = true
        try {
            app.awaitOpen()
        } finally {
            askOpen = false
        }
    }

    /** The notification while a job runs: its headline, the latest line, and the steps done. */
    private fun progress() = RunService.Progress(state.headline.ifEmpty { "TVFreedom" },
        if (askOpen) "Open TVFreedom to go on: the AI on this phone only answers while TVFreedom is open." else state.status.ifEmpty { "Working" },
        state.runDone.size, state.runSteps.size, askOpen)

    private val runner by lazy { Runner(storage, keys, discovery, ::log) { update { copy(headline = it) } } }
    private val homeSetup by lazy { HomeSetup(storage, runner, ::log) }
    private val extras by lazy { Extras(storage, runner, ::log) }
    private val phaseRun by lazy { PhaseRun(runner, homeSetup, extras, ::log) }

    /** One job at a time: nothing starts while another step runs. [headline] names it on screen; empty = silent. */
    private fun job(headline: String = "", block: suspend () -> Unit) {
        if (state.busy) return
        update { copy(busy = true, headline = headline, status = "", notice = "") }
        // Named jobs talk to the TV for a while: keep TVFreedom alive and show their progress if the user leaves the app.
        val long = headline.isNotEmpty()
        if (long) RunService.start(app, progress())
        viewModelScope.launch {
            val watch = if (long) launch { snapshotFlow { progress() }.collect { RunService.update(app, it) } } else null
            try {
                block()
            } catch (e: Exception) {
                val why = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
                log(why)
                if (state.screen == Screen.RUN && state.outcome == null)
                    update { copy(outcome = Outcome(false, "Stopped", "$why. Check the TV; Restore can undo this run.")) }
                else update { copy(notice = why) }
            } finally {
                watch?.cancel()
                if (long) RunService.stop(app)
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
            val status = runCatching {
                when (settings.ai) {
                    Settings.Ai.TERMUX -> TermuxClaude(getApplication(), settings.termuxModel.alias).check().let { if (it == TermuxClaude.READY) "Claude is ready" else it }
                    Settings.Ai.NANO -> NanoClassifier().let { n -> try { n.prepare(::log) } finally { n.close() } }
                    Settings.Ai.CLAUDE -> if (settings.claudeKey.isBlank()) "add a Claude API key" else "key saved"
                    Settings.Ai.NONE -> "no AI: only the guard and the lists decide"
                }
            }.getOrElse { "check failed: ${it.message ?: it.javaClass.simpleName}" }
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
            val step = runCatching { TermuxClaude(getApplication()).probe(::log) }.getOrDefault(TermuxClaude.Step.OPEN_TERMUX)
            update { copy(termuxStep = step) }
        }
    }

    fun discover() = job("Looking for TVs") { search() }

    private suspend fun search() {
        update { copy(candidates = emptyList(), log = emptyList()) }
        if (discovery.localIp() == null) return tell("This phone is not on Wi-Fi. Join the TV's Wi-Fi, or type the TV's address.")
        log("looking for TVs (mDNS and port 5555)")
        val found = discovery.find()
        update { copy(candidates = found, searched = true) }
        log("found ${found.size}")
    }

    fun showPair(address: String = "") = update { copy(showPair = true, pairAddress = address.ifEmpty { pairAddress }) }

    fun hidePair() = update { copy(showPair = false) }

    fun connect(address: String) = job("Connecting") { open(address) }

    private suspend fun open(address: String) {
        val (host, port) = AdbTv.parseAddress(address) ?: return tell("\"$address\" is not an address. Use ip:port, e.g. 192.168.1.20:5555.")
        tv?.close()
        val t = AdbTv(host, port, keys)
        log("If the TV asks \"Allow debugging?\", tick Always allow and press OK")
        when (val r = t.connect()) {
            ConnectResult.Ok -> Unit
            ConnectResult.PairingRequired -> {
                update { copy(showPair = true) }
                return tell("This TV needs pairing first: on the TV open Wireless debugging > Pair device with pairing code.")
            }
            is ConnectResult.Failed -> return tell("Could not connect to ${t.address}: ${r.why}")
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
        log("TV: ${info.brand} ${info.model} (${info.device}), Android ${info.android}")
        update { copy(info = info, plan = null, showPair = false, screen = Screen.TV) }
    }

    /** Pair, then find the TV's own debugging port and connect to it, so the user doesn't have to. */
    fun pair(address: String, code: String) = job("Pairing") {
        val (host, port) = AdbTv.parseAddress(address) ?: return@job tell("\"$address\" is not an address. Use the ip:port the TV shows.")
        update { copy(pairAddress = address) }
        val r = AdbTv.pair(host, port, code, keys)
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

    private fun classifier(): Classifier? = when (settings.ai) {
        Settings.Ai.TERMUX -> TermuxClaude(getApplication(), settings.termuxModel.alias, ::waitOpen)
        Settings.Ai.NANO -> NanoClassifier(::waitOpen)
        Settings.Ai.CLAUDE -> settings.claudeKey.takeIf { it.isNotBlank() }?.let { ClaudeClassifier(it, settings.claudeModel) }
            ?: null.also { log("no Claude API key: only the guard and the lists decide") }
        Settings.Ai.NONE -> null
    }

    /** Backup, then plan. Changes nothing on the TV. */
    fun makePlan() = job("Making a plan") {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        update { copy(log = emptyList(), screen = Screen.PLAN, plan = null) }
        runner.backup(t, info)
        val ai = classifier()
        update { copy(headline = if (ai == null) "Making a plan" else "Asking the AI about the rest") }
        val (plan, dir) = try {
            runner.plan(t, info, ai)
        } finally {
            (ai as? NanoClassifier)?.close()
        }
        // Phases 4 and 5 come from the launcher and screensaver on the TV, not from the package lists.
        val draft = runCatching { homeSetup.draft(t, info, bundledLauncher().second) }
            .onFailure { log("could not read the home screen: ${it.message ?: it.javaClass.simpleName}") }.getOrNull()
        val extra = runCatching { extras.draft(t, info, plan) }
            .onFailure { log("could not read the extras: ${it.message ?: it.javaClass.simpleName}") }.getOrNull()
        // Every phase with something to do starts on; phase 4 also when the TV's logo or colour isn't the company's.
        val home = listOfNotNull(4.takeIf { draft != null && (draft.launcherDue || brandDue(draft)) },
            5.takeIf { draft?.screensaverDue == true }, 6.takeIf { extra != null && ExtrasChoice().due(extra) })
        // The screensaver's weather: if the TV has none yet, use this phone's town (Android asked once, on the form).
        val config = draft?.config?.let { c ->
            if (c.latitude.isNotEmpty() || !PhoneLocation.granted(getApplication())) c
            else PhoneLocation.find(getApplication())?.let { p -> c.copy(place = p.name, latitude = p.latitude, longitude = p.longitude) } ?: c
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

    /** Only called after the user typed GO: the chosen phases, one at a time, each checked before the next. */
    fun apply() = job("Changing the TV") {
        val t = tv ?: return@job
        val plan = state.plan ?: return@job
        val dir = state.runDir ?: return@job
        val draft = state.homeDraft
        val phases = chosenPhases()
        if (phases.isEmpty()) return@job tell("Nothing chosen.")
        val choice = if (phases.any { it in 4..5 }) HomeSetup.Choice(
            (state.homeConfig ?: draft!!.config).copy(brandColor = brand.color), state.keepTvFavourites,
            bundledLauncher().first, withContext(Dispatchers.IO) { brand.logo }) else null
        update {
            copy(screen = Screen.RUN, log = emptyList(), outcome = null, runPhase = null, runDone = emptyList(),
                runSteps = plainSteps(plan, draft, phases, brand.file.isFile, state.extrasDraft?.let { it to state.extras }))
        }
        val o = phaseRun.run(t, plan.info, plan, dir, phases, draft, choice, state.extrasDraft, state.extras) { now, done ->
            update { copy(runPhase = now, runDone = done) }
        }
        o.last.tv?.let { tv = it }
        val doneText = if (o.done.isEmpty()) "No phase finished." else "Phase ${o.done.joinToString(", ")} done and checked."
        update {
            copy(outcome = when {
                o.failed == null -> outcome(o.last, true, "All done",
                    "Phase ${o.done.joinToString(", ")} applied one at a time; the TV restarted and passed its check after each. " +
                        "Undo any time from Restore.")
                o.last.tv == null -> outcome(o.last, false, "The TV didn't come back",
                    "$doneText It stopped during phase ${o.failed}. Check the TV, then undo from Restore.")
                o.failed >= 4 -> outcome(o.last, false, "Phase ${o.failed} didn't check out",
                    "$doneText Phase ${o.failed} was undone, so the TV is as it was before it.")
                else -> outcome(o.last, false, "Home screen didn't come back after phase ${o.failed}",
                    "$doneText Open Restore and undo this run (the newest one).")
            })
        }
    }

    fun openRestore() = job {
        val info = state.info ?: return@job
        val options = withContext(Dispatchers.IO) { storage.restoreOptions(info) }
        update { copy(restoreOptions = options, restoreChoice = null, restorePreview = "", screen = Screen.RESTORE) }
    }

    fun previewRestore(option: RestoreOption) = job {
        val t = tv ?: return@job
        val preview = if (option.kind == RestoreOption.Kind.BACKUP) {
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
        draft.config.logoBytes != brand.file.length()

    fun pickLogo(uri: android.net.Uri) = job {
        val colors = withContext(Dispatchers.IO) { brand.import(uri) }
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

    /** The launcher APK in TVFreedom and its versionCode. */
    private suspend fun bundledLauncher(): Pair<ByteArray, Long> = withContext(Dispatchers.IO) {
        val apk = storage.launcherApk()
        val file = File(getApplication<Application>().cacheDir, "tvhome.apk").apply { writeBytes(apk) }
        val info = getApplication<Application>().packageManager.getPackageArchiveInfo(file.path, 0)
        file.delete()
        apk to (info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L)
    }

    override fun onCleared() {
        tv?.close()
    }
}
