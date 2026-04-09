package com.meshrelief.features.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.PeerRepository
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
    private val userPreferences: UserPreferences,
    private val peerRepository: PeerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState

    init {
        // Observe user preferences
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

        // Observe real peers from Room via PeerRepository
        viewModelScope.launch {
            peerRepository.getAllPeers().collect { peers ->
                _uiState.value = _uiState.value.copy(peers = peers)
            }
        }
    }
}