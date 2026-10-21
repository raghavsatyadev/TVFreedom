package com.gathin.tvgo.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.gathin.tvgo.core.AskItem
import com.gathin.tvgo.core.Classifier
import com.gathin.tvgo.core.Example
import com.gathin.tvgo.core.Prompt
import com.gathin.tvgo.core.TvInfo
import com.gathin.tvgo.core.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Claude Code inside Termux, so planning runs on the user's Claude subscription and tvgo holds no API key.
 * tvgo sends the prompt through Termux's RUN_COMMAND service as stdin to `claude -p --tools ""` (print mode,
 * no tools) and reads stdout back from the result PendingIntent.
 * Termux needs allow-external-apps=true in ~/.termux/termux.properties, and tvgo needs Termux's RUN_COMMAND permission.
 */
/** [model] is a Claude Code model alias ("opus", "sonnet"). */
/** [waitOpen] returns once tvgo is on screen, for when Android won't let a background app start Termux's command. */
class TermuxClaude(private val ctx: Context, private val model: String = "opus",
                   private val waitOpen: suspend () -> Unit = {}) : Classifier {
    override val name = "Claude (Termux, $model)"

    data class Result(val exit: Int, val out: String, val err: String)

    /** What is still missing before tvgo can use Claude in Termux, in the order the user has to do it. */
    enum class Step { INSTALL_TERMUX, PERMISSION, OPEN_TERMUX, ALLOW_APPS, INSTALL_CLAUDE, LOGIN, READY }

    /** The first missing step, found without asking Claude anything (no usage, a second or two). */
    suspend fun probe(log: (String) -> Unit = {}): Step {
        if (!installed(ctx)) return Step.INSTALL_TERMUX
        if (!granted(ctx)) return Step.PERMISSION
        val r = runCatching {
            exec("$PREFIX/bin/sh", arrayOf("-c", "test -x $PREFIX/bin/claude && echo claude; test -s $HOME/.claude/.credentials.json && echo login"),
                timeoutMs = 20_000)
        }.getOrElse { log("Termux did not answer: ${it.message ?: it.javaClass.simpleName}"); return Step.OPEN_TERMUX }
        log("termux probe: exit ${r.exit} out [${r.out.trim()}] err [${r.err.trim().take(200)}]")
        val out = r.out.lines().map { it.trim() }
        return when {
            "allow-external-apps" in r.err -> Step.ALLOW_APPS
            r.exit != 0 && "claude" !in out -> if (r.err.contains("No such file", true)) Step.OPEN_TERMUX else Step.INSTALL_CLAUDE
            "claude" !in out -> Step.INSTALL_CLAUDE
            "login" !in out -> Step.LOGIN
            else -> Step.READY
        }
    }

    /** READY, or what the user still has to do. Sends one tiny prompt, so it also checks the login really works. */
    suspend fun check(): String {
        val step = probe()
        if (step != Step.READY && step != Step.LOGIN) return "Claude in Termux is not set up yet (${step.name.lowercase()}). Open Settings."
        val r = runCatching { exec("$PREFIX/bin/claude", arrayOf("-p", "--model", model, "--tools", ""), "Reply with the single word OK.", 120_000) }
            .getOrElse { return "Termux did not answer: ${it.message ?: it.javaClass.simpleName}" }
        return if (r.exit == 0 && r.out.isNotBlank()) READY
        else "Claude in Termux failed (exit ${r.exit}): ${r.err.ifBlank { r.out }.take(200)}. Log in to Claude in Termux."
    }

    override suspend fun classify(info: TvInfo, items: List<AskItem>, examples: Map<String, Example>,
                                  keep: Set<String>, log: (String) -> Unit): Map<String, Verdict> {
        val status = check()
        if (status != READY) {
            log("$status: every package the lists don't cover is kept")
            return emptyMap()
        }
        return inBatches(items, 60, log, name) { chunk ->
            val r = exec("$PREFIX/bin/claude", arrayOf("-p", "--model", model, "--tools", ""), Prompt.build(info, chunk, examples, keep, withExamples = true), 900_000)
            if (r.exit != 0) log("claude exited ${r.exit}: ${r.err.take(300)}")
            r.out
        }
    }

    /** Install Claude Code with the bundled scripts/termux-setup.sh, then log in, in a Termux window the user watches. */
    fun startInstall() {
        val script = ctx.assets.open("termux/termux-setup.sh").use { it.readBytes().decodeToString() }
        openSession("$PREFIX/bin/sh", arrayOf("-c", script, "sh", "app"))
    }

    /** Open `claude` in a Termux window so the user can log in once. */
    fun startLogin() = openSession("$PREFIX/bin/claude", emptyArray())

    /** Bring Termux to the front (the first open also unpacks its base system). */
    fun openTermux() {
        ctx.packageManager.getLaunchIntentForPackage(TERMUX)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Run [path] in a new, visible Termux session and show it; nothing comes back, the next probe sees the result. */
    private fun openSession(path: String, args: Array<String>) {
        ctx.startService(command(path, args).putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0"))
        // Termux can't open its own window from the background on Android 10+; tvgo is in front, so it can.
        openTermux()
    }

    private fun command(path: String, args: Array<String>) = Intent("com.termux.RUN_COMMAND")
        .setClassName(TERMUX, "com.termux.app.RunCommandService")
        .putExtra("com.termux.RUN_COMMAND_PATH", path)
        .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
        .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)

    /** Run [path] in the background with [stdin]; resumes when Termux reports the result. */
    suspend fun exec(path: String, args: Array<String>, stdin: String = "", timeoutMs: Long): Result = try {
        execOnce(path, args, stdin, timeoutMs)
    } catch (e: CancellationException) {
        throw e // its own timeout (or a cancel) is an IllegalStateException too; don't run it again
    } catch (e: IllegalStateException) {
        // BackgroundServiceStartNotAllowedException: Termux wasn't running and tvgo is in the background. Try again once open.
        waitOpen()
        execOnce(path, args, stdin, timeoutMs)
    }

    private suspend fun execOnce(path: String, args: Array<String>, stdin: String, timeoutMs: Long): Result = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val action = "${ctx.packageName}.TERMUX_RESULT.${UUID.randomUUID()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    runCatching { ctx.unregisterReceiver(this) }
                    val b = i.getBundleExtra("result") ?: Bundle()
                    val err = listOf(b.getString("stderr"), b.getString("errmsg")).filterNot { it.isNullOrBlank() }.joinToString(" ")
                    if (cont.isActive) cont.resume(Result(b.getInt("exitCode", b.getInt("err", -1)), b.getString("stdout").orEmpty(), err))
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
            // Mutable so Termux can add the result extras; explicit (our package) so nothing else can receive it.
            val reply = PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(action).setPackage(ctx.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)
            val run = command(path, args).putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", reply)
            if (stdin.isNotEmpty()) run.putExtra("com.termux.RUN_COMMAND_STDIN", stdin)
            try {
                ctx.startService(run)
            } catch (e: Exception) {
                runCatching { ctx.unregisterReceiver(receiver) }
                cont.resumeWithException(e)
            }
        }
    }

    companion object {
        const val READY = "ready"
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        /** Safe to paste twice: adds the line only if it isn't there yet. */
        const val ALLOW_LINE = "mkdir -p ~/.termux; grep -qs '^allow-external-apps *= *true' ~/.termux/termux.properties || " +
            "echo allow-external-apps=true >> ~/.termux/termux.properties; termux-reload-settings"
        const val GET_TERMUX = "https://f-droid.org/packages/com.termux/"
        private const val TERMUX = "com.termux"
        private const val PREFIX = "/data/data/com.termux/files/usr"
        private const val HOME = "/data/data/com.termux/files/home"

        fun installed(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(TERMUX, 0) }.isSuccess

        fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
