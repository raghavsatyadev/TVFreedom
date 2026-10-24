package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.Removal
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import okio.Path

/**
 * How a run ended: the restart's result (RAM from before the first phase), the phases that were made, and the step
 * that stopped it: a phase, or [PhaseRun.RESTART] when the TV failed its check after the restart.
 */
data class PhaseOutcome(val last: RunResult, val done: List<Int>, val failed: Int?)

/** A phase's changes, made but not restarted yet: its run [dir], what to [check] after the restart and how to [undo] it. */
class Pending(val dir: Path, val check: suspend (Tv) -> Boolean = { true }, val undo: (suspend (Tv) -> Unit)? = null)

/**
 * A debloat: every chosen phase changes the TV, then one wait, one restart and one check of the home screen at the
 * end. If a phase fails or the check does, phases 4 to 6 (home screen, screensaver, extras) are undone at once;
 * phases 1 to 3 are undone from Restore.
 */
class PhaseRun(private val runner: Runner, private val home: HomeSetup, private val extras: Extras, private val log: (String) -> Unit) {

    suspend fun run(tv: Tv, info: TvInfo, plan: Plan, runDir: Path, phases: List<Int>,
                    draft: HomeDraft?, choice: HomeSetup.Choice?,
                    extrasDraft: ExtrasDraft? = null, extrasChoice: ExtrasChoice? = null,
                    progress: (now: Int?, done: List<Int>) -> Unit = { _, _ -> }): PhaseOutcome {
        home.reset()
        val before = runner.freeRam(tv)
        // The check expects the new launcher if phase 4 makes it, or phase 5 runs on a TV that already has it.
        val newHome = 4 in phases || draft?.currentHome?.startsWith(HomeOps.LAUNCHER) == true
        val applied = mutableListOf<Removal>()
        val done = mutableListOf<Int>()
        val pending = mutableListOf<Pending>()
        try {
            for ((i, p) in phases.withIndex()) {
                runner.stepPrefix = "Phase $p (step ${i + 1} of ${phases.size}): "
                log("--- phase $p ---")
                progress(p, done.toList())
                pending += when (p) {
                    4 -> home.launcherPhase(tv, info, requireNotNull(draft), requireNotNull(choice))
                    5 -> home.screensaverPhase(tv, info, requireNotNull(draft), requireNotNull(choice))
                    6 -> extras.phase(tv, info, requireNotNull(extrasDraft), requireNotNull(extrasChoice))
                    else -> {
                        val rows = plan.remove.filter { it.phase == p }
                        applied += rows
                        runner.apply(tv, info, runDir, done + p, rows, applied.toList())
                    }
                }
                done += p
            }
        } catch (e: Exception) {
            log("stopped before the restart: ${e.message}")
            pending.map { it.dir }.distinct().forEach { runner.verified(it, false) }
            undo(tv, pending)
            // A failed phase 4 to 6 undid itself: let the TV save that too.
            if (pending.none { it.undo != null } && phases.getOrNull(done.size)?.let { it >= 4 } == true) runCatching { runner.saveWait(tv) }
            runner.stepPrefix = ""
            throw e
        }
        try {
            runner.stepPrefix = "Last step: "
            log("--- restart and check ---")
            progress(RESTART, done.toList())
            runner.saveWait(tv)
            val expect = HomeOps.LAUNCHER.takeIf { newHome && (4 in phases || 5 in phases) }
            val result = runner.rebootAndCheck(tv, info, runDir, expectHome = expect)
            val back = result.tv
            var ok = result.ok
            if (back != null) for (p in pending) if (!p.check(back)) ok = false
            log(if (ok) "everything checked" else "the check after the restart failed")
            pending.map { it.dir }.distinct().forEach { runner.verified(it, ok) }
            if (back != null && !ok) undo(back, pending)
            if (ok) progress(null, done + RESTART)
            return PhaseOutcome(result.copy(ok = ok, ramBefore = before), done, if (ok) null else RESTART)
        } finally {
            runner.stepPrefix = ""
        }
    }

    /** Undo phases 6, 5 and 4 (newest first); phases 1 to 3 have no undo here. */
    private suspend fun undo(tv: Tv, pending: List<Pending>) {
        val undos = pending.asReversed().mapNotNull { it.undo }
        undos.forEach { u -> runCatching { u(tv) }.onFailure { log("undo failed: ${it.message}") } }
        if (undos.isNotEmpty()) runCatching { runner.saveWait(tv) }
    }

    companion object {
        /** The last step of every run: one restart and one check. Not a phase number. */
        const val RESTART = 99
    }
}
