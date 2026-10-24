package io.github.raghavsatyadev.tvfreedom.ui

import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.Design
import io.github.raghavsatyadev.tvfreedom.design.Looks
import io.github.raghavsatyadev.tvfreedom.design.TvLook
import io.github.raghavsatyadev.tvfreedom.flow.DesignFlow
import io.github.raghavsatyadev.tvfreedom.store.Settings
import io.github.raghavsatyadev.tvfreedom.store.Take
import io.github.raghavsatyadev.tvfreedom.store.stampNow
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.look

// The designs screen's steps. Each one that talks to the TV is a named job, so TVFreedom stays alive if the user leaves.

private fun TvFreedomViewModel.flow() = DesignFlow(storage, ::log)

private fun TvFreedomViewModel.setDesigns(f: DesignsUi.() -> DesignsUi) = update { copy(designs = designs.f()) }

/** The AI picked in Settings can write designs: only Claude can (in Termux, or with an API key). */
fun TvFreedomViewModel.aiWritesDesigns() =
    settings.ai == Settings.Ai.TERMUX || settings.ai == Settings.Ai.CLAUDE && settings.claudeKey.isNotBlank()

/** Read the TV's apps and home screen settings, to draw each look with them. Changes nothing on the TV. */
fun TvFreedomViewModel.openDesigns() = job("Reading the TV's apps") {
    val t = tv ?: return@job
    val info = state.info ?: return@job
    update { copy(screen = Screen.DESIGNS, designs = DesignsUi(takes = storage.designs.takes(info))) }
    val home = HomeOps(t)
    val apps = home.tvApps().filter { it != HomeOps.LAUNCHER }
    val config = runCatching { home.config() }.getOrNull()
    val favourites = config?.favourites.orEmpty()
    val now = config?.look()
    setDesigns {
        copy(apps = (favourites.filter { it in apps } + apps).distinct(), columns = config?.columns ?: columns,
            picked = Looks.ALL.firstOrNull { now != null && it.look.copy(columns = it.look.columns ?: now.columns) == now })
    }
}

fun TvFreedomViewModel.pickDesign(d: Design) = setDesigns { copy(picked = d) }

/** Send the picked look to the TV's home screen. */
fun TvFreedomViewModel.sendDesign() = job("Sending the look to the TV") {
    val d = state.designs.picked ?: return@job
    put("The TV did not take the look:") { home, info -> flow().send(home, info, Take(d.name, stampNow(), Looks.toJson(d.look))) }
}

fun TvFreedomViewModel.undoDesign() = job("Going back one look") {
    put("Could not go back:") { home, info -> flow().undo(home, info) }
}

/** Ask the AI for a look. It shows first; nothing goes to the TV until the user sends it. */
fun TvFreedomViewModel.writeDesign(request: String) = job("Asking the AI for a look") {
    if (request.isBlank()) return@job
    if (!aiWritesDesigns()) return@job tell(NEEDS_CLAUDE)
    val ai = classifier()?.takeIf { it.writes } ?: return@job tell(NEEDS_CLAUDE)
    val current = state.picked() ?: TvLook()
    try {
        flow().write(ai, request, current)
            .onSuccess { d -> setDesigns { copy(ai = d, picked = d) } }
            .onFailure { tell("No look worked after ${DesignFlow.TRIES} tries. The last problem: ${it.message}") }
    } finally {
        ai.close()
    }
}

/** The look the AI starts from: the one picked, else the TV's newest. */
private fun UiState.picked(): TvLook? = designs.picked?.look ?: designs.takes.lastOrNull()?.look

/** Update the home screen if it is too old for looks, run [step], and show the TV's picture once it took the look. */
private suspend fun TvFreedomViewModel.put(failed: String, step: suspend (HomeOps, TvInfo) -> String) {
    val t = tv ?: return
    val info = state.info ?: return
    val f = flow()
    val home = runCatching { f.ready(t) { launcher() } }.getOrElse { return tell("$failed ${it.message}") }
    val r = step(home, info)
    setDesigns { copy(takes = storage.designs.takes(info)) }
    if (r != DesignFlow.OK) return tell("$failed $r")
    update { copy(headline = "Taking a picture of the TV") }
    val shot = f.show(home)
    setDesigns { copy(shot = shot) }
}

private const val NEEDS_CLAUDE = "AI looks need Claude. Pick Claude in Termux or the Claude API in Settings."
