package com.meshrelief.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val name: String = "",
    val phone: String = "",
    val language: String = "EN",
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val nameError: String? = null,
    val phoneError: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun onPhoneChange(phone: String) {
        _uiState.value = _uiState.value.copy(
            phone = phone,
            phoneError = null
        )
    }

    fun onLanguageChange(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun onSubmit() {
        val state = _uiState.value

        // Validate
        val nameError = when {
            state.name.isBlank() -> "Name is required"
            state.name.length < 2 -> "Name must be at least 2 characters"
            state.name.length > 50 -> "Name must be less than 50 characters"
            else -> null
        }
        val phoneError = when {
            state.phone.isBlank() -> "Phone number is required"
            state.phone.length != 10 -> "Enter a valid 10-digit number"
            !state.phone.all { it.isDigit() } -> "Only digits allowed"
            else -> null
        }

        if (nameError != null || phoneError != null) {
            _uiState.value = _uiState.value.copy(
                nameError = nameError,
                phoneError = phoneError
            )
            return
        }

        // Save
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val deviceId = deviceIdentity.generateDeviceId(state.phone)

            userPreferences.saveUserName(state.name.trim())
            userPreferences.saveUserPhone(state.phone.trim())
            userPreferences.saveDeviceId(deviceId)
            userPreferences.saveLanguage(state.language)
            userPreferences.setSetupComplete(true)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isComplete = true
            )
        }
    }
}