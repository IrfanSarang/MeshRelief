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
    private val userPreferences: UserPreferences,
    private val tileDownloadManager: TileDownloadManager   // ← NEW
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ── Tile download state ───────────────────────────────────────────────────

    /** -1 = idle / not downloading; 0–100 = in progress or complete */
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    val mapTilesDownloaded: StateFlow<Boolean> = userPreferences.mapTilesDownloaded
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // ─────────────────────────────────────────────────────────────────────────

    init {
        _uiState.value = MapUiState(isLocating = true)

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

    // ── Tile download ─────────────────────────────────────────────────────────

    /**
     * Starts a background tile pre-fetch centred on the current user location
     * (falls back to a default if location is unavailable).
     * No-ops if a download is already in progress.
     */
    fun startTileDownload() {
        if (_downloadProgress.value in 0..99) return          // already running

        val centre = _uiState.value.userLocation
            ?: GeoPoint(19.0760, 72.8777)                     // Mumbai fallback

        viewModelScope.launch {
            tileDownloadManager.downloadTiles(centre).collect { progress ->
                _downloadProgress.value = progress
            }
        }
    }

    // ── OSMDroid configuration ────────────────────────────────────────────────

    fun configureOsmDroid(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            tileFileSystemCacheMaxBytes =
                Constants.MAP_TILE_CACHE_MB * 1024L * 1024L
            osmdroidBasePath  = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.filesDir, "osmdroid/tiles")
        }
    }

    // ── Location helpers ──────────────────────────────────────────────────────

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