package io.github.raghavsatyadev.tvfreedom.home;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Data for the screensaver: current weather (Open-Meteo, no key) and upcoming events from the calendars
 * named in local.properties or set by TVFreedom ({@link Config}). Blocking calls: run off the main thread.
 * Results are cached in statics so a new dream does not refetch.
 */
final class DreamInfo {

    private static final long WEATHER_MAX_AGE_MS = 30 * 60_000L;
    private static final long EVENTS_AHEAD_MS = 60L * 24 * 60 * 60_000L;
    private static final int MAX_EVENTS = 3;

    private static String weather;
    private static long weatherAt;

    private DreamInfo() {
    }

    /** "31° · Clear · Your City", or null when it could not be fetched (last good value is kept). */
    static String weather(Context ctx) {
        long now = System.currentTimeMillis();
        if (weather != null && now - weatherAt < WEATHER_MAX_AGE_MS) return weather;
        String lat = Config.latitude(ctx), lon = Config.longitude(ctx);
        if (lat.isEmpty() || lon.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto").openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) return weather;
            JSONObject cur = new JSONObject(read(c.getInputStream())).getJSONObject("current");
            long temp = Math.round(cur.getDouble("temperature_2m"));
            weather = temp + "°  ·  " + describe(cur.getInt("weather_code")) + "  ·  "
                    + Config.place(ctx);
            weatherAt = now;
        } catch (Exception ignored) {
            // Offline or API change: keep showing the last value.
        } finally {
            if (c != null) c.disconnect();
        }
        return weather;
    }

    /** Next events ("Diwali  ·  Sun 8 Nov") from the configured calendars; empty without permission. */
    static List<String> events(Context ctx) {
        List<String> out = new ArrayList<>();
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return out;
        }
        List<String> names = new ArrayList<>();
        for (String n : Config.calendars(ctx).split("\\|")) {
            if (!n.trim().isEmpty()) names.add(n.trim());
        }
        if (names.isEmpty()) return out;
        StringBuilder sel = new StringBuilder(CalendarContract.Instances.CALENDAR_DISPLAY_NAME + " IN (");
        for (int i = 0; i < names.size(); i++) sel.append(i == 0 ? "?" : ",?");
        sel.append(")");
        long now = System.currentTimeMillis();
        android.net.Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(uri, now);
        android.content.ContentUris.appendId(uri, now + EVENTS_AHEAD_MS);
        // All-day events are stored in UTC; format their date in UTC so they don't shift a day.
        SimpleDateFormat allDay = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
        allDay.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat timed = new SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault());
        // Today's local date as UTC midnight: an all-day event that ended by then was yesterday's, even
        // though its UTC span can still overlap "now" (India is 5.5 h ahead of UTC).
        java.util.Calendar local = java.util.Calendar.getInstance();
        java.util.Calendar today = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        today.clear();
        today.set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH));
        long todayUtc = today.getTimeInMillis();
        Set<String> seen = new LinkedHashSet<>();   // the same holiday calendar can be synced twice
        try (Cursor cur = ctx.getContentResolver().query(uri.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.END},
                sel.toString(), names.toArray(new String[0]), CalendarContract.Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext() && seen.size() < MAX_EVENTS) {
                if (cur.getInt(2) == 1 && cur.getLong(3) <= todayUtc) continue;
                long begin = cur.getLong(1);
                String when = (cur.getInt(2) == 1 ? allDay : timed).format(begin);
                seen.add(cur.getString(0) + "  ·  " + when);
            }
        } catch (Exception ignored) {
            // Calendar provider missing or not synced yet.
        }
        out.addAll(seen);
        return out;
    }

    /** WMO weather codes as used by Open-Meteo. */
    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        return "Thunderstorm";
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = is.read(b)) > 0; ) buf.write(b, 0, n);
            return buf.toString("UTF-8");
        }
    }
}
