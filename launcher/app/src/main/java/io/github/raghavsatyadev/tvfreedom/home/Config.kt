package io.github.raghavsatyadev.tvfreedom.home

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Settings that the TVFreedom phone app (or adb) can change at runtime through [ConfigReceiver].
 * Each one falls back to the value built into the APK (res/values and local.properties) until it is set.
 */
object Config {

    const val PREFS = "home"
    const val COLUMNS = "columns"
    const val PLACE = "weather_place"
    const val LATITUDE = "weather_latitude"
    const val LONGITUDE = "weather_longitude"
    const val CALENDARS = "dream_calendars"
    const val BRAND_COLOR = "brand_color"

    private const val BLACK = 0xFF000000.toInt()

    fun columns(ctx: Context): Int =
        maxOf(1, prefs(ctx).getInt(COLUMNS, ctx.resources.getInteger(R.integer.columns)))

    fun place(ctx: Context): String = get(ctx, PLACE, R.string.weather_place)

    fun latitude(ctx: Context): String = get(ctx, LATITUDE, R.string.weather_latitude)

    fun longitude(ctx: Context): String = get(ctx, LONGITUDE, R.string.weather_longitude)

    /** Calendar display names separated by |. */
    fun calendars(ctx: Context): String = get(ctx, CALENDARS, R.string.dream_calendars)

    /** The home screen's background: the company colour as #RRGGBB, or black when none is set (or it doesn't parse). */
    fun brandColor(ctx: Context): Int {
        val v = prefs(ctx).getString(BRAND_COLOR, "").orEmpty()
        return try {
            if (v.isEmpty()) BLACK else BLACK or Color.parseColor(v)
        } catch (e: IllegalArgumentException) {
            BLACK
        }
    }

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun get(ctx: Context, key: String, fallback: Int): String =
        prefs(ctx).getString(key, null) ?: ctx.getString(fallback)
}
