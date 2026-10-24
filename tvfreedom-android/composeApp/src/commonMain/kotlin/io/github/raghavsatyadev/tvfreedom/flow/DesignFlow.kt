package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.Design
import io.github.raghavsatyadev.tvfreedom.design.DesignPrompt
import io.github.raghavsatyadev.tvfreedom.design.Looks
import io.github.raghavsatyadev.tvfreedom.design.TvLook
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.store.Take
import io.github.raghavsatyadev.tvfreedom.store.stampNow
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Parse
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import io.github.raghavsatyadev.tvfreedom.tv.look
import kotlinx.coroutines.delay

/**
 * Looks for the TVFreedom home screen: update the launcher when it is too old for looks, send a look, keep each look
 * the TV took (so Undo can go back), show what the TV draws, and ask the AI for a look.
 */
class DesignFlow(private val storage: Storage, private val log: (String) -> Unit) {

    /**
     * The home screen on [tv], updated from [apk] (bytes, versionCode) when it is older than looks. Fails, in words
     * for the user, when the TVFreedom home screen isn't on the TV. Also removes the old designs app.
     */
    suspend fun ready(tv: Tv, apk: suspend () -> Pair<ByteArray, Long>): HomeOps {
        val home = HomeOps(tv)
        val have = home.launcherVersion() ?: error(NO_HOME)
        if (have < LOOKS) {
            val (bytes, version) = apk()
            if (version < LOOKS) error("this TVFreedom is too old to change the home screen's look. Update TVFreedom")
            log("updating the home screen on the TV")
            val r = home.install(bytes)
            if ("Success" !in r) error("the TV did not update the home screen ($r)")
        }
        // The designs app TVFreedom used to put on the TV: looks now go to the home screen itself.
        if (SHELL in Parse.names(tv.sh("pm list packages $SHELL"))) log("removing the old designs app: ${tv.sh("pm uninstall $SHELL").trim()}")
        return home
    }

    /** Send [take]'s look and keep it as the newest take once the TV shows it. The look before goes in first, for Undo. */
    suspend fun send(home: HomeOps, info: TvInfo, take: Take): String {
        val look = take.look ?: return "this is not a look"
        val now = home.config() ?: return NO_ANSWER
        val before = now.look()
        if (storage.designs.takes(info).lastOrNull()?.look != before) storage.designs.add(info, Take(BEFORE, stampNow(), Looks.toJson(before)))
        val r = put(home, look, now.columns)
        if (r == OK) storage.designs.add(info, take)
        return r
    }

    /** Drop the newest take and put back the look before it. */
    suspend fun undo(home: HomeOps, info: TvInfo): String {
        val back = storage.designs.undo(info)?.look ?: return "there is no look to go back to"
        val now = home.config() ?: return NO_ANSWER
        return put(home, back, now.columns)
    }

    private suspend fun put(home: HomeOps, look: TvLook, columns: Int): String {
        val got = home.look(look, columns) ?: return NO_ANSWER
        val want = look.copy(columns = look.columns ?: columns)
        if (got.look() != want) {
            log("sent $want, the TV has ${got.look()}")
            return "the home screen did not keep the look"
        }
        return OK
    }

    /** Restart the home screen so it reads the look, and take a picture of it. */
    suspend fun show(home: HomeOps): ByteArray? {
        home.stopLauncher()
        home.openLauncher()
        delay(DRAW_MS) // the tiles load their icons just after the screen is up
        return home.screenshot()
    }

    /**
     * Ask [ai] for a look for [request], starting from [current]. Up to [TRIES] tries: each answer the phone can't
     * use goes back to the AI with the reason. Nothing goes to the TV: the user sees it first.
     */
    suspend fun write(ai: Classifier, request: String, current: TvLook): Result<Design> {
        var bad: String? = null
        var why = "no answer"
        for (i in 1..TRIES) {
            log("asking ${ai.name} for a look (try $i of $TRIES)")
            val answer = ai.ask(DesignPrompt.build(request, current, bad, why.takeIf { bad != null }), log)
            Looks.extract(answer).onSuccess {
                return Result.success(Design(DesignPrompt.title(request), "Made by the AI", it))
            }.onFailure {
                bad = answer.take(MAX_BAD)
                why = it.message ?: "not a look"
                log("the AI's answer was not a look: $why")
            }
        }
        return Result.failure(IllegalStateException(why))
    }

    companion object {
        const val OK = "ok"
        const val TRIES = 3
        const val BEFORE = "Before"

        /** The launcher versionCode with every look working (2.19: layouts, clocks and backgrounds). */
        const val LOOKS = 21L
        const val NO_HOME = "the TVFreedom home screen isn't on this TV yet. Set up the TV first; it puts the home screen on"
        private const val NO_ANSWER = "the home screen on the TV did not answer"
        private const val SHELL = "io.github.raghavsatyadev.tvfreedom.shell"
        private const val DRAW_MS = 2_500L
        private const val MAX_BAD = 4_000
    }
}
