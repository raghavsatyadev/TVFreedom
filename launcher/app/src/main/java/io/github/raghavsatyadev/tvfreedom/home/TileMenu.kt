package io.github.raghavsatyadev.tvfreedom.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup

/** Menu for a tile (long press OK or Menu): add to / remove from favourites, move a favourite, app info. */
object TileMenu {

    /** onChanged gets the package whose favourite state or place changed, to reload and refocus it. */
    fun show(
        act: Activity, root: ViewGroup, anchor: View, adapter: AppAdapter, position: Int, onChanged: (String) -> Unit,
    ) {
        val e = adapter.entryAt(position) ?: return
        val favs = adapter.favourites()
        val density = act.resources.displayMetrics.density
        val menu = GlassMenu(act, root, maxOf(anchor.width, (240 * density).toInt()))
        menu.addTitle(e.label)
        if (favs.contains(e.pkg)) {
            val installed = adapter.installedFavourites()
            val at = installed.indexOf(e.pkg)
            if (at > 0) {
                menu.addRow(R.drawable.ic_arrow_left, act.getString(R.string.move_left), -1) {
                    favs.move(e.pkg, -1, installed)
                    onChanged(e.pkg)
                }
            }
            if (at >= 0 && at < installed.size - 1) {
                menu.addRow(R.drawable.ic_arrow_right, act.getString(R.string.move_right), -1) {
                    favs.move(e.pkg, 1, installed)
                    onChanged(e.pkg)
                }
            }
            menu.addRow(R.drawable.ic_star_off, act.getString(R.string.remove_favourite), -1) {
                favs.remove(e.pkg)
                onChanged(e.pkg)
            }
        } else {
            menu.addRow(R.drawable.ic_star, act.getString(R.string.add_favourite), -1) {
                favs.add(e.pkg)
                onChanged(e.pkg)
            }
        }
        menu.addSeparator()
        menu.addRow(R.drawable.ic_settings, act.getString(R.string.app_info), -1) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                act.startActivity(i)
            } catch (ignored: ActivityNotFoundException) {
            }
        }
        menu.showBelow(anchor)
    }

    /** Picker behind the "+" tile: every app that is not a favourite yet, alphabetical. */
    fun showAdd(act: Activity, root: ViewGroup, anchor: View, adapter: AppAdapter, onChanged: (String) -> Unit) {
        val favs = adapter.favourites()
        val density = act.resources.displayMetrics.density
        val menu = GlassMenu(act, root, maxOf(anchor.width, (280 * density).toInt()))
        for (e in adapter.apps()) {
            if (favs.contains(e.pkg)) continue
            menu.addAppRow(e.icon, e.label) {
                favs.add(e.pkg)
                onChanged(e.pkg)
            }
        }
        if (!menu.isEmpty()) menu.showAbove(anchor)
    }
}
