package com.meshrelief.features.status

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.StatusPayload
import com.meshrelief.mesh.protocol.encode
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MyStatusUiState(
    val selectedTriage: String = "SAFE",
    val statusMessage: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean = false,
    val broadcastError: String? = null
)

@HiltViewModel
class MyStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,   // for BatteryManager
    private val userPreferences: UserPreferences,
    private val connectionManager: ConnectionManager,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyStatusUiState())
    val uiState: StateFlow<MyStatusUiState> = _uiState

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
        viewModelScope.launch {
            userPreferences.myTriageStatus.collect { triage ->
                _uiState.value = _uiState.value.copy(selectedTriage = triage)
            }
        }
        viewModelScope.launch {
            userPreferences.myStatusMessage.collect { msg ->
                _uiState.value = _uiState.value.copy(statusMessage = msg)
            }
        }
    }

    fun onTriageSelected(triage: String) {
        _uiState.value = _uiState.value.copy(
            selectedTriage = triage,
            broadcastSuccess = false,
            broadcastError = null
        )
    }

    fun onMessageChange(message: String) {
        if (message.length <= 100) {
            _uiState.value = _uiState.value.copy(statusMessage = message)
        }
    }

    fun onSaveAndBroadcast() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBroadcasting = true,
                broadcastSuccess = false,
                broadcastError = null
            )

            // 1. Persist triage + message to DataStore
            val triage = _uiState.value.selectedTriage
            val message = _uiState.value.statusMessage
            userPreferences.saveTriageStatus(triage)
            userPreferences.saveStatusMessage(message)

            // 2. Read sender info (single-shot read via first())
            val deviceId = userPreferences.userDeviceId.first()
            val name = userPreferences.userName.first()
            val phone = userPreferences.userPhone.first()

            // 3. Read current battery level via BatteryManager
            val battery = getBatteryPercent()

            // 4. Build payload using StatusPayload so the receiver's
            //    MeshJson.decodeFromString(StatusPayload.serializer(), payload)
            //    gets the required @SerialName("status") type discriminator.
            val payload = StatusPayload(
                triage  = triage,
                battery = battery,
                message = message
            ).encode()

            // 5. Build MeshPacket
            //    signature is filled in by ConnectionManager.sendPacket() (Issue #15),
            //    so we pass an empty string here.
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                type = PacketType.DEVICE_STATUS,
                senderId = deviceId,
                senderName = name,
                senderPhone = phone,
                payload = payload,
                ttl = 3,
                timestamp = System.currentTimeMillis(),
                signature = ""            // signed inside ConnectionManager
            )

            // 6. Broadcast to all visible peers
            val success = try {
                connectionManager.broadcastPacket(packet)
                true
            } catch (e: Exception) {
                false
            }

            _uiState.value = _uiState.value.copy(
                isBroadcasting = false,
                broadcastSuccess = success,
                broadcastError = if (!success) "Some peers could not be reached" else null
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun getBatteryPercent(): Int {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}