package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The favourite apps (package names, in dock order), changed from the remote via the tile menu.
 * Starts from res/values/config.xml (dock_apps) until the first change is saved, here or from tvgo
 * ({@link ConfigReceiver}).
 */
final class Favourites {

    static final String KEY = "favourites";

    private final Context ctx;
    private final List<String> list = new ArrayList<>();

    Favourites(Context ctx) {
        this.ctx = ctx;
        String saved = prefs().getString(KEY, null);
        if (saved == null) list.addAll(Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        else if (!saved.isEmpty()) list.addAll(Arrays.asList(saved.split(",")));
    }

    /** Every favourite in dock order, installed or not. */
    List<String> all() {
        return new ArrayList<>(list);
    }

    /** Dock order; index -1 means not a favourite. */
    int indexOf(String pkg) {
        return list.indexOf(pkg);
    }

    boolean contains(String pkg) {
        return list.contains(pkg);
    }

    void add(String pkg) {
        if (!list.contains(pkg)) list.add(pkg);
        save();
    }

    void remove(String pkg) {
        list.remove(pkg);
        save();
    }

    /**
     * Move pkg by delta places among the favourites that are installed (installed in dock order), so a
     * missing app in the saved list never makes a move look like it did nothing.
     */
    void move(String pkg, int delta, List<String> installed) {
        int at = installed.indexOf(pkg), to = at + delta;
        if (at < 0 || to < 0 || to >= installed.size()) return;
        String other = installed.get(to);
        int a = list.indexOf(pkg), b = list.indexOf(other);
        list.set(a, other);
        list.set(b, pkg);
        save();
    }

    private void save() {
        prefs().edit().putString(KEY, TextUtils.join(",", list)).apply();
    }

    private SharedPreferences prefs() {
        return Config.prefs(ctx);
    }
}
