package io.github.raghavsatyadev.tvfreedom.ui

import io.github.raghavsatyadev.tvfreedom.core.Plan
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.Design
import io.github.raghavsatyadev.tvfreedom.flow.ChangeGroup
import io.github.raghavsatyadev.tvfreedom.flow.ExtrasChoice
import io.github.raghavsatyadev.tvfreedom.flow.ExtrasDraft
import io.github.raghavsatyadev.tvfreedom.flow.HomeDraft
import io.github.raghavsatyadev.tvfreedom.store.RestoreOption
import io.github.raghavsatyadev.tvfreedom.store.Take
import io.github.raghavsatyadev.tvfreedom.tv.Candidate
import io.github.raghavsatyadev.tvfreedom.tv.LauncherConfig
import okio.Path

enum class Screen { SETUP, CONNECT, TV, PLAN, RUN, RESTORE, SETTINGS, HOME, DESIGNS }

/** How a run ended, for the result screen: free RAM in MB before and after, and the TV's screen after the restart. */
data class Outcome(
    val ok: Boolean,
    val headline: String,
    val body: String,
    val ramBefore: Int? = null,
    val ramAfter: Int? = null,
    val screen: Path? = null,
)

data class UiState(
    val screen: Screen = Screen.SETUP,
    val busy: Boolean = false,
    /** Plain name of the step running now; empty for quick steps, which show nothing. */
    val headline: String = "",
    /** The latest log line: the detail under the headline. */
    val status: String = "",
    /** What the user has to know from the last step (a failure or something to do); empty when all went well. */
    val notice: String = "",
    val log: List<String> = emptyList(),
    val searched: Boolean = false,
    val candidates: List<Candidate> = emptyList(),
    val showPair: Boolean = false,
    val pairAddress: String = "",
    val info: TvInfo? = null,
    val plan: Plan? = null,
    val runDir: Path? = null,
    val phases: Set<Int> = emptySet(),
    val outcome: Outcome? = null,
    val restoreOptions: List<RestoreOption> = emptyList(),
    val restoreChoice: RestoreOption? = null,
    val restorePreview: String = "",
    /** Restore's tab: 0 by run, 1 by change. */
    val restoreTab: Int = 0,
    /** The single changes still in effect on the TV (null until read), and the ones ticked. */
    val changes: List<ChangeGroup>? = null,
    val picked: Set<String> = emptySet(),
    val aiStatus: String = "",
    /** The next thing to do to use Claude in Termux; null while checking. */
    val termuxStep: TermuxStep? = null,
    /** What phases 4 and 5 (home screen, screensaver) would do on this TV; read with the plan. */
    val homeDraft: HomeDraft? = null,
    /** The launcher settings the user chose for phases 4 and 5. */
    val homeConfig: LauncherConfig? = null,
    /** Keep the favourites an older launcher on the TV saved (the user left the list as it was). */
    val keepTvFavourites: Boolean = false,
    /** Background colours that go with the company logo; bumps [brandStamp] when the logo or colour changes. */
    val brandColors: List<String> = emptyList(),
    val brandStamp: Int = 0,
    /** While a debloat runs: the phase on now and the ones done, for the step list. */
    val runPhase: Int? = null,
    val runDone: List<Int> = emptyList(),
    /** The steps of the debloat on the run screen, in everyday words; empty for a restore or a restart. */
    val runSteps: List<PlainStep> = emptyList(),
    /** What phase 6 (lockdown, settings, DNS, SmartTube) finds on this TV, and what the user switched on. */
    val extrasDraft: ExtrasDraft? = null,
    val extras: ExtrasChoice = ExtrasChoice(),
    val designs: DesignsUi = DesignsUi(),
)

/** The designs screen: ready-made designs for this TV's apps, the designs it took (oldest first), and its picture. */
data class DesignsUi(
    val apps: List<String> = emptyList(),
    val ready: List<Design> = emptyList(),
    val takes: List<Take> = emptyList(),
    val shot: ByteArray? = null,
)
