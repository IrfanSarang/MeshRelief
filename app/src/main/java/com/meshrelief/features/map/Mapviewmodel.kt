package com.meshrelief.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.data.repository.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

// ── Data models ───────────────────────────────────────────────────────────────

data class PeerMapMarker(
    val id: String,
    val name: String,
    val position: GeoPoint,
    val triage: TriageStatus,
    val lastSeenMinutesAgo: Int
)

data class CampMapMarker(
    val id: String,
    val name: String,
    val position: GeoPoint,
    val capacity: Int,
    val currentOccupancy: Int
)

data class MapUiState(
    val userLocation: GeoPoint? = null,
    val peers: List<PeerMapMarker> = emptyList(),
    val camps: List<CampMapMarker> = emptyList(),
    val isLocating: Boolean = false,
    val showLegend: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val peerRepository: PeerRepository,
    private val campRepository: CampRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = MapUiState(isLocating = true)

        // Combine both Room flows; any emission from either triggers a UI update
        viewModelScope.launch {
            combine(
                peerRepository.getAllPeers(),
                campRepository.getAllCamps()
            ) { peers, camps ->
                peers.map { it.toMarker() } to camps.map { it.toMarker() }
            }.collect { (peerMarkers, campMarkers) ->
                _uiState.value = _uiState.value.copy(
                    peers = peerMarkers,
                    camps = campMarkers
                )
            }
        }

        viewModelScope.launch { refreshLocation() }
    }

    suspend fun refreshLocation() {
        _uiState.value = _uiState.value.copy(isLocating = true)
        val fix = locationProvider.getLastKnownLocation()
        _uiState.value = _uiState.value.copy(
            userLocation = fix ?: _uiState.value.userLocation,
            isLocating = false
        )
    }

    fun toggleLegend() {
        _uiState.value = _uiState.value.copy(showLegend = !_uiState.value.showLegend)
    }

    fun onRecenterRequested() {
        viewModelScope.launch { refreshLocation() }
    }

    val peerCount: Int get() = _uiState.value.peers.size

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun PeerEntity.toMarker() = PeerMapMarker(
        id = deviceId,
        name = name,
        position = GeoPoint(lat, lng),
        triage = triageStatus.toTriageStatus(),
        lastSeenMinutesAgo = ((System.currentTimeMillis() - lastSeen) / 60_000).toInt()
    )

    private fun CampEntity.toMarker() = CampMapMarker(
        id = id,
        name = name,
        position = GeoPoint(lat, lng),
        capacity = capacity,
        currentOccupancy = currentCount
    )

    private fun String.toTriageStatus(): TriageStatus = when (this.uppercase()) {
        "GREEN"  -> TriageStatus.GREEN
        "AMBER"  -> TriageStatus.AMBER
        "RED"    -> TriageStatus.RED
        else     -> TriageStatus.UNKNOWN
    }
}