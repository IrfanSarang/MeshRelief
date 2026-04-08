package com.meshrelief.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.core.model.TriageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

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
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Stub peer/camp data — real data wired in Issue #4/#5
    private val samplePeers = listOf(
        PeerMapMarker(
            id = "peer_001",
            name = "Ravi Kumar",
            position = GeoPoint(19.0790, 72.8800),
            triage = TriageStatus.GREEN,
            lastSeenMinutesAgo = 2
        ),
        PeerMapMarker(
            id = "peer_002",
            name = "Priya Sharma",
            position = GeoPoint(19.0745, 72.8810),
            triage = TriageStatus.RED,
            lastSeenMinutesAgo = 5
        ),
        PeerMapMarker(
            id = "peer_003",
            name = "Amir Sheikh",
            position = GeoPoint(19.0720, 72.8755),
            triage = TriageStatus.AMBER,
            lastSeenMinutesAgo = 8
        ),
        PeerMapMarker(
            id = "peer_004",
            name = "Sunita Patil",
            position = GeoPoint(19.0780, 72.8740),
            triage = TriageStatus.GREEN,
            lastSeenMinutesAgo = 1
        ),
        PeerMapMarker(
            id = "peer_005",
            name = "Deepak Nair",
            position = GeoPoint(19.0735, 72.8820),
            triage = TriageStatus.UNKNOWN,
            lastSeenMinutesAgo = 22
        ),
        PeerMapMarker(
            id = "peer_006",
            name = "Fatima Ansari",
            position = GeoPoint(19.0800, 72.8765),
            triage = TriageStatus.AMBER,
            lastSeenMinutesAgo = 3
        )
    )

    private val sampleCamps = listOf(
        CampMapMarker(
            id = "camp_001",
            name = "Azad Maidan Relief Camp",
            position = GeoPoint(19.0755, 72.8830),
            capacity = 500,
            currentOccupancy = 320
        ),
        CampMapMarker(
            id = "camp_002",
            name = "Shivaji Park Medical Post",
            position = GeoPoint(19.0710, 72.8730),
            capacity = 200,
            currentOccupancy = 189
        ),
        CampMapMarker(
            id = "camp_003",
            name = "Dadar Community Shelter",
            position = GeoPoint(19.0820, 72.8780),
            capacity = 300,
            currentOccupancy = 95
        )
    )

    init {
        // Seed stub data immediately so the map isn't empty while locating
        _uiState.value = MapUiState(
            peers = samplePeers,
            camps = sampleCamps,
            isLocating = true
        )
        viewModelScope.launch { refreshLocation() }
    }

    /**
     * Asks LocationProvider for the best available fix and updates uiState.
     * If no fix is available the previous userLocation is kept (null on first
     * launch) so the map can still show peers/camps without a user pin.
     */
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
}