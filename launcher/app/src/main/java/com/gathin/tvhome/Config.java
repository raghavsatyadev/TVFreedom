package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings that the tvgo phone app (or adb) can change at runtime through {@link ConfigReceiver}.
 * Each one falls back to the value built into the APK (res/values and local.properties) until it is set.
 */
final class Config {

    static final String PREFS = "home";
    static final String COLUMNS = "columns", PLACE = "weather_place", LATITUDE = "weather_latitude",
            LONGITUDE = "weather_longitude", CALENDARS = "dream_calendars", BRAND_COLOR = "brand_color";

    private Config() {
    }

    static int columns(Context ctx) {
        return Math.max(1, prefs(ctx).getInt(COLUMNS, ctx.getResources().getInteger(R.integer.columns)));
    }

    static String place(Context ctx) {
        return get(ctx, PLACE, R.string.weather_place);
    }

    static String latitude(Context ctx) {
        return get(ctx, LATITUDE, R.string.weather_latitude);
    }

    static String longitude(Context ctx) {
        return get(ctx, LONGITUDE, R.string.weather_longitude);
    }

    /** Calendar display names separated by |. */
    static String calendars(Context ctx) {
        return get(ctx, CALENDARS, R.string.dream_calendars);
    }

    /** The home screen's background: the company colour as #RRGGBB, or black when none is set (or it doesn't parse). */
    static int brandColor(Context ctx) {
        String v = prefs(ctx).getString(BRAND_COLOR, "");
        try {
            return v.isEmpty() ? 0xFF000000 : 0xFF000000 | android.graphics.Color.parseColor(v);
        } catch (IllegalArgumentException e) {
            return 0xFF000000;
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String get(Context ctx, String key, int fallback) {
        String v = prefs(ctx).getString(key, null);
        return v != null ? v : ctx.getString(fallback);
    }
}
