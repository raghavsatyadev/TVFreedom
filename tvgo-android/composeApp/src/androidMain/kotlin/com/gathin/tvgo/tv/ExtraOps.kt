package com.gathin.tvgo.tv

/**
 * Phase 6's TV side (after dumbtv's lockdown, settings and youtube steps): app-ops, a few global settings,
 * Android's own Private DNS, and SmartTube. Everything that goes into a command is a package name, a key from
 * [SETTINGS], a checked hostname, or a mode from a fixed set; every change has an undo line (see [Undo]).
 */
class ExtraOps(private val tv: AdbTv) {

    /** The background op for this Android: RUN_ANY_IN_BACKGROUND from 9 (API 28), RUN_IN_BACKGROUND before. */
    fun backgroundOp(sdk: Int) = if (sdk >= 28) "RUN_ANY_IN_BACKGROUND" else "RUN_IN_BACKGROUND"

    /** The op's mode for [pkg] now ("default" when the app never had one set). */
    suspend fun appop(pkg: String, op: String): String = Parse.appopMode(tv.sh("appops get ${Parse.safePackage(pkg)} ${safeOp(op)}"), op)

    suspend fun setAppop(pkg: String, op: String, mode: String): String {
        require(mode in MODES) { "not an app-op mode: $mode" }
        return tv.sh("appops set ${Parse.safePackage(pkg)} ${safeOp(op)} $mode").trim()
    }

    suspend fun forceStop(pkg: String) = tv.sh("am force-stop ${Parse.safePackage(pkg)}")

    /** A setting's value now; "null" when it isn't set (what settings get prints). */
    suspend fun setting(ns: String, key: String): String = tv.sh("settings get ${safeNs(ns)} ${safeKey(key)}").trim()

    /** Set [key], or with "null" delete it again (an untouched TV has no value). */
    suspend fun putSetting(ns: String, key: String, value: String): String =
        if (value == "null" || value.isEmpty()) tv.sh("settings delete ${safeNs(ns)} ${safeKey(key)}").trim()
        else tv.sh("settings put ${safeNs(ns)} ${safeKey(key)} ${Parse.settingValue(value)}").trim()

    /** True if the TV can look up a name now (through Private DNS when it is on): ping prints the address it found. */
    suspend fun resolves(host: String = "connectivitycheck.gstatic.com"): Boolean =
        Parse.resolved(tv.sh("ping -c 1 -W 3 ${Parse.hostname(host)} 2>&1"))

    suspend fun present(pkg: String): Boolean = pkg in Parse.names(tv.sh("pm list packages ${Parse.safePackage(pkg)}"))

    suspend fun enabled(pkg: String): Boolean = present(pkg) && pkg !in Parse.names(tv.sh("pm list packages -d $pkg"))

    suspend fun uninstall(pkg: String) = tv.sh("pm uninstall ${Parse.safePackage(pkg)}").trim()

    /** Open [pkg]'s TV activity, the way the home screen would. */
    suspend fun open(pkg: String) =
        tv.sh("monkey -p ${Parse.safePackage(pkg)} -c android.intent.category.LEANBACK_LAUNCHER 1")

    private fun safeOp(op: String) = op.also { require(Regex("^[A-Z_]+$").matches(it)) { "not an app-op: $op" } }
    private fun safeNs(ns: String) = ns.also { require(ns in setOf("global", "secure", "system")) { "not a namespace: $ns" } }
    private fun safeKey(key: String) = key.also { require(Regex("^[A-Za-z0-9_.]+$").matches(it)) { "not a setting: $key" } }

    companion object {
        val MODES = setOf("allow", "ignore", "deny", "default", "foreground")

        /** (namespace, key, value): only keys that exist on Android TV and change something there. */
        val SETTINGS = listOf(
            Triple("global", "window_animation_scale", "0.5"),
            Triple("global", "transition_animation_scale", "0.5"),
            // animator_duration_scale stays: dumbtv saw 0.5 break a launcher's menu.
            Triple("global", "send_action_app_error", "0"),   // no "Report" to Google on a crash dialog
            Triple("global", "wifi_scan_always_enabled", "0"), // no Wi-Fi scans for location while Wi-Fi is off (Ethernet TVs)
        )

        const val YOUTUBE = "com.google.android.youtube.tv"
        const val SMARTTUBE = "org.smarttube.stable"
        /** dumbtv's pin: releases are immutable, so the hash names this exact build (stable 32.56, every ABI). */
        const val SMARTTUBE_URL = "https://github.com/yuliskov/SmartTube/releases/download/32.56s/SmartTube_stable_32.56_universal.apk"
        const val SMARTTUBE_SHA256 = "4a35b90940612e048e4e9cb9e10379f7db1e84f30375994129f10a571ee04ce5"
        const val SMARTTUBE_MB = 35
        const val SMARTTUBE_BYTES = 34_922_752
        const val DNS_HOST = "dns.adguard-dns.com"
    }
}

/**
 * The undo lines a phase 6 run writes (extras.txt), one change per line, before it makes the change:
 * `appop <pkg> <op> <old mode>`, `setting <ns> <key> <old value>`, `installed <pkg>`, `favourites <old csv>`.
 * (Turning stock YouTube off goes in applied.txt like any other disable.)
 */
object Undo {
    fun appop(pkg: String, op: String, old: String) = "appop $pkg $op $old"
    fun setting(ns: String, key: String, old: String) = "setting $ns $key $old"
    fun installed(pkg: String) = "installed $pkg"
    fun favourites(csv: String) = "favourites ${csv.ifEmpty { "-" }}"

    /** What undoing [line] does, for the Restore screen. */
    fun describe(line: String): String {
        val p = line.trim().split(' ')
        return when (p.firstOrNull()) {
            "appop" -> "Let ${p.getOrElse(1) { "?" }} run in the background again"
            "setting" -> "${p.getOrElse(2) { "?" }} back to ${p.getOrElse(3) { "?" }.let { if (it == "null") "the TV's default" else it }}"
            "installed" -> "Uninstall ${p.getOrElse(1) { "?" }}"
            "favourites" -> "Home screen favourites back as they were"
            else -> line
        }
    }

    /** Put back what [lines] record, newest first. Returns one log line per change. */
    suspend fun run(tv: AdbTv, lines: List<String>): List<String> {
        val ops = ExtraOps(tv)
        val home = HomeOps(tv)
        return lines.asReversed().mapNotNull { line ->
            val p = line.trim().split(' ')
            runCatching {
                when (p.firstOrNull()) {
                    "appop" -> "${p[1]} ${p[2]} back to ${p[3]}: ${ops.setAppop(p[1], p[2], p[3]).ifEmpty { "ok" }}"
                    "setting" -> "${p[2]} back to ${p[3]}: ${ops.putSetting(p[1], p[2], p[3]).ifEmpty { "ok" }}"
                    "installed" -> "uninstall ${p[1]}: ${ops.uninstall(p[1])}"
                    "favourites" -> {
                        val now = home.config() ?: return@runCatching "favourites: the home screen did not answer"
                        val old = p[1].takeIf { it != "-" }?.split(',').orEmpty()
                        home.config(now.copy(favourites = old))
                        "favourites back: ${old.joinToString()}"
                    }
                    else -> null
                }
            }.getOrElse { "could not undo \"$line\": ${it.message}" }
        }
    }
}

/** "RUN_ANY_IN_BACKGROUND: ignore" (or "...: ignore; time=..."), else "default". */
fun Parse.appopMode(out: String, op: String): String =
    Regex("^\\s*${Regex.escape(op)}: (\\w+)", RegexOption.MULTILINE).find(out)?.groupValues?.get(1)
        ?.takeIf { it in ExtraOps.MODES } ?: "default"

/** A setting value is a plain number or word (the value goes into a shell command). */
fun Parse.settingValue(v: String): String = v.also { require(Regex("^[A-Za-z0-9_.:-]+$").matches(it)) { "not a setting value: $v" } }

/** A DNS hostname such as dns.adguard-dns.com, or an error. */
fun Parse.hostname(h: String): String = h.trim().also {
    require(Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$").matches(it)) { "not a hostname: $h" }
}

/** ping found an address: "PING host (1.2.3.4)"; "unknown host" means the lookup failed. */
fun Parse.resolved(ping: String): Boolean = Regex("^PING \\S+ \\(([0-9a-fA-F.:]+)\\)", RegexOption.MULTILINE).containsMatchIn(ping)
