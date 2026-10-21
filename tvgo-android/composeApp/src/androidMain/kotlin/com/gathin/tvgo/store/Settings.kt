package com.gathin.tvgo.store

import android.content.SharedPreferences

/** Which AI plans, the optional Claude key, and the last TV. Private app storage, no backup. */
class Settings(private val prefs: SharedPreferences) {
    enum class Ai(val label: String, val hint: String) {
        TERMUX("Claude in Termux", "Uses your Claude subscription"),
        NANO("Gemini Nano", "Runs on this phone"),
        CLAUDE("Claude API", "Uses an API key"),
        NONE("No AI", "Only the safety guard and your lists"),
    }

    var ai: Ai
        get() = runCatching { Ai.valueOf(prefs.getString("ai", Ai.TERMUX.name)!!) }.getOrDefault(Ai.TERMUX)
        set(v) = prefs.edit().putString("ai", v.name).apply()

    /** Which Claude plans in Termux: a Claude Code model alias. Opus by default, the most capable for a first debloat. */
    var termuxModel: TermuxModel
        get() = runCatching { TermuxModel.valueOf(prefs.getString("termux_model", TermuxModel.OPUS.name)!!) }
            .getOrDefault(TermuxModel.OPUS)
        set(v) = prefs.edit().putString("termux_model", v.name).apply()

    enum class TermuxModel(val alias: String, val label: String, val hint: String) {
        OPUS("opus", "Opus", "Most capable: best for a TV's first debloat"),
        SONNET("sonnet", "Sonnet", "Faster, and uses less of your plan"),
    }

    var claudeKey: String
        get() = prefs.getString("claude_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_key", v.trim()).apply()

    var claudeModel: String
        get() = prefs.getString("claude_model", DEFAULT_CLAUDE_MODEL) ?: DEFAULT_CLAUDE_MODEL
        set(v) = prefs.edit().putString("claude_model", v.trim().ifEmpty { DEFAULT_CLAUDE_MODEL }).apply()

    var uninstallUserApps: Boolean
        get() = prefs.getBoolean("uninstall_user_apps", false)
        set(v) = prefs.edit().putBoolean("uninstall_user_apps", v).apply()

    /** Turn off the TV's firmware update services when this model's own list names them. On unless the user keeps updates. */
    var stopUpdates: Boolean
        get() = prefs.getBoolean("stop_updates", true)
        set(v) = prefs.edit().putBoolean("stop_updates", v).apply()

    /** The TV home screen's background from the company logo, #RRGGBB; "" for the plain black one. */
    var brandColor: String
        get() = prefs.getString("brand_color", "") ?: ""
        set(v) = prefs.edit().putString("brand_color", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    var lastAddress: String
        get() = prefs.getString("last_address", "") ?: ""
        set(v) = prefs.edit().putString("last_address", v).apply()

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-opus-5-5"
    }
}
