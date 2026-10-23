package io.github.raghavsatyadev.tvfreedom.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * Data for the screensaver: current weather (Open-Meteo, no key) and upcoming events from the calendars
 * named in local.properties or set by TVFreedom ([Config]). Blocking calls: run off the main thread.
 * Results are cached in statics so a new dream does not refetch.
 */
object DreamInfo {

    private const val WEATHER_MAX_AGE_MS = 30 * 60_000L
    private const val EVENTS_AHEAD_MS = 60L * 24 * 60 * 60_000L
    private const val MAX_EVENTS = 3

    @Volatile private var weather: String? = null
    @Volatile private var weatherAt = 0L

    /** "31° · Clear · Your City", or null when it could not be fetched (last good value is kept). */
    fun weather(ctx: Context): String? {
        val now = System.currentTimeMillis()
        weather?.let { if (now - weatherAt < WEATHER_MAX_AGE_MS) return it }
        val lat = Config.latitude(ctx)
        val lon = Config.longitude(ctx)
        if (lat.isEmpty() || lon.isEmpty()) return null
        var c: HttpURLConnection? = null
        try {
            c = URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            if (c.responseCode != 200) return weather
            val cur = JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }).getJSONObject("current")
            val temp = cur.getDouble("temperature_2m").roundToLong()
            weather = temp.toString() + "°  ·  " + describe(cur.getInt("weather_code")) + "  ·  " + Config.place(ctx)
            weatherAt = now
        } catch (ignored: Exception) {
            // Offline or API change: keep showing the last value.
        } finally {
            c?.disconnect()
        }
        return weather
    }

    /** Next events ("Diwali  ·  Sun 8 Nov") from the configured calendars; empty without permission. */
    fun events(ctx: Context): List<String> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val names = Config.calendars(ctx).split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return emptyList()
        val sel = CalendarContract.Instances.CALENDAR_DISPLAY_NAME + " IN (" + names.joinToString(",") { "?" } + ")"
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + EVENTS_AHEAD_MS)
        // All-day events are stored in UTC; format their date in UTC so they don't shift a day.
        val allDay = SimpleDateFormat("EEE d MMM", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val timed = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        // Today's local date as UTC midnight: an all-day event that ended by then was yesterday's, even
        // though its UTC span can still overlap "now" (India is 5.5 h ahead of UTC).
        val local = Calendar.getInstance()
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        today.clear()
        today.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        val todayUtc = today.timeInMillis
        val seen = LinkedHashSet<String>()   // the same holiday calendar can be synced twice
        try {
            ctx.contentResolver.query(
                uri.build(),
                arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.END,
                ),
                sel, names.toTypedArray(), CalendarContract.Instances.BEGIN + " ASC",
            )?.use { cur ->
                while (cur.moveToNext() && seen.size < MAX_EVENTS) {
                    val isAllDay = cur.getInt(2) == 1
                    if (isAllDay && cur.getLong(3) <= todayUtc) continue
                    val begin = cur.getLong(1)
                    val `when` = (if (isAllDay) allDay else timed).format(begin)
                    seen.add(cur.getString(0) + "  ·  " + `when`)
                }
            }
        } catch (ignored: Exception) {
            // Calendar provider missing or not synced yet.
        }
        return seen.toList()
    }

    /** WMO weather codes as used by Open-Meteo. */
    private fun describe(code: Int): String = when {
        code == 0 -> "Clear"
        code <= 2 -> "Partly cloudy"
        code == 3 -> "Cloudy"
        code == 45 || code == 48 -> "Fog"
        code <= 57 -> "Drizzle"
        code <= 67 -> "Rain"
        code <= 77 -> "Snow"
        code <= 82 -> "Showers"
        else -> "Thunderstorm"
    }
}
