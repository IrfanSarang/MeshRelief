package com.meshrelief.features.map

import androidx.lifecycle.ViewModel
import com.meshrelief.core.model.TriageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

// TriageStatus is now com.meshrelief.core.model.TriageStatus (GREEN, AMBER, RED, UNKNOWN).
// The local enum class has been removed.

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
class MapViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Mumbai disaster scenario sample data
    private val sampleUserLocation = GeoPoint(19.0760, 72.8777)

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
        _uiState.value = MapUiState(
            userLocation = sampleUserLocation,
            peers = samplePeers,
            camps = sampleCamps,
            isLocating = false,
            showLegend = false
        )
    }

    fun toggleLegend() {
        _uiState.value = _uiState.value.copy(showLegend = !_uiState.value.showLegend)
    }

    fun onRecenterRequested() {
        // In production: trigger GPS re-fetch here
        // For now, the FAB click in the screen handles map camera move directly
    }

    val peerCount: Int get() = _uiState.value.peers.size
}