package com.meshrelief.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val meshActive: Boolean = false,
    val peersOnline: Int = 0,
    val verifiedPeers: Int = 0,
    val nearbyCamps: Int = 0,
    val openCamps: Int = 0,
    val latestBulletin: BulletinPreview? = null
)

data class BulletinPreview(
    val type: String,
    val content: String,
    val senderName: String,
    val minutesAgo: Int,
    val relayCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                _uiState.value = _uiState.value.copy(userName = name)
            }
        }

        // Simulate mesh data for now — real WiFi Direct comes in next phase
        _uiState.value = _uiState.value.copy(
            meshActive = false,
            peersOnline = 0,
            verifiedPeers = 0,
            nearbyCamps = 0,
            openCamps = 0,
            latestBulletin = null
        )
    }

    fun onMeshToggle() {
        _uiState.value = _uiState.value.copy(
            meshActive = !_uiState.value.meshActive
        )
    }
}