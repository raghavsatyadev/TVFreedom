package com.gathin.tvgo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gathin.tvgo.TvGoApp
import com.gathin.tvgo.ai.ClaudeClassifier
import com.gathin.tvgo.ai.NanoClassifier
import com.gathin.tvgo.core.Classifier
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.TvInfo
import com.gathin.tvgo.flow.Runner
import com.gathin.tvgo.store.RestoreOption
import com.gathin.tvgo.store.Settings
import com.gathin.tvgo.tv.AdbKeys
import com.gathin.tvgo.tv.AdbTv
import com.gathin.tvgo.tv.Candidate
import com.gathin.tvgo.tv.ConnectResult
import com.gathin.tvgo.tv.Discovery
import com.gathin.tvgo.tv.TvOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { SETUP, CONNECT, TV, PLAN, RUN, RESTORE, SETTINGS }

data class UiState(
    val screen: Screen = Screen.SETUP,
    val busy: Boolean = false,
    val status: String = "",
    val log: List<String> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    val info: TvInfo? = null,
    val plan: Plan? = null,
    val runDir: File? = null,
    val phases: Set<Int> = emptySet(),
    val result: String = "",
    val restoreOptions: List<RestoreOption> = emptyList(),
    val restoreChoice: RestoreOption? = null,
    val restorePreview: String = "",
    val aiStatus: String = "",
)

class TvGoViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = (app as TvGoApp).storage
    val settings: Settings = storage.settings
    private val discovery = Discovery(app)
    private val keys by lazy { AdbKeys(storage.keysDir) }
    private var tv: AdbTv? = null

    var state by mutableStateOf(UiState(screen = if (settings.onboarded) Screen.CONNECT else Screen.SETUP))
        private set

    private fun update(f: UiState.() -> UiState) {
        state = state.f()
    }

    private fun log(line: String) = update { copy(log = (log + line).takeLast(400), status = line) }

    private val runner by lazy { Runner(storage, keys, discovery, ::log) }

    /** One job at a time: nothing starts while another step runs. */
    private fun job(block: suspend () -> Unit) {
        if (state.busy) return
        update { copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                log("error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                update { copy(busy = false) }
            }
        }
    }

    fun go(screen: Screen) = update { copy(screen = screen) }

    fun finishSetup() {
        settings.onboarded = true
        go(if (tv?.connected == true) Screen.TV else Screen.CONNECT)
    }

    fun checkAi() = job {
        val status = when (settings.ai) {
            Settings.Ai.NANO -> NanoClassifier().let { n -> try { n.prepare(::log) } finally { n.close() } }
            Settings.Ai.CLAUDE -> if (settings.claudeKey.isBlank()) "add a Claude API key" else "key saved"
            Settings.Ai.NONE -> "no AI: only the guard and the lists decide"
        }
        update { copy(aiStatus = status) }
    }

    fun discover() = job {
        update { copy(candidates = emptyList(), log = emptyList()) }
        if (discovery.localIp() == null) log("no Wi-Fi address: type the TV's ip:port instead")
        log("looking for TVs (mDNS and port 5555)")
        val found = discovery.find()
        update { copy(candidates = found) }
        log(if (found.isEmpty()) "nothing found: check the TV's network debugging, or type its ip:port" else "found ${found.size}")
    }

    fun connect(address: String) = job {
        val (host, port) = AdbTv.parseAddress(address) ?: return@job log("not an address: $address")
        tv?.close()
        val t = AdbTv(host, port, keys)
        log("connecting to ${t.address}: if the TV asks \"Allow debugging?\", tick Always allow and press OK")
        when (val r = t.connect()) {
            ConnectResult.Ok -> Unit
            ConnectResult.PairingRequired -> return@job log("this TV needs pairing first: use Pair with the code it shows")
            is ConnectResult.Failed -> return@job log("could not connect: ${r.why}")
        }
        val ops = TvOps(t, t.address)
        if (!ops.isTv()) {
            t.close()
            return@job log("${t.address} is not a TV, skipped")
        }
        ops.key("KEYCODE_WAKEUP")
        val info = ops.identify()
        tv = t
        settings.lastAddress = t.address
        log("TV: ${info.brand} ${info.model} (${info.device}), Android ${info.android}")
        update { copy(info = info, plan = null, screen = Screen.TV) }
    }

    fun pair(address: String, code: String) = job {
        val (host, port) = AdbTv.parseAddress(address) ?: return@job log("not an address: $address")
        log("pairing with $host:$port")
        log(AdbTv.pair(host, port, code, keys) + ": now connect to the TV's wireless debugging ip:port")
    }

    fun disconnect() {
        tv?.close()
        tv = null
        update { copy(info = null, plan = null, screen = Screen.CONNECT) }
    }

    private fun classifier(): Classifier? = when (settings.ai) {
        Settings.Ai.NANO -> NanoClassifier()
        Settings.Ai.CLAUDE -> settings.claudeKey.takeIf { it.isNotBlank() }?.let { ClaudeClassifier(it, settings.claudeModel) }
            ?: null.also { log("no Claude API key: only the guard and the lists decide") }
        Settings.Ai.NONE -> null
    }

    /** Backup, then plan. Changes nothing on the TV. */
    fun makePlan() = job {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        update { copy(log = emptyList(), screen = Screen.PLAN, plan = null) }
        runner.backup(t, info)
        val ai = classifier()
        val (plan, dir) = try {
            runner.plan(t, info, ai)
        } finally {
            (ai as? NanoClassifier)?.close()
        }
        update { copy(plan = plan, runDir = dir, phases = plan.phases.toSet()) }
    }

    fun togglePhase(p: Int) = update { copy(phases = if (p in phases) phases - p else phases + p) }

    /** Only called after the user typed GO on the confirmation screen. */
    fun apply() = job {
        val t = tv ?: return@job
        val plan = state.plan ?: return@job
        val dir = state.runDir ?: return@job
        val phases = state.phases.sorted()
        val rows = plan.remove.filter { it.phase in phases }
        if (rows.isEmpty()) return@job log("nothing chosen")
        update { copy(screen = Screen.RUN, log = emptyList(), result = "") }
        val (after, ok) = runner.apply(t, plan.info, dir, phases, rows)
        after?.let { tv = it }
        update {
            copy(result = if (ok) "Done. The home screen came back. Undo any time from Restore."
            else "Home screen check failed. Open Restore and undo this run (the newest one).")
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
            "enable ${enable.size}: ${enable.joinToString(" ").ifEmpty { "-" }}\n" +
                "reinstall ${reinstall.size}: ${reinstall.joinToString(" ").ifEmpty { "-" }}\n" +
                "(only undoes changes made after this backup)"
        } else option.entries.joinToString("\n") { "${if (it.action == "disable") "enable" else "reinstall"} ${it.pkg}" }
        update { copy(restoreChoice = option, restorePreview = preview) }
    }

    /** Only called after the user typed RESTORE. */
    fun restore() = job {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        val option = state.restoreChoice ?: return@job
        update { copy(screen = Screen.RUN, log = emptyList(), result = "") }
        if (option.kind == RestoreOption.Kind.BACKUP) runner.restoreBackup(t, info, option.dir!!) else runner.restoreList(t, option)
        update { copy(result = "Restored. Reboot and check the home screen if you like.") }
    }

    fun rebootAndCheck() = job {
        val t = tv ?: return@job
        val info = state.info ?: return@job
        update { copy(screen = Screen.RUN, result = "") }
        val (after, ok) = runner.rebootAndCheck(t, info)
        after?.let { tv = it }
        update { copy(result = if (ok) "Home screen OK." else "Home screen NOT reached. Check the TV.") }
    }

    override fun onCleared() {
        tv?.close()
    }
}
