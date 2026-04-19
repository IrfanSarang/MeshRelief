package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.decodeSos
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Triage level mirrored for incoming display ────────────────────────────────

enum class IncomingTriageLevel { SAFE, MINOR, CRITICAL, UNRESPONSIVE }

private fun String.toIncomingTriage(): IncomingTriageLevel =
    runCatching { IncomingTriageLevel.valueOf(this.uppercase()) }
        .getOrDefault(IncomingTriageLevel.SAFE)

// ── UI state ─────────────────────────────────────────────────────────────────

data class IncomingSosAlertUiState(
    val senderName       : String              = "Unknown",
    val senderIdSuffix   : String              = "????",
    val isVerified       : Boolean             = false,
    val hopCount         : Int                 = 1,
    val triage           : IncomingTriageLevel = IncomingTriageLevel.SAFE,
    val latRaw           : Double              = 0.0,
    val lngRaw           : Double              = 0.0,
    val distanceKm       : Float               = 0f,
    val directionLabel   : String              = "—",
    val message          : String              = "",
    val receivedTimeLabel: String              = "",
    val isRelaying       : Boolean             = false,
    val relaySuccess     : Boolean             = false,
    val isDismissed      : Boolean             = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class IncomingSosAlertViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingSosAlertUiState())
    val uiState: StateFlow<IncomingSosAlertUiState> = _uiState

    // Hold the original packet for relay
    private var cachedPacket: MeshPacket? = null

    /**
     * Primary entry point — called by IncomingSosAlertScreen when a real packet arrives.
     * Decodes via SosPayload schema; falls back gracefully on parse failure.
     */
    fun loadFromMeshPacket(packet: MeshPacket) {
        cachedPacket = packet

        runCatching {
            // ── Decode structured payload — single source of truth ──
            val sos = packet.decodeSos()

            _uiState.value = _uiState.value.copy(
                senderName        = packet.senderName.ifBlank { "Unknown" },
                senderIdSuffix    = packet.senderId.takeLast(4).padStart(4, '0'),
                isVerified        = packet.signature.isNotBlank(),
                hopCount          = (packet.ttl - 1).coerceAtLeast(1), // rough hop estimate
                triage            = sos.triage.toIncomingTriage(),
                latRaw            = sos.lat,
                lngRaw            = sos.lng,
                distanceKm        = 0f,   // compute from own location if LocationProvider injected
                directionLabel    = "—",  // compute bearing if LocationProvider injected
                message           = sos.message,
                receivedTimeLabel = packet.timestamp.toTimeLabel()
            )
        }.onFailure {
            // Payload malformed — show sender info only, triage unknown
            _uiState.value = _uiState.value.copy(
                senderName     = packet.senderName.ifBlank { "Unknown" },
                senderIdSuffix = packet.senderId.takeLast(4).padStart(4, '0'),
                triage         = IncomingTriageLevel.SAFE
            )
        }
    }

    /** Dev/demo fallback — pre-fills state without a real packet. */
    fun loadFromPacket(
        senderName     : String,
        senderIdSuffix : String,
        isVerified     : Boolean,
        hopCount       : Int,
        triage         : IncomingTriageLevel,
        lat            : Double,
        lng            : Double,
        distanceKm     : Float,
        directionLabel : String,
        message        : String,
        receivedTimeLabel : String
    ) {
        _uiState.value = _uiState.value.copy(
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

    fun relayFurther() {
        val packet = cachedPacket ?: return
        if (packet.ttl <= 1) return   // TTL exhausted — do not relay

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRelaying = true)
            runCatching {
                val relayed = packet.copy(ttl = packet.ttl - 1)
                connectionManager.broadcastPacket(relayed)
            }
            _uiState.value = _uiState.value.copy(isRelaying = false, relaySuccess = true)
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(isDismissed = true)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Long.toTimeLabel(): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = this@toTimeLabel }
        val h   = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m   = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (h < 12) "AM" else "PM"
        val h12  = when {
            h == 0  -> 12
            h <= 12 -> h
            else    -> h - 12
        }
        return "$h12:${"%02d".format(m)} $ampm"
    }
}