package io.github.raghavsatyadev.tvfreedom.tv

import io.github.raghavsatyadev.tvfreedom.core.DISABLE
import io.github.raghavsatyadev.tvfreedom.core.Facts
import io.github.raghavsatyadev.tvfreedom.core.ListEntry
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.core.UNINSTALL

/** Runs one shell command on the TV and returns stdout+stderr. */
fun interface Shell {
    suspend fun sh(cmd: String): String
}

/**
 * Read-only queries and the only writes TVFreedom makes (port of tvfreedom_core/adbio.py and tv.py apply/restore).
 * Writes are limited to pm disable-user / uninstall --user 0 and their undo (pm enable / install-existing).
 */
class TvOps(private val shell: Shell, private val host: String) {

    suspend fun getprop(key: String) = shell.sh("getprop $key").trim()

    suspend fun isTv(): Boolean =
        shell.sh("pm has-feature android.software.leanback").trim() == "true" ||
            "tv" in getprop("ro.build.characteristics")

    suspend fun identify() = TvInfo(
        host = host,
        model = getprop("ro.product.model"),
        device = getprop("ro.product.device"),
        brand = getprop("ro.product.brand"),
        android = getprop("ro.build.version.release"),
        sdk = getprop("ro.build.version.sdk"),
        serial = getprop("ro.serialno"),
        home = homeActivity(),
        ime = shell.sh("settings get secure default_input_method").trim(),
    )

    suspend fun homeActivity(): String =
        shell.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim() }.lastOrNull { '/' in it } ?: ""

    /** Every package with a HOME activity (the guard never touches these). */
    suspend fun homePackages(): Set<String> =
        shell.sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().filter { '/' in it }.map { it.trim().substringBefore('/') }.toSet()

    suspend fun packages(flag: String = ""): Set<String> = Parse.names(shell.sh("pm list packages $flag".trim()))

    suspend fun paths(): Map<String, String> = Parse.paths(shell.sh("pm list packages -f"))

    suspend fun facts() = Facts(
        paths = paths(),
        disabled = packages("-d"),
        user = packages("-3"),
        home = homePackages(),
        uids = Parse.uids(shell.sh("pm list packages -U")),
    )

    suspend fun focusedPackage(): String = Parse.focused(shell.sh("dumpsys window"))

    suspend fun key(code: String) = shell.sh("input keyevent $code")

    /** The files a backup keeps (same names as tv.py backup, minus the screenshot). */
    suspend fun backupFiles(): Map<String, String> = linkedMapOf(
        "packages-all.txt" to "pm list packages -f",
        "packages-disabled.txt" to "pm list packages -d",
        "packages-enabled.txt" to "pm list packages -e",
        "packages-user.txt" to "pm list packages -3",
        "getprop.txt" to "getprop",
        "settings-global.txt" to "settings list global",
        "settings-secure.txt" to "settings list secure",
        "settings-system.txt" to "settings list system",
        "home.txt" to "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
    ).mapValues { (_, cmd) -> shell.sh(cmd) + "\n" }

    /** One change per package, in list order. Returns the TV's answer for the log. */
    suspend fun apply(e: ListEntry): String {
        val pkg = Parse.safePackage(e.pkg)
        // uninstall --user 0 keeps the APK on the system partition: install-existing brings it back
        return if (e.action == UNINSTALL) shell.sh("pm uninstall --user 0 $pkg")
        else shell.sh("pm disable-user --user 0 $pkg")
    }

    /** Undo one list entry (tv.py restore). */
    suspend fun undo(e: ListEntry): String {
        val pkg = Parse.safePackage(e.pkg)
        return if (e.action == DISABLE) shell.sh("pm enable $pkg") else reinstall(pkg)
    }

    suspend fun enable(pkg: String) = shell.sh("pm enable ${Parse.safePackage(pkg)}")

    suspend fun reinstall(pkg: String): String {
        val out = shell.sh("cmd package install-existing ${Parse.safePackage(pkg)}")
        return if ("installed for user" in out) out else "$out -> reinstall from Play Store: market://details?id=$pkg"
    }
}

/** Parsers for pm / dumpsys output; pure so they can be unit tested. */
object Parse {
    private val PKG = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

    /** A package name is all a list or the AI can put into a shell command; anything else is refused. */
    fun safePackage(pkg: String): String {
        require(PKG.matches(pkg)) { "not a package name: $pkg" }
        return pkg
    }

    fun names(out: String): Set<String> =
        out.lines().filter { it.startsWith("package:") }.map { it.substring(8).trim() }.toSet()

    fun paths(out: String): Map<String, String> =
        Regex("^package:(.+)=([^=\\s]+)\\r?$", RegexOption.MULTILINE).findAll(out)
            .associate { it.groupValues[2] to it.groupValues[1] }

    /** Paths only (the apk side), for diffing a backup's packages-all.txt. */
    fun backupNames(out: String): Set<String> =
        Regex("^package:.+=([^=\\s]+)\\r?$", RegexOption.MULTILINE).findAll(out).map { it.groupValues[1] }.toSet()

    fun uids(out: String): Map<String, Int> =
        Regex("^package:(\\S+) uid:(\\d+)", RegexOption.MULTILINE).findAll(out)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    fun focused(out: String): String {
        val m = Regex("mCurrentFocus=Window\\{\\S+ \\S+ ([\\w.]+)/").find(out)
            ?: Regex("mFocusedApp=.*? ([\\w.]+)/").find(out)
        return m?.groupValues?.get(1) ?: ""
    }
}
