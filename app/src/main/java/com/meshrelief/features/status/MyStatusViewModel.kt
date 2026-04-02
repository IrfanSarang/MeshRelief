package com.meshrelief.features.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyStatusUiState(
    val selectedTriage: String = "SAFE",
    val statusMessage: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean = false
)

@HiltViewModel
class MyStatusViewModel @Inject constructor(
    private val userPreferences: UserPreferences
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
            broadcastSuccess = false
        )
    }

    fun onMessageChange(message: String) {
        if (message.length <= 100) {
            _uiState.value = _uiState.value.copy(statusMessage = message)
        }
    }

    fun onSaveAndBroadcast() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBroadcasting = true)
            userPreferences.saveTriageStatus(_uiState.value.selectedTriage)
            userPreferences.saveStatusMessage(_uiState.value.statusMessage)
            // WiFi Direct broadcast added in Phase 2
            _uiState.value = _uiState.value.copy(
                isBroadcasting = false,
                broadcastSuccess = true
            )
        }
    }
}