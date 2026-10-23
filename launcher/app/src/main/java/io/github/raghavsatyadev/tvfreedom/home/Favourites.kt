package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.content.SharedPreferences

/**
 * The favourite apps (package names, in dock order), changed from the remote via the tile menu.
 * Starts from res/values/config.xml (dock_apps) until the first change is saved, here or from TVFreedom
 * ([ConfigReceiver]).
 */
class Favourites(private val ctx: Context) {

    private val list = ArrayList<String>()

    init {
        val saved = prefs().getString(KEY, null)
        if (saved == null) list.addAll(ctx.resources.getStringArray(R.array.dock_apps))
        else if (saved.isNotEmpty()) list.addAll(saved.split(","))
    }

    /** Every favourite in dock order, installed or not. */
    fun all(): List<String> = ArrayList(list)

    /** Dock order; index -1 means not a favourite. */
    fun indexOf(pkg: String): Int = list.indexOf(pkg)

    fun contains(pkg: String): Boolean = pkg in list

    fun add(pkg: String) {
        if (pkg !in list) list.add(pkg)
        save()
    }

    fun remove(pkg: String) {
        list.remove(pkg)
        save()
    }

    /**
     * Move pkg by delta places among the favourites that are installed (installed in dock order), so a
     * missing app in the saved list never makes a move look like it did nothing.
     */
    fun move(pkg: String, delta: Int, installed: List<String>) {
        val at = installed.indexOf(pkg)
        val to = at + delta
        if (at < 0 || to < 0 || to >= installed.size) return
        val other = installed[to]
        val a = list.indexOf(pkg)
        val b = list.indexOf(other)
        list[a] = other
        list[b] = pkg
        save()
    }

    private fun save() {
        prefs().edit().putString(KEY, list.joinToString(",")).apply()
    }

    private fun prefs(): SharedPreferences = Config.prefs(ctx)

    companion object {
        const val KEY = "favourites"
    }
}
