package com.meshrelief.features.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.core.util.Constants
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.data.repository.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import java.io.File
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
    private val campRepository: CampRepository,
    private val userPreferences: UserPreferences          // ← NEW
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ── NEW: expose MAP_TILES_DOWNLOADED as a StateFlow ───────────────────────
    val mapTilesDownloaded: StateFlow<Boolean> = userPreferences.mapTilesDownloaded
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

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

    // ── NEW: configure OSMDroid to use local cache ────────────────────────────
    /**
     * Must be called once from the Composable (inside LaunchedEffect) before
     * the MapView is created.  Sets the tile cache path and size so OSMDroid
     * reads/writes tiles locally instead of always hitting the network.
     */
    fun configureOsmDroid(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            tileFileSystemCacheMaxBytes =
                Constants.MAP_TILE_CACHE_MB * 1024L * 1024L          // 200 MB
            osmdroidBasePath  = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.filesDir, "osmdroid/tiles")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

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