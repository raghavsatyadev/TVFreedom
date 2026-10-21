package com.gathin.tvgo.store

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where the phone is, roughly, for the TV screensaver's weather: the town name and its coordinates to 2 decimals
 * (about 1 km), so nothing more precise ever reaches the TV.
 */
object PhoneLocation {
    data class Place(val name: String, val latitude: String, val longitude: String)

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Null without the permission or when the phone can't tell (location off). */
    @SuppressLint("MissingPermission")
    suspend fun find(ctx: Context): Place? {
        if (!granted(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val loc = current(lm) ?: lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        val lat = String.format(Locale.US, "%.2f", loc.latitude)
        val lon = String.format(Locale.US, "%.2f", loc.longitude)
        return Place(town(ctx, loc).orEmpty(), lat, lon)
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < 30) return null
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in lm.getProviders(true) } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(provider, cancel, Runnable::run) { if (cont.isActive) cont.resume(it) }
            }
        }
    }

    private suspend fun town(ctx: Context, loc: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION") // the listener version needs API 33; this runs off the main thread
            Geocoder(ctx).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
    }
}
