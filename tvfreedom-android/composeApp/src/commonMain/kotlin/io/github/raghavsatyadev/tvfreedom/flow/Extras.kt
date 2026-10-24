package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.DISABLE
import io.github.raghavsatyadev.tvfreedom.core.Guard
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.Removal
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.ExtraOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Parse
import io.github.raghavsatyadev.tvfreedom.tv.hostname
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import io.github.raghavsatyadev.tvfreedom.tv.Undo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.raghavsatyadev.tvfreedom.store.Net
import kotlinx.coroutines.IO
import okio.ByteString.Companion.toByteString
import okio.Path

/** What phase 6 finds on the TV, read with the plan. Values are the TV's now. */
data class ExtrasDraft(
    val sdk: Int,
    /** Apps that stay and may run in the background now (lockdown candidates), with their mode. */
    val lockApps: Map<String, String>,
    /** key to value now, for each of [ExtraOps.SETTINGS]. */
    val settings: Map<String, String>,
    val dnsMode: String,
    val dnsHost: String,
    val youtubeOn: Boolean,
    val smartTube: Boolean,
) {
    val privateDns get() = sdk >= 28
}

/** What the user switched on for phase 6. Settings start on; lockdown, DNS and SmartTube start off. */
data class ExtrasChoice(
    val lockdown: Boolean = false,
    val lockSkip: Set<String> = emptySet(),
    val settings: Boolean = true,
    val dns: Boolean = false,
    val dnsHost: String = ExtraOps.DNS_HOST,
    val smartTube: Boolean = false,
    /** Keep YouTube on next to SmartTube instead of turning it off. */
    val keepYoutube: Boolean = false,
) {
    fun lockTargets(d: ExtrasDraft) = if (!lockdown) emptyList() else d.lockApps.filter { it.key !in lockSkip && it.value != "ignore" }.keys.sorted()
    fun settingsDue(d: ExtrasDraft) = settings && ExtraOps.SETTINGS.any { (_, k, v) -> d.settings[k] != v }
    fun dnsDue(d: ExtrasDraft) = dns && d.privateDns && runCatching { Parse.hostname(dnsHost) }.isSuccess &&
        !(d.dnsMode == "hostname" && d.dnsHost == dnsHost)
    fun tubeDue(d: ExtrasDraft) = smartTube && (!d.smartTube || (d.youtubeOn && !keepYoutube))
    fun due(d: ExtrasDraft) = lockTargets(d).isNotEmpty() || settingsDue(d) || dnsDue(d) || tubeDue(d)
}

/**
 * Phase 6, after dumbtv: stop kept apps running in the background, a few settings, Private DNS, SmartTube for
 * YouTube. Every change is written to the run's extras.txt (applied.txt for YouTube) before it is made, and a failed
 * check after the run's restart puts everything back.
 */
class Extras(private val storage: Storage, private val net: Net, private val runner: Runner, private val log: (String) -> Unit) {

    suspend fun draft(tv: Tv, info: TvInfo, plan: Plan): ExtrasDraft {
        val ops = ExtraOps(tv)
        val sdk = info.sdk.toIntOrNull() ?: 0
        val homes = TvOps(tv, tv.address).homePackages()
        val op = ops.backgroundOp(sdk)
        val removed = plan.remove.map { it.pkg }.toSet()
        // Guarded apps (keyboards, VPNs, cast, voice, home) keep their background; the keep list only stops removal.
        val stay = plan.userApps.filter { it !in removed && it !in plan.alreadyOff && it != HomeOps.LAUNCHER &&
            Guard.keepReason(it, home = homes, ime = info.ime).isEmpty() }
        val lock = stay.associateWith { ops.appop(it, op) }
        val settings = ExtraOps.SETTINGS.associate { (ns, k, _) -> k to ops.setting(ns, k) }
        val d = ExtrasDraft(sdk, lock, settings, ops.setting("global", "private_dns_mode"), ops.setting("global", "private_dns_specifier"),
            ops.enabled(ExtraOps.YOUTUBE), ops.present(ExtraOps.SMARTTUBE))
        log("extras: ${lock.count { it.value != "ignore" }} apps may run in the background, settings $settings, " +
            "private DNS ${d.dnsMode}/${d.dnsHost}, YouTube ${if (d.youtubeOn) "on" else "off"}, SmartTube ${if (d.smartTube) "installed" else "not installed"}")
        return d
    }

    suspend fun phase(tv: Tv, info: TvInfo, d: ExtrasDraft, c: ExtrasChoice): Pending {
        val tube = c.tubeDue(d)
        // Fetch SmartTube before anything changes: a failed download then leaves the TV as it was.
        val apk = if (tube && !d.smartTube) download(tv) else null
        val rows = if (tube && d.youtubeOn && !c.keepYoutube) listOf(Removal(ExtraOps.YOUTUBE, DISABLE, 6, "SmartTube takes its place", "extras")) else emptyList()
        val dir = storage.writeExtraRun(info, rows)
        log("phase 6 saved: ${dir.name}")
        val ops = ExtraOps(tv)
        val dnsSet = try {
            lockdown(tv, ops, dir, d, c)
            if (c.settingsDue(d)) settings(tv, ops, dir, d)
            val dns = c.dnsDue(d) && privateDns(tv, ops, dir, c.dnsHost)
            if (tube) smartTube(tv, ops, dir, rows, apk, c.keepYoutube)
            dns
        } catch (e: Exception) {
            rollBack(tv, dir, rows)
            throw e
        }
        val check: suspend (Tv) -> Boolean = { back ->
            val after = ExtraOps(back)
            if (c.settingsDue(d)) log("after the restart: " + ExtraOps.SETTINGS.map { (ns, k, _) -> "$k=${after.setting(ns, k)}" }.joinToString())
            c.lockTargets(d).firstOrNull()?.let { log("$it in the background after the restart: ${after.appop(it, after.backgroundOp(d.sdk))}") }
            val names = !dnsSet || after.resolves()
            if (!names) log("the TV can't look names up through ${c.dnsHost} after the restart")
            names
        }
        return Pending(dir, check) { rollBack(it, dir, rows) }
    }

    private suspend fun lockdown(tv: Tv, ops: ExtraOps, dir: Path, d: ExtrasDraft, c: ExtrasChoice) {
        val targets = c.lockTargets(d)
        if (targets.isEmpty()) return
        runner.begin(tv, "Stopping ${targets.size} apps running in the background")
        val op = ops.backgroundOp(d.sdk)
        for (pkg in targets) {
            val old = ops.appop(pkg, op)
            if (old == "ignore") continue
            storage.addUndo(dir, Undo.appop(pkg, op, old))
            log("$pkg $op ignore (was $old): ${ops.setAppop(pkg, op, "ignore").ifEmpty { "ok" }}")
            ops.forceStop(pkg)
        }
    }

    private suspend fun settings(tv: Tv, ops: ExtraOps, dir: Path, d: ExtrasDraft) {
        runner.begin(tv, "Changing ${ExtraOps.SETTINGS.size} settings")
        for ((ns, key, value) in ExtraOps.SETTINGS) {
            val old = ops.setting(ns, key)
            if (old == value) continue
            storage.addUndo(dir, Undo.setting(ns, key, old))
            log("$key $value (was $old): ${ops.putSetting(ns, key, value).ifEmpty { "ok" }}")
        }
    }

    /** Turn on Private DNS and look a name up through it; if that fails, it goes straight back. True if it stayed on. */
    private suspend fun privateDns(tv: Tv, ops: ExtraOps, dir: Path, host: String): Boolean {
        runner.begin(tv, "Turning on Private DNS ($host)")
        val oldMode = ops.setting("global", "private_dns_mode")
        val oldHost = ops.setting("global", "private_dns_specifier")
        storage.addUndo(dir, Undo.setting("global", "private_dns_specifier", oldHost))
        storage.addUndo(dir, Undo.setting("global", "private_dns_mode", oldMode))
        ops.putSetting("global", "private_dns_specifier", host)
        ops.putSetting("global", "private_dns_mode", "hostname")
        delay(8_000)
        if (ops.resolves()) return true.also { log("private DNS $host: names resolve") }
        log("private DNS $host did not answer, so it was turned off again: ${ops.putSetting("global", "private_dns_mode", oldMode).ifEmpty { "ok" }}")
        ops.putSetting("global", "private_dns_specifier", oldHost)
        return false
    }

    private suspend fun smartTube(tv: Tv, ops: ExtraOps, dir: Path, rows: List<Removal>, apk: ByteArray?, keepYoutube: Boolean) {
        if (!ops.present(ExtraOps.SMARTTUBE)) {
            requireNotNull(apk) { "SmartTube was not downloaded" }
            runner.begin(tv, "Installing SmartTube")
            storage.addUndo(dir, Undo.installed(ExtraOps.SMARTTUBE))
            val out = HomeOps(tv).install(apk)
            log("install SmartTube: $out")
            if (out.isEmpty() || "VERIFICATION" in out) error("the TV blocked SmartTube (Google Play Protect). Close the message on the TV; YouTube stays")
            if ("Success" !in out) error("the TV did not install SmartTube ($out); YouTube stays")
        }
        ops.open(ExtraOps.SMARTTUBE)
        delay(6_000)
        val focus = TvOps(tv, tv.address).focusedPackage()
        TvOps(tv, tv.address).key("KEYCODE_HOME")
        if (focus != ExtraOps.SMARTTUBE) error("SmartTube did not open (the TV shows $focus), so YouTube stays")
        log("SmartTube opens")
        val t = TvOps(tv, tv.address)
        rows.forEach { r -> log("disable ${r.pkg}: ${t.apply(ListEntry(DISABLE, r.pkg, r.reason)).ifEmpty { "ok" }}") }
        if (keepYoutube) return // YouTube stays, so its home-screen place does too
        val home = HomeOps(tv)
        val now = home.config() ?: return
        if (ExtraOps.YOUTUBE !in now.favourites) return
        storage.addUndo(dir, Undo.favourites(now.favourites.joinToString(",")))
        home.config(now.copy(favourites = now.favourites.map { if (it == ExtraOps.YOUTUBE) ExtraOps.SMARTTUBE else it }.distinct()))
        log("favourites: SmartTube in YouTube's place")
    }

    /** dumbtv's pinned SmartTube build, kept on the phone once it checks out. */
    private suspend fun download(tv: Tv): ByteArray = withContext(Dispatchers.IO) {
        runner.begin(tv, "Downloading SmartTube (${ExtraOps.SMARTTUBE_MB} MB)")
        val name = "smarttube-${ExtraOps.SMARTTUBE_SHA256.take(12)}.apk"
        storage.readCache(name)?.takeIf { sha256(it) == ExtraOps.SMARTTUBE_SHA256 }?.let { return@withContext it }
        val bytes = net.get(ExtraOps.SMARTTUBE_URL)
        if (bytes.size != ExtraOps.SMARTTUBE_BYTES) error("the SmartTube download is ${bytes.size} bytes, not ${ExtraOps.SMARTTUBE_BYTES}, so it wasn't installed")
        val got = sha256(bytes)
        if (got != ExtraOps.SMARTTUBE_SHA256) error("the SmartTube download doesn't match its pinned checksum ($got), so it wasn't installed")
        storage.writeCache(name, bytes)
        log("SmartTube downloaded, checksum ok")
        bytes
    }

    private fun sha256(b: ByteArray) = b.toByteString().sha256().hex()

    /** Put back everything this run changed: its undo lines, then YouTube. */
    private suspend fun rollBack(tv: Tv, dir: Path, rows: List<Removal>) {
        runner.begin(tv, "Undoing phase 6")
        Undo.run(tv, storage.undoLines(dir)).forEach(log)
        val ops = TvOps(tv, tv.address)
        rows.forEach { log("enable ${it.pkg}: ${ops.enable(it.pkg).ifEmpty { "ok" }}") }
        storage.markVerified(dir, false)
        storage.clearRun(dir) // nothing left to undo: keep it off the Restore list
    }
}
