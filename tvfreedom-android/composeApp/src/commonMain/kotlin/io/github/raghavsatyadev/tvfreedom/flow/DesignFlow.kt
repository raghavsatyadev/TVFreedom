package io.github.raghavsatyadev.tvfreedom.flow

import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.design.DesignPrompt
import io.github.raghavsatyadev.tvfreedom.store.Storage
import io.github.raghavsatyadev.tvfreedom.store.Take
import io.github.raghavsatyadev.tvfreedom.store.stampNow
import io.github.raghavsatyadev.tvfreedom.tv.DesignOps
import io.github.raghavsatyadev.tvfreedom.tv.HomeOps
import io.github.raghavsatyadev.tvfreedom.tv.Tv
import kotlinx.coroutines.delay

/**
 * Designs on the TV's designs screen: install it when needed, send a ready-made or AI design, keep each design the TV
 * took as a take (so Undo can go back), and show what the TV draws.
 */
class DesignFlow(private val storage: Storage, private val log: (String) -> Unit) {

    /** The designs screen on [tv], installed or updated first when TVFreedom carries a newer one ([apk], versionCode). */
    suspend fun shell(tv: Tv, apk: suspend () -> Pair<ByteArray, Long>): DesignOps {
        val ops = DesignOps(tv)
        val have = ops.version()
        val (bytes, bundled) = apk()
        if (have == null || have < bundled) {
            log(if (have == null) "installing the designs screen on the TV" else "updating the designs screen on the TV")
            val r = ops.install(bytes)
            if ("Success" !in r) error("the TV did not install the designs screen: $r")
        }
        return ops
    }

    /** Send [take] (or go back to the built-in design) and keep it as the newest take once the TV says ok. */
    suspend fun send(ops: DesignOps, info: TvInfo, take: Take): String {
        val r = if (take.builtIn) ops.reset() else ops.apply(take.json)
        if (r == OK) storage.designs.add(info, take) else log("the TV said: $r")
        return r
    }

    /** Drop the newest take and show the one before it (the built-in design when none is left). */
    suspend fun undo(ops: DesignOps, info: TvInfo): String {
        val back = storage.designs.undo(info)
        return if (back == null || back.builtIn) ops.reset() else ops.apply(back.json)
    }

    /** Open the designs screen and take a picture of it. */
    suspend fun show(ops: DesignOps, tv: Tv): ByteArray? {
        ops.open()
        delay(DRAW_MS) // am start -W returns once the screen is up, but the tiles load their icons just after
        return HomeOps(tv).screenshot()
    }

    /**
     * Ask [ai] to write a design for [request], or change [current]. Up to [TRIES] tries: each one that the phone or
     * the TV rejects goes back to the AI with the reason. "ok" once the TV took one, else the last reason.
     */
    suspend fun write(ops: DesignOps, info: TvInfo, ai: Classifier, request: String, apps: List<String>, current: String?): String {
        val catalog = ops.catalog()
        var bad: String? = null
        var why = "no answer"
        for (i in 1..TRIES) {
            log("asking ${ai.name} for a design (try $i of $TRIES)")
            val answer = ai.ask(DesignPrompt.build(catalog, apps, request, current, bad, why.takeIf { bad != null }), log)
            val json = DesignPrompt.extract(answer).getOrElse {
                bad = answer.take(MAX_BAD)
                why = it.message ?: "not a design"
                log("the AI's answer was not a design: $why")
                null
            } ?: continue
            val r = send(ops, info, Take(DesignPrompt.title(request), stampNow(), json))
            if (r == OK) return OK
            bad = json
            why = r
        }
        return why
    }

    companion object {
        const val OK = "ok"
        const val TRIES = 3
        private const val DRAW_MS = 2_000L
        private const val MAX_BAD = 8_000
    }
}
