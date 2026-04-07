package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.model.TriageLevel
import com.meshrelief.core.util.Constants
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// The local "enum class TriageStatus" has been removed.
// This file now uses com.meshrelief.core.model.TriageLevel (SAFE, MINOR, CRITICAL, UNKNOWN).
//
// Migration note — UNRESPONSIVE → UNKNOWN:
//   The old local enum had an UNRESPONSIVE value that does not exist in the
//   canonical TriageLevel. UNRESPONSIVE has been mapped to UNKNOWN because
//   both indicate the same UX outcome (person cannot self-report). If a
//   distinct UNRESPONSIVE state is needed in the future it should be added
//   to TriageLevel in core/model/ rather than re-introduced locally.
//
// label / color are now sourced from TriageLevel.label and TriageLevel.color
// so the UI layer no longer needs to maintain its own colour mapping.

data class SOSUiState(
    val selectedTriage: TriageLevel = TriageLevel.SAFE,   // was: local TriageStatus
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

    fun onTriageSelected(triage: TriageLevel) {             // was: local TriageStatus
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
        // Wi-Fi Direct broadcast will be added in Phase 2
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