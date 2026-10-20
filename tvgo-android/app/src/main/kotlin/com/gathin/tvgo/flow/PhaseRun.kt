package com.gathin.tvgo.flow

import com.gathin.tvgo.core.Plan
import com.gathin.tvgo.core.Removal
import com.gathin.tvgo.core.TvInfo
import com.gathin.tvgo.tv.AdbTv
import com.gathin.tvgo.tv.HomeOps
import java.io.File

/** How a phase-by-phase run ended: the last step's result (RAM from before the first) and the phases that checked out. */
data class PhaseOutcome(val last: RunResult, val done: List<Int>, val failed: Int?)

/**
 * A debloat applied one phase at a time, like the PC runs were: each phase changes the TV, waits, restarts it and
 * checks the home screen before the next one starts. The first phase that fails stops the run; phases 4 to 6
 * (home screen, screensaver, extras) undo themselves when their check fails.
 */
class PhaseRun(private val runner: Runner, private val home: HomeSetup, private val extras: Extras, private val log: (String) -> Unit) {

    suspend fun run(tv: AdbTv, info: TvInfo, plan: Plan, runDir: File, phases: List<Int>,
                    draft: HomeDraft?, choice: HomeSetup.Choice?,
                    extrasDraft: ExtrasDraft? = null, extrasChoice: ExtrasChoice? = null,
                    progress: (now: Int?, done: List<Int>) -> Unit = { _, _ -> }): PhaseOutcome {
        home.reset()
        val before = runner.freeRam(tv)
        // Phase 5 checks Home too: it must still be the new launcher if phase 4 made it, or already was.
        val newHome = 4 in phases || draft?.currentHome?.startsWith(HomeOps.LAUNCHER) == true
        var current = tv
        val applied = mutableListOf<Removal>()
        val done = mutableListOf<Int>()
        var last = RunResult(tv, true)
        try {
            for ((i, p) in phases.withIndex()) {
                runner.stepPrefix = "Phase $p (step ${i + 1} of ${phases.size}): "
                log("--- phase $p ---")
                progress(p, done.toList())
                last = when (p) {
                    4 -> home.launcherPhase(current, info, requireNotNull(draft), requireNotNull(choice))
                    5 -> home.screensaverPhase(current, info, requireNotNull(draft), requireNotNull(choice), newHome)
                    6 -> extras.phase(current, info, requireNotNull(extrasDraft), requireNotNull(extrasChoice))
                    else -> {
                        val rows = plan.remove.filter { it.phase == p }
                        applied += rows
                        runner.apply(current, info, runDir, done + p, rows, applied.toList())
                    }
                }
                log("phase $p ${if (last.ok) "checked" else "failed"}")
                last.tv?.let { current = it }
                if (!last.ok || last.tv == null) return PhaseOutcome(last.copy(ramBefore = before), done, p)
                done += p
            }
            progress(null, done.toList())
        } finally {
            runner.stepPrefix = ""
        }
        return PhaseOutcome(last.copy(ramBefore = before), done, null)
    }
}
