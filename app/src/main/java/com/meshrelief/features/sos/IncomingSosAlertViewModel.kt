package com.meshrelief.features.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IncomingTriageLevel {
    SAFE, MINOR, CRITICAL, UNRESPONSIVE
}

data class IncomingSosUiState(
    val senderName: String = "Unknown",
    val senderIdSuffix: String = "0000",
    val isVerified: Boolean = false,
    val hopCount: Int = 1,
    val triage: IncomingTriageLevel = IncomingTriageLevel.CRITICAL,
    val latRaw: Double = 19.0760,
    val lngRaw: Double = 72.8777,
    val distanceKm: Float = 0.8f,
    val directionLabel: String = "NE",
    val message: String = "",
    val receivedTimeLabel: String = "Just now",
    val isRelaying: Boolean = false,
    val relaySuccess: Boolean = false,
    val isDismissed: Boolean = false
)

@HiltViewModel
class IncomingSosAlertViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingSosUiState())
    val uiState: StateFlow<IncomingSosUiState> = _uiState.asStateFlow()

    fun loadFromPacket(
        senderName: String,
        senderIdSuffix: String,
        isVerified: Boolean,
        hopCount: Int,
        triage: IncomingTriageLevel,
        lat: Double,
        lng: Double,
        distanceKm: Float,
        directionLabel: String,
        message: String,
        receivedTimeLabel: String
    ) {
        _uiState.value = IncomingSosUiState(
            senderName = senderName,
            senderIdSuffix = senderIdSuffix,
            isVerified = isVerified,
            hopCount = hopCount,
            triage = triage,
            latRaw = lat,
            lngRaw = lng,
            distanceKm = distanceKm,
            directionLabel = directionLabel,
            message = message,
            receivedTimeLabel = receivedTimeLabel
        )
    }

    fun relayFurther() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRelaying = true)
            kotlinx.coroutines.delay(1000L)
            _uiState.value = _uiState.value.copy(isRelaying = false, relaySuccess = true)
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(isDismissed = true)
    }
}