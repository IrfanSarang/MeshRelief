package com.meshrelief.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Attempts a fresh GPS fix (up to 10 seconds), then falls back to the
     * last cached location. Returns null if permissions are missing or no
     * location is available at all.
     */
    suspend fun getLastKnownLocation(): GeoPoint? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        if (fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Try a fresh GPS fix with a 10-second timeout
            val fresh = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            cont.resume(GeoPoint(loc.latitude, loc.longitude))
                        }

                        override fun onProviderDisabled(p: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }

                    @Suppress("MissingPermission")
                    locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER,
                        listener,
                        null
                    )

                    cont.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }
                }
            }

            if (fresh != null) return fresh
        }

        // Fall back to cached location
        return getCachedLocation(fineGranted, coarseGranted)
    }

    /**
     * Original implementation: returns the best cached location from
     * GPS_PROVIDER (tried first) or NETWORK_PROVIDER (fallback).
     */
    private fun getCachedLocation(fineGranted: Boolean, coarseGranted: Boolean): GeoPoint? {
        val providers = buildList {
            if (fineGranted) add(LocationManager.GPS_PROVIDER)
            if (coarseGranted || fineGranted) add(LocationManager.NETWORK_PROVIDER)
        }

        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider)) continue
            @Suppress("MissingPermission")
            val location = locationManager.getLastKnownLocation(provider)
            if (location != null) {
                return GeoPoint(location.latitude, location.longitude)
            }
        }

        return null
    }
}