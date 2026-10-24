package io.github.raghavsatyadev.tvfreedom.store

/** Which AI plans, the optional Claude key, and the last TV. Private app storage, no backup. */
class Settings(private val prefs: Prefs) {
    enum class Ai(val label: String, val hint: String) {
        TERMUX("Claude in Termux", "Uses your Claude subscription"),
        NANO("Gemini Nano", "Runs on this phone"),
        CLAUDE("Claude API", "Uses an API key"),
        NONE("No AI", "Lists for 130+ TV models"),
    }

    var ai: Ai
        get() = runCatching { Ai.valueOf(prefs.string("ai", Ai.NONE.name)) }.getOrDefault(Ai.NONE)
        set(v) = prefs.putString("ai", v.name)

    /** Which Claude plans in Termux: a Claude Code model alias. Opus by default, the most capable for a first debloat. */
    var termuxModel: TermuxModel
        get() = runCatching { TermuxModel.valueOf(prefs.string("termux_model", TermuxModel.OPUS.name)) }
            .getOrDefault(TermuxModel.OPUS)
        set(v) = prefs.putString("termux_model", v.name)

    enum class TermuxModel(val alias: String, val label: String, val hint: String) {
        OPUS("opus", "Opus", "Most capable: best for a TV's first debloat"),
        SONNET("sonnet", "Sonnet", "Faster, and uses less of your plan"),
    }

    var claudeKey: String
        get() = prefs.string("claude_key", "")
        set(v) = prefs.putString("claude_key", v.trim())

    var claudeModel: String
        get() = prefs.string("claude_model", DEFAULT_CLAUDE_MODEL)
        set(v) = prefs.putString("claude_model", v.trim().ifEmpty { DEFAULT_CLAUDE_MODEL })

    var uninstallUserApps: Boolean
        get() = prefs.bool("uninstall_user_apps", false)
        set(v) = prefs.putBool("uninstall_user_apps", v)

    /** Turn off the TV's firmware update services when this model's own list names them. On unless the user keeps updates. */
    var stopUpdates: Boolean
        get() = prefs.bool("stop_updates", true)
        set(v) = prefs.putBool("stop_updates", v)

    /** The TV home screen's background from the company logo, #RRGGBB; "" for the plain black one. */
    var brandColor: String
        get() = prefs.string("brand_color", "")
        set(v) = prefs.putString("brand_color", v)

    var onboarded: Boolean
        get() = prefs.bool("onboarded", false)
        set(v) = prefs.putBool("onboarded", v)

    var lastAddress: String
        get() = prefs.string("last_address", "")
        set(v) = prefs.putString("last_address", v)

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-opus-5-5"
    }
}
