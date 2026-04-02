package com.meshrelief.features.evacuation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WayPoint(val lat: Double, val lng: Double)

data class EvacuationRouteUiState(
    val waypoints: List<WayPoint> = emptyList(),
    val routeLabel: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean = false
)

@HiltViewModel
class EvacuationRouteViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EvacuationRouteUiState())
    val uiState: StateFlow<EvacuationRouteUiState> = _uiState.asStateFlow()

    fun addWaypoint(lat: Double, lng: Double) {
        val current = _uiState.value.waypoints
        if (current.size >= 10) return
        _uiState.value = _uiState.value.copy(
            waypoints = current + WayPoint(lat, lng)
        )
    }

    fun undoLast() {
        val current = _uiState.value.waypoints
        if (current.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            waypoints = current.dropLast(1)
        )
    }

    fun clearAll() {
        _uiState.value = _uiState.value.copy(waypoints = emptyList())
    }

    fun setRouteLabel(label: String) {
        _uiState.value = _uiState.value.copy(routeLabel = label)
    }

    fun broadcastRoute(onDone: () -> Unit) {
        if (_uiState.value.waypoints.size < 2) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBroadcasting = true)
            kotlinx.coroutines.delay(1200L)
            _uiState.value = _uiState.value.copy(
                isBroadcasting = false,
                broadcastSuccess = true
            )
            kotlinx.coroutines.delay(1500L)
            onDone()
        }
    }
}