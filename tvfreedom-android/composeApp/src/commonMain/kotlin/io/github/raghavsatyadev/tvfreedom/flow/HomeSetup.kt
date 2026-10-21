package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.DISABLE
import io.github.raghavsatyadev.tvfreedom.core.Guard
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.Removal
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.LauncherConfig
import io.github.raghavsatyadev.tvfreedom.tv.TvOps
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

/**
 * What the home-screen setup would do on this TV, read before anything changes (shown above the typed GO).
 * [apps] are the TV's home-screen apps; [config] starts from what the launcher on the TV uses now, if it has one.
 */
data class HomeDraft(
    val apps: List<String>,
    val config: LauncherConfig,
    /** The stock home screen packages to turn off, then the stock screensaver ones. */
    val homeOff: List<ListEntry>,
    val dreamOff: List<ListEntry>,
    val oldScreensaver: String,
    /** The launcher's versionCode on the TV now (null: not installed) and in TVFreedom. */
    val installed: Long?,
    val bundled: Long,
    /** True when the packages come from this model's own tested lists, not from a guess. */
    val fromLists: Boolean,
    /** The launcher on the TV already reported its settings (so the screen shows them, not defaults). */
    val reported: Boolean,
    /** What the TV opens on Home now. */
    val currentHome: String = "",
) {
    /** Phase 4 has something to do: the launcher is missing or old, not Home yet, or a stock home screen is still on. */
    val launcherDue get() = installed == null || installed < bundled || homeOff.isNotEmpty() ||
        !currentHome.startsWith(HomeOps.LAUNCHER)

    /** Phase 5 has something to do: the clock isn't the screensaver yet, or a stock screensaver is still on. */
    val screensaverDue get() = oldScreensaver != HomeOps.CLOCK_DREAM || dreamOff.isNotEmpty()

    /** An older launcher on the TV couldn't say what it uses, so its favourites are kept unless changed. */
    val olderLauncher get() = installed != null && !reported
}

/**
 * tv.py phases 4 and 5 from the phone, as the last two phases of a debloat: 4 installs the launcher and turns off the
 * stock home screen, 5 makes ClockDream the screensaver and turns off the stock one. Each phase is saved first (so
 * Restore can undo it), restarts the TV, and is undone at once if its check fails.
 */
class HomeSetup(private val storage: Storage, private val runner: Runner, private val log: (String) -> Unit) {

    suspend fun draft(tv: Tv, info: TvInfo, bundled: Long): HomeDraft {
        runner.begin(tv, "Reading the TV")
        val home = HomeOps(tv)
        val ops = TvOps(tv, tv.address)
        val installed = home.launcherVersion()
        val reported = if (installed != null) runCatching { home.config() }.getOrNull() else null
        val apps = home.tvApps().filter { it != HomeOps.LAUNCHER }
        val fromModel4 = storage.modelList(info, 4)
        val fromModel5 = storage.modelList(info, 5)
        val all = ops.packages()
        val off = ops.packages("-d")
        val oldScreensaver = home.screensaver()
        val homeOff = (fromModel4 ?: guessHome(ops)).filter { it.pkg in all && it.pkg !in off }
        val dreamOff = (fromModel5 ?: guessDream(oldScreensaver)).filter { it.pkg in all && it.pkg !in off }
        val config = reported ?: LauncherConfig(
            favourites = DEFAULT_FAVOURITES.filter { it in apps },
            columns = home.columns(),
        )
        log("launcher on the TV: ${installed ?: "not installed"}, in TVFreedom: $bundled; ${apps.size} TV apps")
        log("would turn off: ${(homeOff + dreamOff).joinToString { it.pkg }.ifEmpty { "nothing" }}; screensaver now $oldScreensaver")
        return HomeDraft(apps, config, homeOff, dreamOff, oldScreensaver, installed, bundled,
            fromLists = fromModel4 != null && fromModel5 != null, reported = reported != null,
            currentHome = ops.homeActivity())
    }

    /** Every other app with a HOME screen, when this model has no tested list (core and TV settings stay). */
    private suspend fun guessHome(ops: TvOps): List<ListEntry> = ops.homePackages().sorted()
        .filter { it != HomeOps.LAUNCHER && Guard.keepReason(it).let { why -> why.isEmpty() || why == "launcher/setup" } }
        .map { ListEntry(DISABLE, it, "stock home screen") }

    /** The current screensaver's app, when this model has no tested list and the guard allows it. */
    private fun guessDream(current: String): List<ListEntry> {
        val pkg = current.substringBefore('/').takeIf { '/' in current } ?: return emptyList()
        if (pkg == HomeOps.LAUNCHER || Guard.keepReason(pkg).isNotEmpty()) return emptyList()
        return listOf(ListEntry(DISABLE, pkg, "stock screensaver"))
    }

    /**
     * What the user chose on the plan: [config] for the launcher (its brandColor included), whether to keep the
     * favourites an older one saved, and the company [logo] as PNG (null: no logo, so any on the TV is taken off).
     */
    class Choice(val config: LauncherConfig, val keepTvFavourites: Boolean, val apk: ByteArray, val logo: ByteArray? = null)

    /**
     * Phase 4, after the typed GO: install or update the launcher, set it up, turn off the stock home screen,
     * restart and check the new one opens. If it doesn't, the stock one is turned back on at once.
     */
    suspend fun launcherPhase(tv: Tv, info: TvInfo, draft: HomeDraft, choice: Choice): RunResult {
        val rows = draft.homeOff.map { Removal(it.pkg, DISABLE, 4, it.comment, "home setup") }
        val runDir = storage.writeHomeRun(info, 4, rows, emptyMap())
        log("phase 4 saved: ${runDir.name}")
        ensureLauncher(tv, draft, choice)
        val ops = TvOps(tv, tv.address)
        runner.begin(tv, "Turning off the old home screen")
        rows.forEach { r -> log("disable ${r.pkg}: ${ops.apply(ListEntry(DISABLE, r.pkg, r.reason)).ifEmpty { "ok" }}") }
        if (rows.isEmpty()) log("the old home screen is already off")
        runner.saveWait(tv)
        val result = runner.rebootAndCheck(tv, info, runDir, expectHome = HomeOps.LAUNCHER)
        storage.markVerified(runDir, result.ok)
        result.tv?.takeIf { !result.ok }?.let { rollBack(it, rows, null) }
        return result
    }

    /**
     * Phase 5, after the typed GO: make ClockDream the screensaver (installing the launcher first if phase 4 didn't),
     * check it starts, turn off the stock screensaver, restart and check. On failure the old one comes back.
     */
    suspend fun screensaverPhase(tv: Tv, info: TvInfo, draft: HomeDraft, choice: Choice, newHome: Boolean): RunResult {
        val rows = draft.dreamOff.map { Removal(it.pkg, DISABLE, 5, it.comment, "home setup") }
        val runDir = storage.writeHomeRun(info, 5, rows, mapOf(HomeOps.SCREENSAVER to draft.oldScreensaver))
        log("phase 5 saved: ${runDir.name}")
        ensureLauncher(tv, draft, choice)
        val home = HomeOps(tv)
        val ops = TvOps(tv, tv.address)
        runner.begin(tv, "Setting the screensaver")
        log("screensaver: ${home.setScreensaver(HomeOps.CLOCK_DREAM).ifEmpty { "ok" }}")
        home.startDream(home.sdk())
        delay(5_000)
        val runs = "ClockDream" in home.dreaming()
        log(if (runs) "the clock screensaver runs" else "could not see the clock screensaver start")
        ops.key("KEYCODE_WAKEUP")
        if (!runs) {
            log("screensaver back to ${draft.oldScreensaver}: ${home.setScreensaver(draft.oldScreensaver).ifEmpty { "ok" }}")
            error("the clock screensaver did not start, so the old one was put back. Nothing was turned off")
        }
        runner.begin(tv, "Turning off the old screensaver")
        rows.forEach { r -> log("disable ${r.pkg}: ${ops.apply(ListEntry(DISABLE, r.pkg, r.reason)).ifEmpty { "ok" }}") }
        if (rows.isEmpty()) log("the old screensaver is already off")
        runner.saveWait(tv)
        val result = runner.rebootAndCheck(tv, info, runDir, expectHome = HomeOps.LAUNCHER.takeIf { newHome })
        val kept = result.tv?.let { HomeOps(it).screensaver() } == HomeOps.CLOCK_DREAM
        if (result.tv != null) log(if (kept) "the screensaver is still the clock after the restart" else "the screensaver setting did not stay")
        val ok = result.ok && kept
        storage.markVerified(runDir, ok)
        result.tv?.takeIf { !ok }?.let { rollBack(it, rows, draft.oldScreensaver) }
        return result.copy(ok = ok)
    }

    /** Set once the launcher is installed, set up and seen to open in this run. */
    private var ready = false

    /** A new run starts: install and set up the launcher again if a phase needs it. */
    fun reset() {
        ready = false
    }

    /** Install or update the launcher once per run, grant its permissions, send the settings and check it opens. */
    private suspend fun ensureLauncher(tv: Tv, draft: HomeDraft, choice: Choice) {
        if (ready) return
        val home = HomeOps(tv)
        val ops = TvOps(tv, tv.address)
        val installed = home.launcherVersion()
        if (installed == null || installed < draft.bundled) {
            runner.begin(tv, if (installed == null) "Installing the new home screen" else "Updating the home screen")
            val out = try {
                home.install(choice.apk)
            } catch (e: TimeoutCancellationException) {
                error("the TV did not finish installing the home screen. If it shows a Google Play Protect message, " +
                    "close it; nothing was turned off")
            }
            log("install: $out")
            if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out) error(
                "the home screen on the TV was built on another computer, so it can't be updated. Uninstall " +
                    "${HomeOps.LAUNCHER} on the TV (its settings go with it), then try again")
            // Google TV on Android 14+ has Play Protect block apps that target older Android: the install then
            // ends with no answer, or a verification failure, and a "blocked" dialog on the TV.
            if (out.isEmpty() || "VERIFICATION" in out) error(
                "the TV blocked the home screen (Google Play Protect). Close the message on the TV; nothing was turned off")
            if (!out.contains("Success")) error("the TV did not install the home screen ($out)")
        }
        HomeOps.PERMISSIONS.forEach { log("grant $it: ${home.grant(it).ifEmpty { "ok" }}") }

        runner.begin(tv, "Setting up the home screen")
        val c = choice.config
        val send = if (choice.keepTvFavourites) home.config()?.let { c.copy(favourites = it.favourites) } ?: c else c
        home.config(send) ?: error("the new home screen did not take its settings")
        log("settings sent: ${send.favourites.size} favourites, ${send.columns} per row, weather ${send.place.ifEmpty { "off" }}, " +
            "colour ${send.brandColor.ifEmpty { "black" }}")
        sendLogo(home, choice.logo)
        home.stopLauncher()
        home.openLauncher()
        delay(4_000)
        val focus = ops.focusedPackage()
        if (focus != HomeOps.LAUNCHER) error("the new home screen did not open (the TV shows $focus). Nothing was turned off")
        ready = true
    }

    /** The logo is a nice-to-have: if it doesn't arrive, say so and go on with the home screen. */
    private suspend fun sendLogo(home: HomeOps, logo: ByteArray?) {
        val out = runCatching { home.setLogo(logo) }.getOrElse { io.github.raghavsatyadev.tvfreedom.core.Text.why(it) }
        var onTv = home.config()?.logoBytes ?: -1
        // content can still be writing when its stream closes: look once more before calling it lost.
        if (logo != null && onTv != logo.size.toLong()) {
            delay(1_500)
            onTv = home.config()?.logoBytes ?: -1
        }
        log(when {
            logo == null -> "no logo: ${if (onTv == 0L) "none on the TV" else "could not take the old one off ($out)"}"
            onTv == logo.size.toLong() -> "logo sent (${logo.size / 1024} KB)"
            else -> "the logo did not reach the TV, so the home screen shows none (${out.ifEmpty { "size $onTv" }})"
        })
    }

    /** A phase didn't check out: turn back on what it turned off (and the old screensaver) rather than leave it half done. */
    private suspend fun rollBack(tv: Tv, rows: List<Removal>, oldScreensaver: String?) {
        runner.begin(tv, "Undoing this phase")
        val ops = TvOps(tv, tv.address)
        rows.asReversed().forEach { log("enable ${it.pkg}: ${ops.enable(it.pkg).ifEmpty { "ok" }}") }
        oldScreensaver?.let { log("screensaver back to $it: ${HomeOps(tv).setScreensaver(it).ifEmpty { "ok" }}") }
        ops.key("KEYCODE_HOME")
        runner.saveWait(tv)
    }

    companion object {
        /** The launcher's own default dock (launcher/app/src/main/res/values/config.xml), kept where installed. */
        val DEFAULT_FAVOURITES = listOf(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.mitv.tvhome.mitvplus",
            "com.google.android.youtube.tv", "org.jellyfin.androidtv", "com.plexapp.android", "com.android.vending",
        )
    }
}
