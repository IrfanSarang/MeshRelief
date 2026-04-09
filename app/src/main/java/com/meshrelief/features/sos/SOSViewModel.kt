package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.core.model.TriageLevel
import com.meshrelief.core.util.Constants
import com.meshrelief.data.db.entity.SOSEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.SOSRepository
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SOSUiState(
    val selectedTriage: TriageLevel = TriageLevel.SAFE,
    val showConfirmDialog: Boolean = false,
    val confirmCountdown: Int = 10,
    val cooldownRemainingMs: Long = 0L,
    val sosSent: Boolean = false,
    val userName: String = "",
    val userPhone: String = ""
)

@HiltViewModel
class SOSViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val connectionManager: ConnectionManager,
    private val sosRepository: SOSRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SOSUiState())
    val uiState: StateFlow<SOSUiState> = _uiState

    private var countdownJob: Job? = null
    private var cooldownJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                _uiState.value = _uiState.value.copy(userName = name)
            }
        }
        viewModelScope.launch {
            userPreferences.userPhone.collect { phone ->
                _uiState.value = _uiState.value.copy(userPhone = phone)
            }
        }
    }

    fun onTriageSelected(triage: TriageLevel) {
        _uiState.value = _uiState.value.copy(selectedTriage = triage)
    }

    fun onSOSButtonPressed() {
        if (_uiState.value.cooldownRemainingMs > 0) return
        _uiState.value = _uiState.value.copy(
            showConfirmDialog = true,
            confirmCountdown = 10
        )
        startConfirmCountdown()
    }

    fun onConfirmSOS() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showConfirmDialog = false,
            sosSent = true
        )
        startCooldown()

        viewModelScope.launch {
            // 1. Grab device ID (first emission is enough)
            val deviceId = userPreferences.userDeviceId.first()

            // 2. Get last known location (null-safe)
            val location = locationProvider.getLastKnownLocation()
            val lat = location?.latitude ?: 0.0
            val lng = location?.longitude ?: 0.0

            val state = _uiState.value
            val packetId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // 3. Build payload JSON manually (no extra dependency needed)
            val payload = """{"triage":"CRITICAL","lat":$lat,"lng":$lng,"message":""}"""

            // 4. Build MeshPacket
            val packet = MeshPacket(
                id        = packetId,
                type      = PacketType.SOS_ALERT,
                senderId  = deviceId,
                senderName  = state.userName,
                senderPhone = state.userPhone,
                payload   = payload,
                ttl       = Constants.SOS_TTL,
                timestamp = now,
                signature = "" // ConnectionManager signs before send
            )

            // 5. Broadcast over Wi-Fi Direct
            connectionManager.broadcastPacket(packet)

            // 6. Persist to Room
            val entity = SOSEntity(
                id           = packetId,
                senderId     = deviceId,
                senderName   = state.userName,
                senderPhone4 = state.userPhone,
                lat          = lat,
                lng          = lng,
                triageStatus = state.selectedTriage.name,
                message      = "",
                timestamp    = now
            )
            sosRepository.save(entity)
        }
    }

    fun onCancelSOS() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showConfirmDialog = false,
            confirmCountdown = 10
        )
    }

    private fun startConfirmCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 9 downTo 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(confirmCountdown = i)
            }
            onCancelSOS()
        }
    }

    private fun startCooldown() {
        val cooldownMs = when (_uiState.value.selectedTriage) {
            TriageLevel.CRITICAL -> Constants.SOS_COOLDOWN_CRITICAL_MS
            else                 -> Constants.SOS_COOLDOWN_DEFAULT_MS
        }
        _uiState.value = _uiState.value.copy(cooldownRemainingMs = cooldownMs)

        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = cooldownMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _uiState.value = _uiState.value.copy(cooldownRemainingMs = remaining)
            }
            _uiState.value = _uiState.value.copy(
                cooldownRemainingMs = 0,
                sosSent = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        cooldownJob?.cancel()
    }
}