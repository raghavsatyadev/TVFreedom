package io.github.raghavsatyadev.tvfreedom.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Takes settings from adb, so the TVFreedom phone app can set up the home screen without rebuilding the APK:
 * <pre>
 * am broadcast -f 0x20 -n io.github.raghavsatyadev.tvfreedom.home/.ConfigReceiver --es favourites com.a,com.b --ei columns 4 \
 *     --es weather_place 'Your City' --es weather_latitude 12.34 --es weather_longitude 56.78 \
 *     --es dream_calendars 'Holidays|Family' --es brand_color '#123456'
 * </pre>
 * Only extras that are present are changed; an empty string clears a setting back to "none". The result data is
 * "ok", then every setting now in effect as a URL query (favourites=…&amp;columns=…), so with no extras it only reports.
 * The manifest guards it with android.permission.DUMP, which the adb shell holds and normal apps can't get.
 * The logo comes through {@link LogoProvider}; "logo" in the report is its size in bytes (0: none).
 * Home reads the new values the next time it starts (TVFreedom force-stops it and presses Home).
 */
public class ConfigReceiver extends BroadcastReceiver {

    private static final String[] STRINGS = {Favourites.KEY, Config.PLACE, Config.LATITUDE, Config.LONGITUDE,
            Config.CALENDARS, Config.BRAND_COLOR};

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences.Editor e = Config.prefs(ctx).edit();
        StringBuilder changed = new StringBuilder();
        for (String key : STRINGS) {
            String v = intent.getStringExtra(key);
            if (v == null) continue;
            e.putString(key, v.trim());
            changed.append(' ').append(key);
        }
        int columns = intent.getIntExtra(Config.COLUMNS, 0);
        if (columns > 0) {
            e.putInt(Config.COLUMNS, Math.min(columns, 8));
            changed.append(' ').append(Config.COLUMNS);
        }
        e.commit();
        if (changed.length() > 0) Log.i("tvfreedom", "config:" + changed);
        setResultData("ok " + current(ctx));
    }

    private static String current(Context ctx) {
        return "favourites=" + enc(TextUtils.join(",", new Favourites(ctx).all()))
                + "&columns=" + Config.columns(ctx)
                + "&" + Config.PLACE + "=" + enc(Config.place(ctx))
                + "&" + Config.LATITUDE + "=" + enc(Config.latitude(ctx))
                + "&" + Config.LONGITUDE + "=" + enc(Config.longitude(ctx))
                + "&" + Config.CALENDARS + "=" + enc(Config.calendars(ctx))
                + "&" + Config.BRAND_COLOR + "=" + enc(Config.prefs(ctx).getString(Config.BRAND_COLOR, ""))
                + "&logo=" + LogoProvider.file(ctx).length();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
