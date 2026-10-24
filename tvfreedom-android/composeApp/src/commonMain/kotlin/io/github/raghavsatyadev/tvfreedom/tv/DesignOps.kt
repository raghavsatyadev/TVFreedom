package io.github.raghavsatyadev.tvfreedom.tv

/**
 * The designs screen on the TV (tvShell): install it, send it a design, ask it to check and keep the design, read what
 * blocks it knows, and open it. The design travels as bytes on stdin; nothing from it goes into a command line.
 */
class DesignOps(private val tv: Tv) {

    /** The shell's versionCode on the TV, or null if it isn't installed. */
    suspend fun version(): Long? {
        if (SHELL !in Parse.names(tv.sh("pm list packages $SHELL"))) return null
        return Parse.versionCode(tv.sh("dumpsys package $SHELL")) ?: 0
    }

    /** Stream [apk] into the package installer. "Success" when it worked. */
    suspend fun install(apk: ByteArray): String =
        tv.exec("cmd package install -r -S ${apk.size}", apk, 180_000).decodeToString().trim()

    /** Send [json] and ask the TV to check it: "ok" (kept and shown) or what is wrong with it. */
    suspend fun apply(json: String): String {
        val bytes = json.encodeToByteArray()
        // exec can't close the TV's stdin: head stops content after the design's bytes (bytes, not characters).
        val out = tv.exec("head -c ${bytes.size} | content write --uri $URI/design", bytes, 60_000).decodeToString().trim()
        if (out.isNotEmpty()) return "The TV did not take the design: $out"
        return call("apply")
    }

    /** Back to the design built into the shell. */
    suspend fun reset(): String = call("reset")

    /** How to write a design for this shell, in words an AI follows. */
    suspend fun catalog(): String = call("catalog")

    /** Open the designs screen and wait until it is up. */
    suspend fun open() = tv.sh("am start -W -n $SHELL/.ShellActivity")

    private suspend fun call(method: String) = Parse.callResult(tv.sh("content call --uri $URI --method $method"))

    companion object {
        const val SHELL = "io.github.raghavsatyadev.tvfreedom.shell"
        private const val URI = "content://$SHELL.design"
    }
}

/** The "result" out of `content call`'s `Result: Bundle[{result=...}]`; the text may span lines and hold } or ]. */
fun Parse.callResult(out: String): String {
    val start = out.indexOf("result=")
    if (start < 0) return out.trim().ifEmpty { "The TV gave no answer." }
    val body = out.substring(start + "result=".length)
    val end = body.lastIndexOf("}]")
    return (if (end >= 0) body.substring(0, end) else body).replace("\r\n", "\n").trim()
}
