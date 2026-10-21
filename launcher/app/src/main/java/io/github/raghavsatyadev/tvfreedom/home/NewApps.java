package io.github.raghavsatyadev.tvfreedom.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A newly installed app: the home screen offers to add it to the favourites the first time it sees it.
 * The apps seen so far are kept in prefs; the very first start only records them, so nothing pops up after setup.
 */
final class NewApps {

    private static final String SEEN = "seen_apps";
    private static final int MAX_OFFERED = 3;

    private NewApps() {
    }

    /** Call after the adapter reloaded; onAdded gets the package that became a favourite. */
    static void offer(Activity act, ViewGroup root, View anchor, AppAdapter adapter, Consumer<String> onAdded) {
        SharedPreferences prefs = Config.prefs(act);
        List<AppAdapter.Entry> apps = adapter.apps();
        Set<String> now = new HashSet<>();
        for (AppAdapter.Entry e : apps) now.add(e.pkg);
        String saved = prefs.getString(SEEN, null);
        prefs.edit().putString(SEEN, TextUtils.join(",", now)).apply();
        if (saved == null) return;
        Set<String> seen = new HashSet<>(Arrays.asList(saved.split(",")));
        Favourites favs = adapter.favourites();
        List<AppAdapter.Entry> fresh = new ArrayList<>();
        for (AppAdapter.Entry e : apps) {
            if (!seen.contains(e.pkg) && !favs.contains(e.pkg) && fresh.size() < MAX_OFFERED) fresh.add(e);
        }
        if (fresh.isEmpty()) return;
        float density = act.getResources().getDisplayMetrics().density;
        GlassMenu menu = new GlassMenu(act, root, (int) (320 * density));
        for (AppAdapter.Entry e : fresh) {
            menu.addRow(R.drawable.ic_star, act.getString(R.string.add_new_favourite, e.label), -1, () -> {
                favs.add(e.pkg);
                onAdded.accept(e.pkg);
            });
        }
        menu.addSeparator();
        menu.addRow(R.drawable.ic_star_off, act.getString(R.string.not_now), -1, () -> {
        });
        menu.showBelow(anchor);
    }
}
