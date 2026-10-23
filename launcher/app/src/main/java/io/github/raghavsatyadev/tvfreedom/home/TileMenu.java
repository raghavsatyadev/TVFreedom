package io.github.raghavsatyadev.tvfreedom.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/** Menu for a tile (long press OK or Menu): add to / remove from favourites, move a favourite, app info. */
final class TileMenu {

    private TileMenu() {
    }

    /** onChanged gets the package whose favourite state or place changed, to reload and refocus it. */
    static void show(Activity act, ViewGroup root, View anchor, AppAdapter adapter, int position,
                     java.util.function.Consumer<String> onChanged) {
        AppAdapter.Entry e = adapter.entryAt(position);
        if (e == null) return;
        Favourites favs = adapter.favourites();
        float density = act.getResources().getDisplayMetrics().density;
        GlassMenu menu = new GlassMenu(act, root, Math.max(anchor.getWidth(), (int) (240 * density)));
        if (favs.contains(e.pkg)) {
            List<String> installed = adapter.installedFavourites();
            int at = installed.indexOf(e.pkg);
            if (at > 0) {
                menu.addRow(R.drawable.ic_arrow_left, act.getString(R.string.move_left), -1, () -> {
                    favs.move(e.pkg, -1, installed);
                    onChanged.accept(e.pkg);
                });
            }
            if (at >= 0 && at < installed.size() - 1) {
                menu.addRow(R.drawable.ic_arrow_right, act.getString(R.string.move_right), -1, () -> {
                    favs.move(e.pkg, 1, installed);
                    onChanged.accept(e.pkg);
                });
            }
            menu.addRow(R.drawable.ic_star_off, act.getString(R.string.remove_favourite), -1, () -> {
                favs.remove(e.pkg);
                onChanged.accept(e.pkg);
            });
        } else {
            menu.addRow(R.drawable.ic_star, act.getString(R.string.add_favourite), -1, () -> {
                favs.add(e.pkg);
                onChanged.accept(e.pkg);
            });
        }
        menu.addSeparator();
        menu.addRow(R.drawable.ic_settings, act.getString(R.string.app_info), -1, () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                act.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
        });
        menu.showBelow(anchor);
    }

    /** Picker behind the "+" tile: every app that is not a favourite yet, alphabetical. */
    static void showAdd(Activity act, ViewGroup root, View anchor, AppAdapter adapter,
                        java.util.function.Consumer<String> onChanged) {
        Favourites favs = adapter.favourites();
        float density = act.getResources().getDisplayMetrics().density;
        GlassMenu menu = new GlassMenu(act, root, Math.max(anchor.getWidth(), (int) (280 * density)));
        for (AppAdapter.Entry e : adapter.apps()) {
            if (favs.contains(e.pkg)) continue;
            menu.addAppRow(e.icon, e.label, () -> {
                favs.add(e.pkg);
                onChanged.accept(e.pkg);
            });
        }
        if (!menu.isEmpty()) menu.showAbove(anchor, root);
    }
}
