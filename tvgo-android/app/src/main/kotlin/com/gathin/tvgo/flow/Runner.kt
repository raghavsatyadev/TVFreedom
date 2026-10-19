package com.gathin.tvgo.flow

import com.gathin.tvgo.core.Classifier
import com.gathin.tvgo.core.Known
import com.gathin.tvgo.core.ListEntry
import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.Planner
import com.gathin.tvgo.core.Removal
import com.gathin.tvgo.core.TvInfo
import com.gathin.tvgo.store.RestoreOption
import com.gathin.tvgo.store.Storage
import com.gathin.tvgo.tv.AdbKeys
import com.gathin.tvgo.tv.AdbTv
import com.gathin.tvgo.tv.ConnectResult
import com.gathin.tvgo.tv.Discovery
import com.gathin.tvgo.tv.Parse
import com.gathin.tvgo.tv.TvOps
import kotlinx.coroutines.delay
import java.io.File

/**
 * The steps of tvgo.py go / restore / verify, with every TV write logged.
 * The UI owns the confirmations (typed GO / RESTORE); this class never asks and never decides on its own.
 */
class Runner(
    private val storage: Storage,
    private val keys: AdbKeys,
    private val discovery: Discovery,
    private val log: (String) -> Unit,
) {
    suspend fun backup(tv: AdbTv, info: TvInfo): File {
        log("backing up the package lists")
        val dir = storage.writeBackup(info, TvOps(tv, tv.address).backupFiles())
        log("backup saved: ${dir.name}")
        return dir
    }

    /** Read the TV, then guard, lists and AI. Changes nothing on the TV. */
    suspend fun plan(tv: AdbTv, info: TvInfo, classifier: Classifier?): Pair<Plan, File> {
        log("reading packages")
        val facts = TvOps(tv, tv.address).facts()
        log("${facts.paths.size} packages, ${facts.disabled.size} already off")
        val planner = Planner(Known(storage.knownFiles()), storage.keep())
        val plan = planner.build(info, facts, classifier, storage.settings.uninstallUserApps, log)
        val dir = storage.savePlan(plan)
        log("plan: ${plan.remove.size} to remove, ${plan.kept.size} kept, ${plan.alreadyOff.size} already off")
        return plan to dir
    }

    /**
     * Apply the chosen rows (disable-user / uninstall --user 0 only), wait 30 s, reboot and check the home screen.
     * Returns the connection to use afterwards (the port can move after a reboot) and whether the home screen came back.
     */
    suspend fun apply(tv: AdbTv, info: TvInfo, runDir: File, phases: List<Int>, rows: List<Removal>): Pair<AdbTv?, Boolean> {
        storage.writeApplied(runDir, info, phases, rows)
        val ops = TvOps(tv, tv.address)
        rows.forEach { r -> log("${r.action} ${r.pkg}: ${ops.apply(ListEntry(r.action, r.pkg, r.reason)).ifEmpty { "ok" }}") }
        saveWait()
        val (after, ok) = rebootAndCheck(tv, info)
        storage.markVerified(runDir, ok)
        return after to ok
    }

    /** Undo a list (newest line first, like tv.py restore). A run that is undone is no longer learned from. */
    suspend fun restoreList(tv: AdbTv, option: RestoreOption) {
        option.dir?.let { storage.markVerified(it, false) }
        val ops = TvOps(tv, tv.address)
        option.entries.asReversed().forEach { e ->
            val verb = if (e.action == "disable") "enable" else "reinstall"
            log("$verb ${e.pkg}: ${ops.undo(e).ifEmpty { "ok" }}")
        }
        saveWait()
    }

    /** What "everything back to this backup" would do: (to enable, to reinstall). */
    suspend fun diffToBackup(tv: AdbTv, backupDir: File): Pair<List<String>, List<String>> {
        val ops = TvOps(tv, tv.address)
        val thenAll = Parse.backupNames(File(backupDir, "packages-all.txt").readText())
        val thenOff = Parse.names(File(backupDir, "packages-disabled.txt").readText())
        val nowAll = ops.packages()
        val nowOff = ops.packages("-d")
        return ((nowOff - thenOff) intersect thenAll).sorted() to (thenAll - nowAll).sorted()
    }

    suspend fun restoreBackup(tv: AdbTv, info: TvInfo, backupDir: File) {
        storage.unlearnSince(info, backupDir)
        val (enable, reinstall) = diffToBackup(tv, backupDir)
        val ops = TvOps(tv, tv.address)
        reinstall.forEach { log("reinstall $it: ${ops.reinstall(it)}") }
        enable.forEach { log("enable $it: ${ops.enable(it).ifEmpty { "ok" }}") }
        saveWait()
    }

    /** Android saves package state lazily; a reboot within seconds loses it. */
    private suspend fun saveWait() {
        log("waiting 30 s so the TV saves the package state before any reboot")
        delay(30_000)
    }

    /** Reboot, wait for sys.boot_completed, press Home and check the focused window is the HOME package. */
    suspend fun rebootAndCheck(tv: AdbTv, info: TvInfo): Pair<AdbTv?, Boolean> {
        log("rebooting the TV")
        tv.reboot()
        val back = waitBoot(tv, info.serial)
        if (back == null) {
            log("the TV did not come back over adb within 4 min. Check it on screen.")
            return null to false
        }
        val ok = homeOk(back)
        log(if (ok) "home screen OK" else "home screen NOT reached")
        return back to ok
    }

    private suspend fun waitBoot(tv: AdbTv, serial: String): AdbTv? {
        delay(20_000)
        var current = tv
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            if (current.connect(15) == ConnectResult.Ok) {
                val ops = TvOps(current, current.address)
                if (runCatching { ops.getprop("sys.boot_completed") }.getOrNull() == "1") return current
            } else if (current.port != 5555) { // wireless debugging: the port may have changed
                relocate(serial)?.let { current = it; log("TV is now at ${it.address}") }
            }
            delay(10_000)
        }
        current.close()
        return null
    }

    /** After a reboot a wireless-debugging port can change: find the TV with this serial again over mDNS. */
    private suspend fun relocate(serial: String): AdbTv? {
        for (c in discovery.mdns().filter { !it.pairing }) {
            val (host, port) = AdbTv.parseAddress(c.address) ?: continue
            val t = AdbTv(host, port, keys)
            if (t.connect(10) == ConnectResult.Ok && runCatching { TvOps(t, c.address).getprop("ro.serialno") }.getOrNull() == serial)
                return t
            t.close()
        }
        return null
    }

    private suspend fun homeOk(tv: AdbTv): Boolean {
        val ops = TvOps(tv, tv.address)
        ops.key("KEYCODE_WAKEUP")
        delay(2_000)
        ops.key("KEYCODE_HOME")
        delay(6_000)
        val home = ops.homeActivity().substringBefore('/')
        val focus = ops.focusedPackage()
        log("home package ${home.ifEmpty { "?" }}, focused ${focus.ifEmpty { "?" }}")
        return home.isNotEmpty() && focus == home
    }
}
