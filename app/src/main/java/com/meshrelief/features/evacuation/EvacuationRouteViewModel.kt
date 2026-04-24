package com.meshrelief.features.evacuation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.mesh.protocol.EvacuationRoutePayload
import com.meshrelief.mesh.protocol.LatLng
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.encode
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WayPoint(val lat: Double, val lng: Double)

data class EvacuationRouteUiState(
    val waypoints        : List<WayPoint> = emptyList(),
    val routeLabel       : String         = "",
    val isBroadcasting   : Boolean        = false,
    val broadcastSuccess : Boolean        = false,
    val broadcastError   : String?        = null
)

@HiltViewModel
class EvacuationRouteViewModel @Inject constructor(
    private val connectionManager : ConnectionManager,
    private val userPreferences   : UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvacuationRouteUiState())
    val uiState: StateFlow<EvacuationRouteUiState> = _uiState.asStateFlow()

    fun addWaypoint(lat: Double, lng: Double) {
        val current = _uiState.value.waypoints
        if (current.size >= 10) return
        _uiState.value = _uiState.value.copy(
            waypoints = current + WayPoint(lat, lng)
        )
    }

    fun undoLast() {
        val current = _uiState.value.waypoints
        if (current.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            waypoints = current.dropLast(1)
        )
    }

    fun clearAll() {
        _uiState.value = _uiState.value.copy(waypoints = emptyList())
    }

    fun setRouteLabel(label: String) {
        _uiState.value = _uiState.value.copy(routeLabel = label)
    }

    fun broadcastRoute(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.waypoints.size < 2) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBroadcasting   = true,
                broadcastSuccess = false,
                broadcastError   = null
            )

            // Collect user identity from DataStore flows
            val deviceId = userPreferences.userDeviceId.first()
            val name     = userPreferences.userName.first()
            val phone    = userPreferences.userPhone.first()

            // Build payload
            val payload = EvacuationRoutePayload(
                label     = state.routeLabel.ifBlank { "Evacuation Route" },
                waypoints = state.waypoints.map { LatLng(it.lat, it.lng) }
            )

            // Build packet
            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.EVACUATION_ROUTE,
                senderId    = deviceId,
                senderName  = name,
                senderPhone = phone,
                payload     = payload.encode(),
                ttl         = 5,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )

            // Send over Wi-Fi Direct mesh
            // BroadcastResult.results is Map<String, Boolean> (IP → success)
            // Consider success if at least one peer received the packet
            // and the Group Owner was not unreachable
            val result  = connectionManager.broadcastPacket(packet)
            val success = result.results.values.any { it } && !result.goUnreachable

            _uiState.value = _uiState.value.copy(
                isBroadcasting   = false,
                broadcastSuccess = success,
                broadcastError   = when {
                    result.goUnreachable              -> "Group Owner unreachable — route not delivered"
                    result.results.values.none { it } -> "No peers received the route"
                    else                              -> null
                }
            )

            if (success) {
                kotlinx.coroutines.delay(1500L)
                onDone()
            }
        }
    }
}