package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.DISABLE
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.ExtraOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import io.github.raghavsatyadev.tvfreedom.tv.Undo

/** What undoing some changes left: the picked ones still in effect, and whether the home screen was kept on purpose. */
data class ChangeUndo(val left: List<Change>, val keptHome: Boolean)

/** Restore's "By change" tab: every single change still in effect on the TV, and undoing just the picked ones. */
class ChangeRestore(private val storage: Storage, private val runner: Runner, private val log: (String) -> Unit) {

    /** The changes from every run that the TV still shows, newest run first; the home-screen item first. */
    suspend fun list(tv: Tv, info: TvInfo): List<ChangeGroup> {
        val ops = TvOps(tv, tv.address)
        val extra = ExtraOps(tv)
        val home = HomeOps(tv)
        val off = ops.userDisabled()
        val present = ops.packages()
        val screensaver = home.screensaver()
        suspend fun on(line: String): Boolean {
            val p = line.trim().split(' ')
            return when (p[0]) {
                "appop" -> p.size == 4 && !ChangeList.same(extra.appop(p[1], p[2]), p[3])
                "setting" -> p.size == 4 && !ChangeList.same(extra.setting(p[1], p[2]), p[3])
                "installed" -> p.size == 2 && p[1] in present
                else -> true // favourites: the launcher can't be asked cheaply; always offered
            }
        }
        suspend fun inEffect(c: Change): Boolean = when (c.kind) {
            ChangeKind.APP -> c.entry!!.let { if (it.action == DISABLE) it.pkg in off else it.pkg !in present }
            ChangeKind.EXTRA -> c.lines.any { runCatching { on(it) }.getOrDefault(true) }
            ChangeKind.SCREENSAVER -> !ChangeList.same(screensaver, c.lines.first().trim().split(' ')[2])
            ChangeKind.HOME -> true
        }
        val groups = ChangeList.build(storage.runRecords(info)).mapNotNull { g ->
            g.changes.filter { inEffect(it) }.takeIf { it.isNotEmpty() }?.let { g.copy(changes = it) }
        }
        val launcher = if (home.launcherVersion() != null) listOf(ChangeGroup("TVFreedom home screen", listOf(
            Change(HOME_ID, null, ChangeKind.HOME, "Remove the TVFreedom home screen",
                "Only if the TV's own home screen is on", emptyList())))) else emptyList()
        return launcher + groups
    }

    /** Only called after the user typed RESTORE. Undoes the [picked] changes, then keeps the run files in step. */
    suspend fun undo(tv: Tv, info: TvInfo, picked: Set<String>): ChangeUndo {
        runner.begin(tv, "Reading what is still changed")
        val chosen = list(tv, info).flatMap { it.changes }.filter { it.id in picked }
        val ops = TvOps(tv, tv.address)
        val home = HomeOps(tv)
        runner.begin(tv, "Undoing ${chosen.size} changes")
        // Newest run first; in a run its extras, then its apps, then its screensaver, each newest line first.
        chosen.filter { it.run != null }.groupBy { it.run!!.dir }.values.forEach { inRun ->
            Undo.run(tv, inRun.filter { it.kind == ChangeKind.EXTRA }.flatMap { it.lines }).forEach(log)
            inRun.filter { it.kind == ChangeKind.APP }.asReversed().forEach { c ->
                val e = c.entry!!
                log("${if (e.action == DISABLE) "enable" else "reinstall"} ${e.pkg}: ${ops.undo(e).ifEmpty { "ok" }}")
            }
            inRun.filter { it.kind == ChangeKind.SCREENSAVER }.forEach { c ->
                val old = c.lines.first().trim().split(' ')[2]
                log("screensaver back to $old: ${home.setScreensaver(old).ifEmpty { "ok" }}")
            }
        }
        var keptHome = false
        if (chosen.any { it.kind == ChangeKind.HOME }) {
            // Never leave the TV without a home screen: a stock one must be on before ours goes.
            // Settings' FallbackHome is only a stopgap screen, not a home screen.
            val stock = ops.homePackages() - ops.userDisabled() - HomeOps.LAUNCHER - FALLBACK_HOMES
            if (stock.isEmpty()) {
                keptHome = true
                log("kept the TVFreedom home screen: the TV's own home screen is off")
            } else {
                runner.begin(tv, "Removing the TVFreedom home screen")
                if (HomeOps.LAUNCHER in home.screensaver()) log("screensaver back to the TV's own: ${home.setScreensaver("").ifEmpty { "ok" }}")
                log("uninstall ${HomeOps.LAUNCHER}: ${ExtraOps(tv).uninstall(HomeOps.LAUNCHER)}")
            }
        }
        runner.saveWait(tv)
        val still = list(tv, info).flatMap { it.changes }.map { it.id }.toSet()
        // Favourites can't be read back, so once put back they count as done.
        val (left, done) = chosen.partition { it.id in still && !it.lines.all { l -> l.startsWith("favourites ") } }
        forget(info, done.filter { it.run != null })
        return ChangeUndo(left, keptHome)
    }

    /** Drop what really got undone from the run files; an app put back is dropped from every run that lists it. */
    private fun forget(info: TvInfo, done: List<Change>) {
        if (done.isEmpty()) return
        val apps = done.mapNotNull { c -> c.entry?.let { it.action to it.pkg } }.toSet()
        storage.runRecords(info).forEach { r ->
            val mine = done.filter { it.run?.dir == r.dir && it.kind != ChangeKind.APP }
            val applied = ChangeList.withoutApps(r.applied, apps)
            if (mine.isEmpty() && applied == r.applied) return@forEach
            storage.rewriteRun(r.copy(
                applied = applied,
                extras = ChangeList.without(r.extras, mine.filter { it.kind == ChangeKind.EXTRA }.flatMap { it.lines }),
                settings = ChangeList.without(r.settings, mine.filter { it.kind == ChangeKind.SCREENSAVER }.flatMap { it.lines }),
            ))
            storage.markVerified(r.dir, false) // part of it is undone: not learned from any more
        }
    }

    companion object {
        const val HOME_ID = "home"
        private val FALLBACK_HOMES = setOf("com.android.settings", "com.android.tv.settings")
    }
}
