package com.meshrelief.features.camps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import com.meshrelief.core.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

enum class CampType(val label: String) {
    MEDICAL("Medical"),
    SHELTER("Shelter"),
    SUPPLY("Supply"),
    MIXED("Mixed")
}

data class ResourceRow(
    val emoji: String,
    val name: String,
    val status: ResourceStatus = ResourceStatus.AVAILABLE
)

data class AddCampUiState(
    val campName: String = "",
    val campNameError: String? = null,
    val campType: CampType = CampType.SHELTER,
    val capacity: String = "",
    val capacityError: String? = null,
    val adminContact: String = "",
    val adminNotes: String = "",
    val resources: List<ResourceRow> = listOf(
        ResourceRow("💧", "Water"),
        ResourceRow("🍲", "Food"),
        ResourceRow("💊", "Medicine"),
        ResourceRow("🛏", "Blankets"),
        ResourceRow("⚡", "Power")
    ),
    val latitude: String = "20.5937",
    val longitude: String = "78.9629",
    val isLocating: Boolean = false,
    val isSubmitting: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = ""
)

@HiltViewModel
class AddCampViewModel @Inject constructor(
    private val campRepository: CampRepository,
    private val connectionManager: ConnectionManager,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddCampUiState())
    val uiState: StateFlow<AddCampUiState> = _uiState.asStateFlow()

    private val json = Json { encodeDefaults = true }

    fun onCampNameChange(value: String) {
        _uiState.update { it.copy(campName = value, campNameError = null) }
    }

    fun onCampTypeChange(type: CampType) {
        _uiState.update { it.copy(campType = type) }
    }

    fun onCapacityChange(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update { it.copy(capacity = filtered, capacityError = null) }
    }

    fun onAdminContactChange(value: String) {
        _uiState.update { it.copy(adminContact = value) }
    }

    fun onAdminNotesChange(value: String) {
        _uiState.update { it.copy(adminNotes = value) }
    }

    fun onResourceStatusChange(index: Int, status: ResourceStatus) {
        _uiState.update { state ->
            val updated = state.resources.toMutableList()
            updated[index] = updated[index].copy(status = status)
            state.copy(resources = updated)
        }
    }

    fun onLatitudeChange(value: String) {
        _uiState.update { it.copy(latitude = value) }
    }

    fun onLongitudeChange(value: String) {
        _uiState.update { it.copy(longitude = value) }
    }

    fun onSnackbarDismissed() {
        _uiState.update { it.copy(showSnackbar = false) }
    }

    fun submitCamp(onSuccess: () -> Unit) {
        val state = _uiState.value
        var hasError = false

        if (state.campName.isBlank()) {
            _uiState.update { it.copy(campNameError = "Camp name is required") }
            hasError = true
        }
        if (state.capacity.isBlank()) {
            _uiState.update { it.copy(capacityError = "Capacity is required") }
            hasError = true
        }
        if (hasError) return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            // a. Build entity
            val entity = CampEntity(
                id = UUID.randomUUID().toString(),
                name = state.campName.trim(),
                type = state.campType.label,
                lat = state.latitude.toDoubleOrNull() ?: 0.0,
                lng = state.longitude.toDoubleOrNull() ?: 0.0,
                capacity = state.capacity.toIntOrNull() ?: 0,
                currentCount = 0,
                status = "OPEN",
                notes = state.adminNotes.trim(),
                adminId = state.adminContact.trim(),
                updatedAt = System.currentTimeMillis()
            )

            // b. Insert to Room
            campRepository.upsert(entity)

            // c. Build MeshPacket
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                type = PacketType.CAMP_UPDATE,
                senderId = "",          // signed/filled by ConnectionManager
                senderName = "",
                senderPhone = "",
                payload = json.encodeToString(entity),
                ttl = 5,
                timestamp = System.currentTimeMillis(),
                signature = ""
            )

            // d. Broadcast to mesh peers
            connectionManager.broadcastPacket(packet)

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    showSnackbar = true,
                    snackbarMessage = "Camp registered and broadcast to mesh"
                )
            }

            onSuccess()
        }
    }
    fun fetchGpsLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val fix = locationProvider.getLastKnownLocation()
            if (fix != null) {
                _uiState.update {
                    it.copy(
                        latitude = "%.6f".format(fix.latitude),
                        longitude = "%.6f".format(fix.longitude),
                        isLocating = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        showSnackbar = true,
                        snackbarMessage = "Unable to get location. Enable GPS and try again."
                    )
                }
            }
        }
    }
}