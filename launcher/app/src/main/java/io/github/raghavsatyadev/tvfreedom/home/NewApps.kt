package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.view.View
import android.view.ViewGroup

/**
 * A newly installed app: the home screen offers to add it to the favourites the first time it sees it.
 * The apps seen so far are kept in prefs; the very first start only records them, so nothing pops up after setup.
 */
object NewApps {

    private const val SEEN = "seen_apps"
    private const val MAX_OFFERED = 3

    /** Call after the adapter reloaded; onAdded gets the package that became a favourite. */
    fun offer(act: Activity, root: ViewGroup, anchor: View, adapter: AppAdapter, onAdded: (String) -> Unit) {
        val prefs = Config.prefs(act)
        val apps = adapter.apps()
        val now = apps.mapTo(HashSet()) { it.pkg }
        val saved = prefs.getString(SEEN, null)
        prefs.edit().putString(SEEN, now.joinToString(",")).apply()
        if (saved == null) return
        val seen = saved.split(",").toHashSet()
        val favs = adapter.favourites()
        val fresh = apps.filter { it.pkg !in seen && !favs.contains(it.pkg) }.take(MAX_OFFERED)
        if (fresh.isEmpty()) return
        val density = act.resources.displayMetrics.density
        val menu = GlassMenu(act, root, (320 * density).toInt())
        for (e in fresh) {
            menu.addRow(R.drawable.ic_star, act.getString(R.string.add_new_favourite, e.label), -1) {
                favs.add(e.pkg)
                onAdded(e.pkg)
            }
        }
        menu.addSeparator()
        menu.addRow(R.drawable.ic_star_off, act.getString(R.string.not_now), -1) {}
        menu.showBelow(anchor)
    }
}
