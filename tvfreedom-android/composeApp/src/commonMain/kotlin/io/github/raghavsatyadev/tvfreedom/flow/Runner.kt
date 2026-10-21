package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.core.Known
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.Planner
import io.github.raghavsatyadev.tvfreedom.core.Removal
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.store.RestoreOption
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.ConnectResult
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Parse
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.TvLink
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import io.github.raghavsatyadev.tvfreedom.tv.Undo
import kotlinx.coroutines.delay
import okio.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How a run that restarts the TV ended. [tv] is the connection to use afterwards (the port can move after a
 * reboot), null if the TV never came back. RAM is MemAvailable in MB; [screen] is a PNG taken after the restart.
 */
data class RunResult(
    val tv: Tv?,
    val ok: Boolean,
    val ramBefore: Int? = null,
    val ramAfter: Int? = null,
    val screen: Path? = null,
)

/**
 * The steps of tvfreedom.py go / restore / verify, with every TV write logged.
 * The UI owns the confirmations (typed GO / RESTORE); this class never asks and never decides on its own.
 */
class Runner(
    private val storage: Storage,
    private val link: TvLink,
    private val log: (String) -> Unit,
    /** A short plain-language name for the step starting now, for the screen. */
    private val step: (String) -> Unit = {},
) {
    /** Put before every step name while a phase-by-phase run is on ("Phase 2 (step 1 of 3): "). */
    var stepPrefix = ""

    /**
     * Name the step on screen and wake the TV, so a TV that dozed off mid-run takes the next command
     * (sh() reconnects first if the connection dropped).
     */
    suspend fun begin(tv: Tv, name: String) {
        step(stepPrefix + name)
        runCatching { tv.sh("input keyevent KEYCODE_WAKEUP") }
    }

    suspend fun backup(tv: Tv, info: TvInfo): Path {
        begin(tv, "Backing up the TV")
        log("backing up the package lists")
        val dir = storage.writeBackup(info, TvOps(tv, tv.address).backupFiles())
        log("backup saved: ${dir.name}")
        return dir
    }

    /** Read the TV, then guard, lists and AI. Changes nothing on the TV. */
    suspend fun plan(tv: Tv, info: TvInfo, classifier: Classifier?): Pair<Plan, Path> {
        begin(tv, "Reading the TV's apps")
        log("reading packages")
        val facts = TvOps(tv, tv.address).facts()
        log("${facts.paths.size} packages, ${facts.disabled.size} already off")
        val planner = Planner(Known(storage.knownFiles()), storage.keep())
        val plan = planner.build(info, facts, classifier, storage.settings.uninstallUserApps, log,
            stopUpdates = storage.settings.stopUpdates)
        val dir = storage.savePlan(plan)
        log("plan: ${plan.remove.size} to remove, ${plan.kept.size} kept, ${plan.alreadyOff.size} already off")
        return plan to dir
    }

    /**
     * Apply [rows] (disable-user / uninstall --user 0 only), wait 30 s, reboot and check the home screen.
     * [applied] is everything this run has applied so far, rows included: applied.txt always lists all of it,
     * so a run applied one phase at a time is undone as one.
     */
    suspend fun apply(tv: Tv, info: TvInfo, runDir: Path, phases: List<Int>, rows: List<Removal>,
                      applied: List<Removal> = rows): RunResult {
        storage.writeApplied(runDir, info, phases, applied)
        val before = freeRam(tv)
        begin(tv, "Changing ${rows.size} apps")
        val ops = TvOps(tv, tv.address)
        rows.forEach { r -> log("${r.action} ${r.pkg}: ${ops.apply(ListEntry(r.action, r.pkg, r.reason)).ifEmpty { "ok" }}") }
        saveWait(tv)
        val result = rebootAndCheck(tv, info, runDir).copy(ramBefore = before)
        storage.markVerified(runDir, result.ok)
        return result
    }

    /**
     * Undo a list (newest line first, like tv.py restore), then put back any setting the run changed.
     * A run that is undone is no longer learned from.
     */
    suspend fun restoreList(tv: Tv, option: RestoreOption) {
        begin(tv, "Restoring ${option.entries.size} apps")
        option.dir?.let { storage.markVerified(it, false) }
        val ops = TvOps(tv, tv.address)
        option.entries.asReversed().forEach { e ->
            val verb = if (e.action == "disable") "enable" else "reinstall"
            log("$verb ${e.pkg}: ${ops.undo(e).ifEmpty { "ok" }}")
        }
        option.dir?.let { storage.savedSettings(it) }?.forEach { (key, value) ->
            if (key != HomeOps.SCREENSAVER) return@forEach
            log("screensaver back to $value: ${HomeOps(tv).setScreensaver(value).ifEmpty { "ok" }}")
        }
        option.dir?.let { dir ->
            Undo.run(tv, storage.undoLines(dir)).forEach(log) // phase 6: app-ops, settings, SmartTube
            if (dir.name.startsWith("extra-")) storage.clearRun(dir) // undone: not offered (or replayed) again
        }
        saveWait(tv)
    }

    /** What "everything back to this backup" would do: (to enable, to reinstall). */
    suspend fun diffToBackup(tv: Tv, backupDir: Path): Pair<List<String>, List<String>> {
        val ops = TvOps(tv, tv.address)
        val thenAll = Parse.backupNames(storage.readFile(backupDir / "packages-all.txt"))
        val thenOff = Parse.names(storage.readFile(backupDir / "packages-disabled.txt"))
        val nowAll = ops.packages()
        val nowOff = ops.packages("-d")
        return ((nowOff - thenOff) intersect thenAll).sorted() to (thenAll - nowAll).sorted()
    }

    suspend fun restoreBackup(tv: Tv, info: TvInfo, backupDir: Path) {
        begin(tv, "Restoring the backup")
        storage.unlearnSince(info, backupDir)
        // The backup only has package lists: phase 6's other changes are put back from their own runs.
        storage.extraRunsSince(info, backupDir).forEach { run ->
            Undo.run(tv, storage.undoLines(run)).forEach(log)
            storage.clearRun(run)
        }
        val (enable, reinstall) = diffToBackup(tv, backupDir)
        val ops = TvOps(tv, tv.address)
        reinstall.forEach { log("reinstall $it: ${ops.reinstall(it)}") }
        enable.forEach { log("enable $it: ${ops.enable(it).ifEmpty { "ok" }}") }
        saveWait(tv)
    }

    /** Android saves package state lazily; a reboot within seconds loses it. */
    suspend fun saveWait(tv: Tv) {
        begin(tv, "Letting the TV save the changes")
        log("waiting 30 s so the TV saves the package state before any reboot")
        delay(30_000)
    }

    suspend fun freeRam(tv: Tv): Int? = runCatching { HomeOps(tv).freeRamMb() }.getOrNull()?.also { log("free RAM $it MB") }

    /**
     * Reboot, wait for sys.boot_completed, press Home and check the focused window is the HOME package
     * (or exactly [expectHome], when a run has just set one). Then read free RAM and, with [shotDir], take a screenshot.
     */
    suspend fun rebootAndCheck(tv: Tv, info: TvInfo, shotDir: Path? = null, expectHome: String? = null): RunResult {
        begin(tv, "Restarting the TV")
        log("rebooting the TV")
        tv.reboot()
        val back = waitBoot(tv, info.serial)
        if (back == null) {
            log("the TV did not come back over adb within 4 min. Check it on screen.")
            return RunResult(null, false)
        }
        begin(back, "Checking the home screen")
        val ok = homeOk(back, expectHome)
        log(if (ok) "home screen OK" else "home screen NOT reached")
        val ram = freeRam(back)
        val shot = shotDir?.let { screenshot(back, it) }
        return RunResult(back, ok, ramAfter = ram, screen = shot)
    }

    /** What the TV shows now, saved as [dir]/screen.png; null if screencap gave no PNG. */
    suspend fun screenshot(tv: Tv, dir: Path): Path? {
        val png = runCatching { HomeOps(tv).screenshot() }.getOrNull() ?: return null.also { log("no screenshot") }
        return storage.writePng(dir / "screen.png", png)
    }

    private suspend fun waitBoot(tv: Tv, serial: String): Tv? {
        delay(20_000)
        var current = tv
        val deadline = TimeSource.Monotonic.markNow() + 240.seconds
        while (deadline.hasNotPassedNow()) {
            if (current.connect(15) == ConnectResult.Ok) {
                val ops = TvOps(current, current.address)
                if (runCatching { ops.getprop("sys.boot_completed") }.getOrNull() == "1") {
                    delay(10_000) // the TV's adbd may restart once more right after boot; let it settle
                    return current
                }
            } else if (current.port != 5555) { // wireless debugging: the port may have changed
                relocate(serial)?.let { current = it; log("TV is now at ${it.address}") }
            }
            delay(10_000)
        }
        current.close()
        return null
    }

    /** After a reboot a wireless-debugging port can change: find the TV with this serial again over mDNS. */
    private suspend fun relocate(serial: String): Tv? {
        for (c in link.mdns().filter { !it.pairing }) {
            val (host, port) = TvLink.parseAddress(c.address) ?: continue
            val t = link.tv(host, port)
            if (t.connect(10) == ConnectResult.Ok && runCatching { TvOps(t, c.address).getprop("ro.serialno") }.getOrNull() == serial)
                return t
            t.close()
        }
        return null
    }

    /**
     * Press Home and compare the focused app with the HOME package. With [expect], both must be exactly that
     * package: a setup screen that took over Home would otherwise match itself.
     */
    suspend fun homeOk(tv: Tv, expect: String? = null): Boolean {
        val ops = TvOps(tv, tv.address)
        ops.key("KEYCODE_WAKEUP")
        delay(2_000)
        ops.key("KEYCODE_HOME")
        delay(6_000)
        val home = ops.homeActivity().substringBefore('/')
        val focus = ops.focusedPackage()
        log("home package ${home.ifEmpty { "?" }}, focused ${focus.ifEmpty { "?" }}")
        return home.isNotEmpty() && focus == home && (expect == null || home == expect)
    }
}
