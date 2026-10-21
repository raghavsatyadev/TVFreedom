package io.github.raghavsatyadev.tvfreedom.tv

import kotlin.math.roundToInt

/** The launcher's settings, as its ConfigReceiver reports and takes them. */
data class LauncherConfig(
    val favourites: List<String> = emptyList(),
    val columns: Int = 4,
    val place: String = "",
    val latitude: String = "",
    val longitude: String = "",
    /** Calendar display names separated by |. */
    val calendars: String = "",
    /** The home screen's background as #RRGGBB; "" is black. */
    val brandColor: String = "",
    /** Size in bytes of the logo on the TV (0: none). Only reported; the logo goes through [HomeOps.setLogo]. */
    val logoBytes: Long = 0,
)

/**
 * The home-screen side of the TV (tv.py phases 4 and 5): install and set up the launcher, swap the screensaver,
 * and read RAM and the screen. Everything that goes into a command is a package name, a checked number, or quoted.
 */
class HomeOps(private val tv: Tv) {

    suspend fun sdk(): Int = tv.sh("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0

    /** The launcher's versionCode on the TV, or null if it isn't installed. */
    suspend fun launcherVersion(): Long? {
        if (LAUNCHER !in Parse.names(tv.sh("pm list packages $LAUNCHER"))) return null
        return Parse.versionCode(tv.sh("dumpsys package $LAUNCHER")) ?: 0
    }

    /** Stream [apk] into the package installer (no file left on the TV). Returns its answer, "Success" when it worked. */
    suspend fun install(apk: ByteArray): String =
        tv.exec("cmd package install -r -S ${apk.size}", apk, 180_000).decodeToString().trim()

    suspend fun grant(permission: String) = tv.sh("pm grant $LAUNCHER ${Parse.safePackage(permission)}")

    /** Send [c] to the launcher, or with null only read what it uses now. Null if it didn't answer (not installed, or v2). */
    suspend fun config(c: LauncherConfig? = null): LauncherConfig? {
        val cmd = buildString {
            append("am broadcast -f 0x20 -n $LAUNCHER/.ConfigReceiver")
            if (c != null) {
                append(" --es favourites ").append(quote(c.favourites.joinToString(",") { Parse.safePackage(it) }))
                append(" --ei columns ").append(c.columns.coerceIn(1, 8))
                append(" --es weather_place ").append(quote(c.place))
                append(" --es weather_latitude ").append(quote(Parse.coordinate(c.latitude)))
                append(" --es weather_longitude ").append(quote(Parse.coordinate(c.longitude)))
                append(" --es dream_calendars ").append(quote(c.calendars))
                append(" --es brand_color ").append(quote(Parse.hexColor(c.brandColor)))
            }
        }
        return Parse.launcherConfig(tv.sh(cmd))
    }

    /**
     * Put the company logo [png] on the launcher (its LogoProvider writes it into the app's own files), or with null
     * take it off. Returns content's output, empty when it worked.
     */
    suspend fun setLogo(png: ByteArray?): String =
        if (png == null) tv.sh("content delete --uri $LOGO_URI").trim()
        // exec: can't close the TV's stdin, so content would wait for more forever: head stops it after the logo's bytes.
        else tv.exec("head -c ${png.size} | content write --uri $LOGO_URI", png, 60_000).decodeToString().trim()

    /** Close the launcher so it reads its settings again when it next opens. */
    suspend fun stopLauncher() = tv.sh("am force-stop $LAUNCHER")

    suspend fun openLauncher() = tv.sh("am start -n $LAUNCHER/.MainActivity")

    suspend fun screensaver(): String = tv.sh("settings get secure $SCREENSAVER").trim()

    /** Set the screensaver; "null" or "" puts back "no value" (what an untouched TV has). */
    suspend fun setScreensaver(component: String): String =
        if (component.isEmpty() || component == "null") tv.sh("settings delete secure $SCREENSAVER")
        else tv.sh("settings put secure $SCREENSAVER ${Parse.safeComponent(component)}")

    /** Start the screensaver now (Android 11+: the dream service; older: System UI's Somnambulator). */
    suspend fun startDream(sdk: Int): String =
        if (sdk >= 30) tv.sh("service call dreams 1") else tv.sh("am start -n com.android.systemui/.Somnambulator")

    suspend fun dreaming(): String = tv.sh("dumpsys dreams")

    /** MemAvailable in MB, or null if /proc/meminfo can't be read. */
    suspend fun freeRamMb(): Int? = Parse.memAvailableMb(tv.sh("cat /proc/meminfo"))

    /** A PNG of the TV's screen, or null. */
    suspend fun screenshot(): ByteArray? = Parse.png(tv.exec("screencap -p", timeoutMs = 60_000))

    /** Apps that show on a TV home screen (they have a LEANBACK_LAUNCHER activity). */
    suspend fun tvApps(): List<String> =
        tv.sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER")
            .lines().filter { '/' in it }.map { it.trim().substringBefore('/') }.distinct()

    /** Tiles per row that fit this TV, from its screen size and density. */
    suspend fun columns(): Int = Parse.columns(tv.sh("wm size"), tv.sh("wm density"))

    companion object {
        const val LAUNCHER = "com.gathin.tvhome"
        const val CLOCK_DREAM = "$LAUNCHER/.ClockDream"
        const val SCREENSAVER = "screensaver_components"
        const val LOGO_URI = "content://$LAUNCHER.logo/logo"
        /** Wi-Fi name on the home screen (Android 10 needs location for it), and holidays on the screensaver. */
        val PERMISSIONS = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_CALENDAR")

        /** One shell word: single quotes, with any ' inside closed, escaped and reopened. */
        fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}

/** Parsers for the home-screen commands; pure so they can be unit tested. */
fun Parse.versionCode(dumpsys: String): Long? = Regex("versionCode=(\\d+)").find(dumpsys)?.groupValues?.get(1)?.toLong()

/** pkg/.Class components, comma separated as the setting allows, nothing else (the value goes into a shell command). */
fun Parse.safeComponent(c: String): String {
    require(Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.]+(,[A-Za-z0-9_.]+/[A-Za-z0-9_.]+)*$").matches(c)) { "not a component: $c" }
    return c
}

/** A latitude or longitude as the launcher wants it (dot decimal), or "" if [s] isn't one. */
fun Parse.coordinate(s: String): String = s.trim().takeIf { Regex("^-?\\d{1,3}(\\.\\d+)?$").matches(it) } ?: ""

/** The launcher's answer to am broadcast: Broadcast completed: result=0, data="ok favourites=…&columns=…". */
fun Parse.launcherConfig(out: String): LauncherConfig? {
    val query = Regex("data=\"ok ([^\"]*)\"").find(out)?.groupValues?.get(1) ?: return null
    val v = query.split('&').filter { '=' in it }.associate {
        it.substringBefore('=') to Parse.formDecode(it.substringAfter('='))
    }
    return LauncherConfig(
        favourites = v["favourites"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        columns = v["columns"]?.toIntOrNull() ?: 4,
        place = v["weather_place"].orEmpty(),
        latitude = v["weather_latitude"].orEmpty(),
        longitude = v["weather_longitude"].orEmpty(),
        calendars = v["dream_calendars"].orEmpty(),
        brandColor = v["brand_color"].orEmpty(),
        logoBytes = v["logo"]?.toLongOrNull() ?: 0,
    )
}

/** application/x-www-form-urlencoded value: + is a space, %XX bytes are UTF-8. A bad escape stays as it is. */
fun Parse.formDecode(s: String): String {
    val out = okio.Buffer()
    var i = 0
    while (i < s.length) {
        val hex = if (s[i] == '%' && i + 3 <= s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
        when {
            s[i] == '+' -> { out.writeByte(' '.code); i++ }
            hex != null -> { out.writeByte(hex); i += 3 }
            else -> {
                var j = i + 1
                while (j < s.length && s[j] != '%' && s[j] != '+') j++
                out.writeUtf8(s, i, j); i = j
            }
        }
    }
    return out.readUtf8()
}

/** #RRGGBB in upper case, or "" if [s] isn't one (the value goes into a shell command). */
fun Parse.hexColor(s: String): String = s.trim().takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }?.uppercase() ?: ""

fun Parse.memAvailableMb(meminfo: String): Int? =
    Regex("^MemAvailable:\\s+(\\d+) kB", RegexOption.MULTILINE).find(meminfo)?.groupValues?.get(1)?.toInt()?.div(1024)

private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

/** The PNG inside screencap's output: some vendors print debug text before it. */
fun Parse.png(bytes: ByteArray): ByteArray? {
    for (i in 0..bytes.size - PNG.size) {
        if ((PNG.indices).all { bytes[i + it] == PNG[it] }) return bytes.copyOfRange(i, bytes.size)
    }
    return null
}

/**
 * Tiles per row: the launcher's tiles are about 240 dp wide with their gaps, so a 960 dp wide TV (1080p at 320 dpi,
 * or 4K at 640) gets 4. Uses the override size or density when the TV has one. 4 if either can't be read.
 */
fun Parse.columns(wmSize: String, wmDensity: String): Int {
    val size = Regex("(\\d+)x(\\d+)").findAll(wmSize).lastOrNull() ?: return 4
    val density = Regex("(\\d+)").findAll(wmDensity).lastOrNull()?.value?.toIntOrNull()?.takeIf { it > 0 } ?: return 4
    val widthPx = maxOf(size.groupValues[1].toInt(), size.groupValues[2].toInt())
    return (widthPx * 160.0 / density / 240.0).roundToInt().coerceIn(3, 6)
}
