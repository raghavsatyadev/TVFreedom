package io.github.raghavsatyadev.tvfreedom.core

/**
 * Hard keep rules, ported verbatim from tvfreedom_core/guard.py (raw strings hold the same regex text).
 * Code, not the AI, has the last word: a guard hit is never removed.
 */
object Guard {
    const val OTA = "OTA"

    /** (reason, regex on the package name). First match wins. */
    val RULES: List<Pair<String, String>> = listOf(
        "core" to ("""^android$|^android\.|\.auto_generated_rro|overlay|^com\.android\.(systemui|settings|shell""" +
            """|providers\.|server\.|internal\.|keychain|certinstaller|externalstorage|location""" +
            """|packageinstaller|permissioncontroller|statementservice|dynsystem|se$|vndk|apex""" +
            """|media(\.|$)|ext\.|devicelockcontroller|sdksandbox|uwb|nfc|phone)""" +
            """|^com\.google\.android\.(gms|gsf|packageinstaller|permissioncontroller|ext\.|modulemetadata""" +
            """|webview|trichromelibrary|configupdater|partnersetup|tvrecommendations)$""" +
            """|^com\.android\.vending$|webview|framework|systemui|provider"""),
        "network" to """wifi|network|ethernet|tethering|connectivity|captiveportal|\.dns|vpn""",
        "input/remote" to ("""input|keyboard|\.ime\b|latin|remote|\.rcu|keyhandler|keymap|bluetooth|\.bt\.|pairing""" +
            """|controller|gamepad|\.tv\.remote"""),
        "tv inputs" to ("""^com\.(google\.)?android\.tv$|tvinput|tv\.input|hdmi|\.cec|livetv|tvservice|^mitv\.service$|tuner|tvsettings""" +
            """|tv\.settings|tvprovider|\.tv\.framework"""),
        "cast" to """cast|mediashell|dial|miracast|airplay|\.dlna""",
        "voice" to """katniss|voice|assistant|speech|\.tts|texttospeech|hotword|\.asr|mic\b""",
        OTA to """\bota|update|upgrad|firmware|abupdate|recovery|\.fota""",
        "launcher/setup" to """launcher|setup|wraith|\.home$|gathin""",
        "vendor HAL" to ("""^com\.(mediatek|mstar|droidlogic|amlogic|realtek|hisilicon|rockchip|qualcomm|qti""" +
            """|nvidia\.(shield\.remote|tegra|ota|blakepairing)|sony\.dtv\.(tvx|tvinput|hdmi|sonysystem)""" +
            """|tcl\.(tv$|tvinput|common|systemserver)|hisense\.(hiservice|tv$)|tpv\.|philips\.tv\.core)""" +
            """|^org\.droidtv\.(tv|settings|playtv|channels|euinstallersat|tvserver)|^vendor\.|^com\.android\.cts"""),
    )
    private val compiled = RULES.map { (why, rx) -> why to Regex(rx) }
    private val PROTECTED_PATHS = listOf("/apex/", "/system/framework/")

    /** Why this package must stay, or "" if the guard allows removing it. */
    fun keepReason(
        pkg: String,
        apkPath: String = "",
        home: Set<String> = emptySet(),
        ime: String = "",
        keep: Set<String> = emptySet(),
        uid: Int? = null,
    ): String {
        if (pkg in keep) return "your keep list"
        if (pkg in home) return "launcher/setup (HOME)"
        if (ime.isNotEmpty() && ime.substringBefore('/') == pkg) return "input/remote (current keyboard)"
        if (uid != null && uid < 10000) return "core (system uid)"
        if (PROTECTED_PATHS.any { apkPath.startsWith(it) }) return "core"
        return compiled.firstOrNull { (_, rx) -> rx.containsMatchIn(pkg) }?.first ?: ""
    }

    /** Packages the user always keeps (lists/keep.txt: one package per line, # comments). */
    fun parseKeep(text: String): Set<String> =
        text.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
}
