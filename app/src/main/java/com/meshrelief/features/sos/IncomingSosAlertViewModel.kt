package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.*

enum class IncomingTriageLevel {
    SAFE, MINOR, CRITICAL, UNRESPONSIVE
}

data class IncomingSosUiState(
    val senderName: String = "Unknown",
    val senderIdSuffix: String = "0000",
    val isVerified: Boolean = false,
    val hopCount: Int = 1,
    val triage: IncomingTriageLevel = IncomingTriageLevel.CRITICAL,
    val latRaw: Double = 0.0,
    val lngRaw: Double = 0.0,
    val distanceKm: Float = 0f,
    val directionLabel: String = "??",
    val message: String = "",
    val receivedTimeLabel: String = "Just now",
    val isRelaying: Boolean = false,
    val relaySuccess: Boolean = false,
    val isDismissed: Boolean = false
)

// Parsed result from MeshPacket.payload JSON
private data class SosPayload(
    val triage: IncomingTriageLevel,
    val lat: Double,
    val lng: Double,
    val message: String
)

@HiltViewModel
class IncomingSosAlertViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingSosUiState())
    val uiState: StateFlow<IncomingSosUiState> = _uiState.asStateFlow()

    // Holds the original packet so relayFurther() can broadcast it
    private var cachedPacket: MeshPacket? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Entry point called from IncomingSosAlertScreen when a real MeshPacket
     * is available. Parses payload, resolves location, then updates uiState.
     */
    fun loadFromMeshPacket(packet: MeshPacket) {
        cachedPacket = packet
        viewModelScope.launch {
            val payload = parsePayload(packet.payload)

            // Get user's own position (may be null if permission denied / no fix)
            val userLocation = locationProvider.getLastKnownLocation()

            val distanceKm: Float
            val directionLabel: String

            if (userLocation != null) {
                distanceKm = haversineKm(
                    userLocation.latitude, userLocation.longitude,
                    payload.lat, payload.lng
                )
                directionLabel = compassLabel(
                    userLocation.latitude, userLocation.longitude,
                    payload.lat, payload.lng
                )
            } else {
                distanceKm = 0f
                directionLabel = "??"
            }

            _uiState.value = IncomingSosUiState(
                senderName        = packet.senderName,
                senderIdSuffix    = packet.senderId.takeLast(4),
                isVerified        = packet.signature.isNotBlank(),
                hopCount          = packet.ttl,
                triage            = payload.triage,
                latRaw            = payload.lat,
                lngRaw            = payload.lng,
                distanceKm        = distanceKm,
                directionLabel    = directionLabel,
                message           = payload.message,
                receivedTimeLabel  = "Just now"
            )
        }
    }

    /**
     * Legacy overload kept so existing Screen call-sites that still pass
     * individual fields continue to compile while you migrate them.
     */
    fun loadFromPacket(
        senderName: String,
        senderIdSuffix: String,
        isVerified: Boolean,
        hopCount: Int,
        triage: IncomingTriageLevel,
        lat: Double,
        lng: Double,
        distanceKm: Float,
        directionLabel: String,
        message: String,
        receivedTimeLabel: String
    ) {
        _uiState.value = IncomingSosUiState(
            senderName        = senderName,
            senderIdSuffix    = senderIdSuffix,
            isVerified        = isVerified,
            hopCount          = hopCount,
            triage            = triage,
            latRaw            = lat,
            lngRaw            = lng,
            distanceKm        = distanceKm,
            directionLabel    = directionLabel,
            message           = message,
            receivedTimeLabel = receivedTimeLabel
        )
    }

    /**
     * Broadcasts the cached packet to all nearby peers via ConnectionManager.
     * TTL was already decremented by MeshRouter before this screen opened.
     */
    fun relayFurther() {
        val packet = cachedPacket ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRelaying = true)
            connectionManager.broadcastPacket(packet)
            _uiState.value = _uiState.value.copy(isRelaying = false, relaySuccess = true)
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(isDismissed = true)
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Parses the JSON payload string from MeshPacket.
     * Expected format: {"triage":"CRITICAL","lat":19.076,"lng":72.877,"message":"..."}
     * Falls back to safe defaults on any parse error.
     */
    private fun parsePayload(json: String): SosPayload {
        return try {
            val obj = JSONObject(json)
            SosPayload(
                triage  = mapTriageString(obj.optString("triage", "CRITICAL")),
                lat     = obj.optDouble("lat", 0.0),
                lng     = obj.optDouble("lng", 0.0),
                message = obj.optString("message", "")
            )
        } catch (e: Exception) {
            SosPayload(
                triage  = IncomingTriageLevel.CRITICAL,
                lat     = 0.0,
                lng     = 0.0,
                message = json   // show raw string as message if JSON is malformed
            )
        }
    }

    /**
     * Maps triage string from packet → IncomingTriageLevel enum.
     * Case-insensitive; unknown values → CRITICAL (fail-safe).
     */
    private fun mapTriageString(value: String): IncomingTriageLevel =
        when (value.uppercase()) {
            "SAFE"         -> IncomingTriageLevel.SAFE
            "MINOR"        -> IncomingTriageLevel.MINOR
            "CRITICAL"     -> IncomingTriageLevel.CRITICAL
            "UNRESPONSIVE" -> IncomingTriageLevel.UNRESPONSIVE
            else           -> IncomingTriageLevel.CRITICAL
        }

    /**
     * Haversine formula — returns straight-line distance in km
     * between two lat/lng points.
     */
    private fun haversineKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val r = 6371.0                              // Earth radius km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return (2 * r * asin(sqrt(a))).toFloat()
    }

    /**
     * Returns an 8-point compass label (N, NE, E, SE, S, SW, W, NW)
     * for the bearing from (lat1,lon1) to (lat2,lon2).
     */
    private fun compassLabel(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): String {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val x = sin(dLon) * cos(lat2R)
        val y = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        val bearing = (Math.toDegrees(atan2(x, y)) + 360) % 360
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return directions[((bearing + 22.5) / 45).toInt() % 8]
    }
}