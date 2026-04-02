package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.util.Constants
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TriageStatus(val label: String, val color: Long) {
    SAFE("Safe", 0xFF1D9E75),
    MINOR("Minor injury", 0xFFEF9F27),
    CRITICAL("Critical", 0xFFE24B4A),
    UNRESPONSIVE("Unresponsive", 0xFF888780)
}

data class SOSUiState(
    val selectedTriage: TriageStatus = TriageStatus.SAFE,
    val showConfirmDialog: Boolean = false,
    val confirmCountdown: Int = 10,
    val cooldownRemainingMs: Long = 0L,
    val sosSent: Boolean = false,
    val userName: String = "",
    val userPhone: String = ""
)

@HiltViewModel
class SOSViewModel @Inject constructor(
    private val userPreferences: UserPreferences
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

    fun onTriageSelected(triage: TriageStatus) {
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
        // WiFi Direct broadcast will be added in Phase 2
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
            // Auto cancel after 10 seconds
            onCancelSOS()
        }
    }

    private fun startCooldown() {
        val cooldownMs = when (_uiState.value.selectedTriage) {
            TriageStatus.CRITICAL -> Constants.SOS_COOLDOWN_CRITICAL_MS
            else -> Constants.SOS_COOLDOWN_DEFAULT_MS
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