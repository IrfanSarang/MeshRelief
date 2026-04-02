package com.meshrelief.features.camps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.features.camps.CampResource
import com.meshrelief.features.camps.ResourceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val isSubmitting: Boolean = false,
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = ""
)

@HiltViewModel
class AddCampViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AddCampUiState())
    val uiState: StateFlow<AddCampUiState> = _uiState.asStateFlow()

    fun onCampNameChange(value: String) {
        _uiState.update { it.copy(campName = value, campNameError = null) }
    }

    fun onCampTypeChange(type: CampType) {
        _uiState.update { it.copy(campType = type) }
    }

    fun onCapacityChange(value: String) {
        val filtered = value.filter { c -> c.isDigit() }
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
            // Mock Room insert — no actual DB call needed
            delay(600L)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    showSnackbar = true,
                    snackbarMessage = "Camp registered and broadcast to mesh"
                )
            }
            delay(1000L)
            onSuccess()
        }
    }
}