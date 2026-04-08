package com.meshrelief.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Returns the best last-known location available, or null if:
     * - permissions are not granted, or
     * - no fix has been acquired yet on either provider.
     *
     * GPS_PROVIDER is tried first (most accurate); NETWORK_PROVIDER is used
     * as a fallback so indoor / low-signal devices still get a position.
     */
    suspend fun getLastKnownLocation(): GeoPoint? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        // Try GPS first, fall back to network
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