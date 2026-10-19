package com.gathin.tvgo.store

import android.content.SharedPreferences

/** Which AI plans, the optional Claude key, and the last TV. Private app storage, no backup. */
class Settings(private val prefs: SharedPreferences) {
    enum class Ai(val label: String) { NANO("Gemini Nano (on this phone)"), CLAUDE("Claude (API key)"), NONE("No AI: lists and guard only") }

    var ai: Ai
        get() = runCatching { Ai.valueOf(prefs.getString("ai", Ai.NANO.name)!!) }.getOrDefault(Ai.NANO)
        set(v) = prefs.edit().putString("ai", v.name).apply()

    var claudeKey: String
        get() = prefs.getString("claude_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_key", v.trim()).apply()

    var claudeModel: String
        get() = prefs.getString("claude_model", DEFAULT_CLAUDE_MODEL) ?: DEFAULT_CLAUDE_MODEL
        set(v) = prefs.edit().putString("claude_model", v.trim().ifEmpty { DEFAULT_CLAUDE_MODEL }).apply()

    var uninstallUserApps: Boolean
        get() = prefs.getBoolean("uninstall_user_apps", false)
        set(v) = prefs.edit().putBoolean("uninstall_user_apps", v).apply()

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
