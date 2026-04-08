package com.meshrelief.features.navigate

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class NavigateToCampUiState(
    val campName: String = "",
    val campType: String = "",
    val campLatitude: Double = 0.0,
    val campLongitude: Double = 0.0,
    val userLatitude: Double = 0.0,
    val userLongitude: Double = 0.0,
    val distanceKm: Double = 0.0,
    val directionLabel: String = "N",
    val estimatedMinutes: Int = 0,
    val bearingDeg: Float = 0f,
    val compassHeadingDeg: Float = 0f,
    val isNavigating: Boolean = false,
    val isLoaded: Boolean = false,
    val isLocating: Boolean = false
)

@HiltViewModel
class NavigateToCampViewModel @Inject constructor(
    application: Application,
    private val locationProvider: LocationProvider
) : AndroidViewModel(application), SensorEventListener {

    private val _uiState = MutableStateFlow(NavigateToCampUiState())
    val uiState: StateFlow<NavigateToCampUiState> = _uiState

    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Stub camp list — real data wired in Issue #4/#5
    private val stubCamps = listOf(
        mapOf(
            "id" to "camp_001",
            "name" to "St. Xavier School",
            "type" to "Food & Rest",
            "lat" to 19.0821,
            "lng" to 72.8697
        ),
        mapOf(
            "id" to "camp_002",
            "name" to "Don Bosco Ground",
            "type" to "Medical + Shelter",
            "lat" to 19.0680,
            "lng" to 72.8350
        ),
        mapOf(
            "id" to "camp_003",
            "name" to "Azad Maidan Camp",
            "type" to "Shelter & Supply",
            "lat" to 18.9334,
            "lng" to 72.8280
        ),
        mapOf(
            "id" to "camp_004",
            "name" to "NSCI Dome Relief Centre",
            "type" to "Medical Camp",
            "lat" to 19.0176,
            "lng" to 72.8562
        )
    )

    init {
        viewModelScope.launch { fetchUserLocation() }
    }

    fun autoSelectCamp(campId: String?) {
        if (campId != null) loadCamp(campId)
    }

    /**
     * Resolves the best available GPS/network fix and writes it into uiState.
     * If no fix is available, lat/lng remain 0.0 and the UI should show a
     * "waiting for GPS" indicator (isLocating = true until resolved).
     */
    private suspend fun fetchUserLocation() {
        _uiState.value = _uiState.value.copy(isLocating = true)
        val fix = locationProvider.getLastKnownLocation()
        _uiState.value = _uiState.value.copy(
            userLatitude = fix?.latitude ?: _uiState.value.userLatitude,
            userLongitude = fix?.longitude ?: _uiState.value.userLongitude,
            isLocating = false
        )
    }

    fun loadCamp(campId: String) {
        viewModelScope.launch {
            // Refresh location right before computing navigation so the fix
            // is as fresh as possible when the user opens this screen.
            fetchUserLocation()

            val camp = stubCamps.find { it["id"] == campId } ?: stubCamps.first()
            val campLat = camp["lat"] as Double
            val campLng = camp["lng"] as Double
            val userLat = _uiState.value.userLatitude
            val userLng = _uiState.value.userLongitude

            val distance = haversineKm(userLat, userLng, campLat, campLng)
            val bearing = bearingDeg(userLat, userLng, campLat, campLng)
            val direction = compassLabel(bearing)
            val walkMin = (distance / 0.067).roundToInt() // ~4 km/h

            _uiState.value = _uiState.value.copy(
                campName = camp["name"] as String,
                campType = camp["type"] as String,
                campLatitude = campLat,
                campLongitude = campLng,
                distanceKm = (distance * 10).roundToInt() / 10.0,
                directionLabel = direction,
                estimatedMinutes = walkMin,
                bearingDeg = bearing,
                isLoaded = true
            )
        }
    }

    fun startWalking() {
        _uiState.value = _uiState.value.copy(isNavigating = true)
    }

    fun registerCompass() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun unregisterCompass() {
        sensorManager.unregisterListener(this)
    }

    // ── SensorEventListener ──────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val heading = (azimuthDeg + 360f) % 360f
        _uiState.value = _uiState.value.copy(compassHeadingDeg = heading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ── Math helpers ─────────────────────────────────────────────────────────

    private fun haversineKm(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    private fun bearingDeg(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360f) % 360f
    }

    private fun compassLabel(bearing: Float): String {
        return when {
            bearing < 22.5f  -> "N"
            bearing < 67.5f  -> "NE"
            bearing < 112.5f -> "E"
            bearing < 157.5f -> "SE"
            bearing < 202.5f -> "S"
            bearing < 247.5f -> "SW"
            bearing < 292.5f -> "W"
            bearing < 337.5f -> "NW"
            else             -> "N"
        }
    }
}