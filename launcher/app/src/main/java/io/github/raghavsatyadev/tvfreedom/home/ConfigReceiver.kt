package io.github.raghavsatyadev.tvfreedom.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.URLEncoder

/**
 * Takes settings from adb, so the TVFreedom phone app can set up the home screen without rebuilding the APK:
 * ```
 * am broadcast -f 0x20 -n io.github.raghavsatyadev.tvfreedom.home/.ConfigReceiver --es favourites com.a,com.b --ei columns 4 \
 *     --es weather_place 'Your City' --es weather_latitude 12.34 --es weather_longitude 56.78 \
 *     --es dream_calendars 'Holidays|Family' --es brand_color '#123456' \
 *     --es true_black 1 --es continue_watching 1 --es tile_shape circle --es names focus --es accent '#5B8DEF'
 * ```
 * Only extras that are present are changed; an empty string clears a setting back to "none". The result data is
 * "ok", then every setting now in effect as a URL query (favourites=…&columns=…), so with no extras it only reports.
 * The manifest guards it with android.permission.DUMP, which the adb shell holds and normal apps can't get.
 * The logo comes through [LogoProvider]; "logo" in the report is its size in bytes (0: none).
 * Home reads the new values the next time it starts (TVFreedom force-stops it and presses Home).
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val e = Config.prefs(ctx).edit()
        val changed = StringBuilder()
        for (key in STRINGS) {
            val v = intent.getStringExtra(key) ?: continue
            e.putString(key, v.trim())
            changed.append(' ').append(key)
        }
        val columns = intent.getIntExtra(Config.COLUMNS, 0)
        if (columns > 0) {
            e.putInt(Config.COLUMNS, minOf(columns, 8))
            changed.append(' ').append(Config.COLUMNS)
        }
        e.commit()
        if (changed.isNotEmpty()) Log.i("tvfreedom", "config:$changed")
        resultData = "ok " + current(ctx)
    }

    private companion object {
        val STRINGS = arrayOf(
            Favourites.KEY, Config.PLACE, Config.LATITUDE, Config.LONGITUDE, Config.CALENDARS, Config.BRAND_COLOR,
            Config.TRUE_BLACK, Config.CONTINUE_WATCHING, Look.SHAPE, Look.NAMES, Look.ACCENT,
        )

        fun current(ctx: Context): String =
            "favourites=" + enc(Favourites(ctx).all().joinToString(",")) +
                "&columns=" + Config.columns(ctx) +
                "&" + Config.PLACE + "=" + enc(Config.place(ctx)) +
                "&" + Config.LATITUDE + "=" + enc(Config.latitude(ctx)) +
                "&" + Config.LONGITUDE + "=" + enc(Config.longitude(ctx)) +
                "&" + Config.CALENDARS + "=" + enc(Config.calendars(ctx)) +
                "&" + Config.BRAND_COLOR + "=" + enc(Config.prefs(ctx).getString(Config.BRAND_COLOR, "").orEmpty()) +
                "&" + Config.TRUE_BLACK + "=" + (if (Config.trueBlack(ctx)) "1" else "") +
                "&" + Config.CONTINUE_WATCHING + "=" + (if (Config.continueWatching(ctx)) "1" else "") +
                Look.report(ctx) +
                "&logo=" + LogoProvider.file(ctx).length()

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
