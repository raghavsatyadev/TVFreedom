package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.core.UNINSTALL
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.ExtraOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Parse
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import io.github.raghavsatyadev.tvfreedom.tv.Undo

/**
 * What putting the TV back the way it came would do. Turned-off apps come from the TV itself, so ones turned off
 * before TVFreedom go back on too; [undo] is every phase 6 change TVFreedom recorded, newest first.
 */
data class RebloatPlan(
    val undo: List<String>,
    /** TVFreedom's settings still set with no run on this phone to undo them (an older install made them). */
    val reset: List<String> = emptyList(),
    val screensaver: String?,
    val enable: List<String>,
    val reinstall: List<String>,
    val launcher: Boolean,
) {
    val isEmpty get() = undo.isEmpty() && reset.isEmpty() && screensaver == null && enable.isEmpty() && reinstall.isEmpty() && !launcher

    fun describe(): String = if (isEmpty) "" else listOfNotNull(
        undo.takeIf { it.isNotEmpty() }?.let { "Undo TVFreedom's other changes:\n" + it.joinToString("\n") { l -> Undo.describe(l) } },
        screensaver?.let { "Screensaver back to ${it.ifEmpty { "the TV's own" }}" },
        enable.takeIf { it.isNotEmpty() }?.let { "Turn back on (${it.size}): ${it.joinToString(", ")}" },
        reinstall.takeIf { it.isNotEmpty() }?.let { "Reinstall (${it.size}): ${it.joinToString(", ")}" },
        "Uninstall the TVFreedom home screen, with its logo and settings".takeIf { launcher },
        "Then restart the TV and check its own home screen opens.",
    ).joinToString("\n\n")
}

/** Everything back to how the TV came: every app on, every TVFreedom change undone, the TVFreedom home gone. */
class Rebloat(private val storage: Storage, private val runner: Runner, private val log: (String) -> Unit) {

    suspend fun plan(tv: Tv, info: TvInfo): RebloatPlan {
        val ops = TvOps(tv, tv.address)
        val home = HomeOps(tv)
        val runs = storage.allRuns(info)
        val launcher = home.launcherVersion() != null
        // The oldest run's saved screensaver is the one the TV came with; with none, only ours has to go.
        val saved = runs.asReversed().firstNotNullOfOrNull { storage.savedSettings(it)[HomeOps.SCREENSAVER] }
        val now = home.screensaver()
        val target = (saved ?: now).let { if (it == "null" || HomeOps.LAUNCHER in it) "" else it }
        val screensaver = target.takeIf { it != now.let { n -> if (n == "null") "" else n } }
        val undo = runs.flatMap { storage.undoLines(it).asReversed() }
        val undone = undo.mapNotNull { l -> l.split(' ').takeIf { it.firstOrNull() == "setting" }?.getOrNull(2) }.toSet()
        val extras = ExtraOps(tv)
        val reset = ExtraOps.SETTINGS.filter { (ns, key, value) -> key !in undone && extras.setting(ns, key) == value }
            .map { (ns, key, _) -> Undo.setting(ns, key, "null") }
        return RebloatPlan(
            undo = undo,
            reset = reset,
            screensaver = screensaver,
            enable = (ops.userDisabled() - HomeOps.LAUNCHER).sorted(),
            reinstall = (ops.uninstalledForUser() intersect knownOnTv(info)).sorted(),
            launcher = launcher,
        )
    }

    /**
     * Packages known to have been on this TV: in a backup, or taken off by a run or a repo list. Android keeps some
     * packages off user 0 on purpose (a private space app, say), so only these are put back.
     */
    private fun knownOnTv(info: TvInfo): Set<String> =
        storage.restoreOptions(info).flatMap { it.entries }.filter { it.action == UNINSTALL }.map { it.pkg }.toSet() +
            storage.backupsFor(info).flatMap { Parse.backupNames(storage.readFile(it / "packages-all.txt")) }

    /** Only called after the user typed RESTORE. Ends with a restart and a home-screen check. */
    suspend fun run(tv: Tv, info: TvInfo): RunResult {
        runner.begin(tv, "Reading what TVFreedom changed")
        val p = plan(tv, info)
        val ops = TvOps(tv, tv.address)
        runner.begin(tv, "Undoing TVFreedom's other changes")
        storage.allRuns(info).forEach { run ->
            Undo.run(tv, storage.undoLines(run)).forEach(log)
            storage.markVerified(run, false) // nothing on the TV is learned from any more
            if (run.name.startsWith("extra-")) storage.clearRun(run)
        }
        Undo.run(tv, p.reset).forEach(log)
        runner.begin(tv, "Turning ${p.reinstall.size + p.enable.size} apps back on")
        // Stock home screens first, so the TV has its own one before the TVFreedom home goes.
        p.reinstall.forEach { log("reinstall $it: ${ops.reinstall(it)}") }
        p.enable.forEach { log("enable $it: ${ops.enable(it).ifEmpty { "ok" }}") }
        if (p.launcher) {
            runner.begin(tv, "Removing the TVFreedom home screen")
            log("uninstall ${HomeOps.LAUNCHER}: ${ExtraOps(tv).uninstall(HomeOps.LAUNCHER)}")
        }
        p.screensaver?.let { log("screensaver back to ${it.ifEmpty { "default" }}: ${HomeOps(tv).setScreensaver(it).ifEmpty { "ok" }}") }
        runner.saveWait(tv)
        return runner.rebootAndCheck(tv, info, storage.shotDir())
    }
}
