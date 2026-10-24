package io.github.raghavsatyadev.tvfreedom.ui

import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.Defaults
import io.github.raghavsatyadev.tvfreedom.design.Design
import io.github.raghavsatyadev.tvfreedom.flow.DesignFlow
import io.github.raghavsatyadev.tvfreedom.store.Settings
import io.github.raghavsatyadev.tvfreedom.store.Take
import io.github.raghavsatyadev.tvfreedom.store.stampNow
import io.github.raghavsatyadev.tvfreedom.tv.DesignOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

// The designs screen's steps. Each one that talks to the TV is a named job, so TVFreedom stays alive if the user leaves.

private fun TvFreedomViewModel.flow() = DesignFlow(storage, ::log)

private fun TvFreedomViewModel.setDesigns(f: DesignsUi.() -> DesignsUi) = update { copy(designs = designs.f()) }

/** The AI picked in Settings can write designs: only Claude can (in Termux, or with an API key). */
fun TvFreedomViewModel.aiWritesDesigns() =
    settings.ai == Settings.Ai.TERMUX || settings.ai == Settings.Ai.CLAUDE && settings.claudeKey.isNotBlank()

/** Read the TV's apps for the ready-made designs. Changes nothing on the TV. */
fun TvFreedomViewModel.openDesigns() = job("Reading the TV's apps") {
    val t = tv ?: return@job
    val info = state.info ?: return@job
    update { copy(screen = Screen.DESIGNS, designs = DesignsUi(takes = storage.designs.takes(info))) }
    val home = HomeOps(t)
    val apps = home.tvApps().filter { it != DesignOps.SHELL && it != HomeOps.LAUNCHER }
    val favourites = runCatching { home.config()?.favourites }.getOrNull().orEmpty()
    setDesigns { copy(apps = (favourites.filter { it in apps } + apps).distinct(), ready = Defaults.all(apps, favourites)) }
}

fun TvFreedomViewModel.sendDesign(d: Design) = sendTake(Take(d.name, stampNow(), d.json))

/** Send a design the TV took before again. */
fun TvFreedomViewModel.sendTake(take: Take) = job("Sending the design") {
    put("The TV did not take the design:") { ops, info -> flow().send(ops, info, take.copy(stamp = stampNow())) }
}

fun TvFreedomViewModel.builtInDesign() = sendTake(Take(BUILT_IN, stampNow(), ""))

fun TvFreedomViewModel.undoDesign() = job("Going back one design") {
    put("Could not go back:") { ops, info -> flow().undo(ops, info) }
}

/** Ask the AI for a design, or for a change to the one on the TV now. */
fun TvFreedomViewModel.writeDesign(request: String) = job("Asking the AI for a design") {
    if (request.isBlank()) return@job
    if (!aiWritesDesigns()) return@job tell(NEEDS_CLAUDE)
    put("No design worked after ${DesignFlow.TRIES} tries. The last problem:") { ops, info ->
        val ai = classifier()?.takeIf { it.writes } ?: return@put NEEDS_CLAUDE
        try {
            val current = storage.designs.takes(info).lastOrNull()?.json?.ifEmpty { null }
            flow().write(ops, info, ai, request, state.designs.apps, current)
        } finally {
            ai.close()
        }
    }
}

/** Install the designs screen on the TV if needed, run [step], and show the TV's picture once it took the design. */
private suspend fun TvFreedomViewModel.put(failed: String, step: suspend (DesignOps, TvInfo) -> String) {
    val t = tv ?: return
    val info = state.info ?: return
    val f = flow()
    val ops = f.shell(t) {
        withContext(Dispatchers.IO) { storage.shellApk().let { it to platform.apkVersion(it) } }
    }
    val r = step(ops, info)
    setDesigns { copy(takes = storage.designs.takes(info)) }
    if (r != DesignFlow.OK) return tell("$failed $r")
    update { copy(headline = "Taking a picture of the TV") }
    val shot = f.show(ops, t)
    setDesigns { copy(shot = shot) }
}

const val BUILT_IN = "Built-in design"
private const val NEEDS_CLAUDE = "AI designs need Claude. Pick Claude in Termux or the Claude API in Settings."
