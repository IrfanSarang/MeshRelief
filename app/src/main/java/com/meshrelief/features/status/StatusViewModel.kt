package com.meshrelief.features.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatusUiState(
    val peers: List<PeerEntity> = emptyList(),
    val myName: String = "",
    val myPhone: String = "",
    val myTriage: String = "SAFE",
    val isAdmin: Boolean = false
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState

    init {
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                _uiState.value = _uiState.value.copy(myName = name)
            }
        }
        viewModelScope.launch {
            userPreferences.userPhone.collect { phone ->
                _uiState.value = _uiState.value.copy(myPhone = phone)
            }
        }
        viewModelScope.launch {
            userPreferences.myTriageStatus.collect { triage ->
                _uiState.value = _uiState.value.copy(myTriage = triage)
            }
        }
        viewModelScope.launch {
            userPreferences.isAdmin.collect { admin ->
                _uiState.value = _uiState.value.copy(isAdmin = admin)
            }
        }

        // Placeholder peers — real data comes from WiFi Direct in Phase 2
        _uiState.value = _uiState.value.copy(
            peers = listOf(
                PeerEntity(
                    deviceId = "abc123",
                    name = "Ravi K.",
                    phone4 = "4401",
                    verified = true,
                    triageStatus = "SAFE",
                    battery = 80,
                    hopCount = 1
                ),
                PeerEntity(
                    deviceId = "def456",
                    name = "Priya M.",
                    phone4 = "2210",
                    verified = false,
                    triageStatus = "MINOR",
                    battery = 25,
                    hopCount = 2
                ),
                PeerEntity(
                    deviceId = "ghi789",
                    name = "Unknown",
                    phone4 = "7821",
                    verified = false,
                    flagged = true,
                    triageStatus = "CRITICAL",
                    battery = 12,
                    hopCount = 3
                )
            )
        )
    }
}