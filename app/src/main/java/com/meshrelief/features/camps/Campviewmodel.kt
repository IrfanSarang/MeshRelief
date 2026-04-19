package com.meshrelief.features.camps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.mesh.protocol.BulletinPayload
import com.meshrelief.mesh.protocol.CampPayload
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.decodeCamp
import com.meshrelief.mesh.protocol.encode
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CampResource(
    val emoji  : String,
    val name   : String,
    val status : ResourceStatus
)

enum class ResourceStatus { AVAILABLE, LOW, OUT }

data class CampDetail(
    val id               : String,
    val name             : String,
    val type             : String,
    val currentOccupancy : Int,
    val capacity         : Int,
    val established      : String,
    val adminContact     : String,
    val lastUpdated      : String,
    val adminNotes       : String,
    val resources        : List<CampResource>,
    val latitude         : Double,
    val longitude        : Double
)

data class CampDetailUiState(
    val camp                : CampDetail?  = null,
    val showBroadcastSheet  : Boolean      = false,
    val broadcastMessage    : String       = "",
    val showSnackbar        : Boolean      = false,
    val snackbarMessage     : String       = "",
    val filter              : CampFilter   = CampFilter.ALL,
    val filtered            : List<CampDetail> = emptyList()
)

@HiltViewModel
class CampDetailViewModel @Inject constructor(
    private val campRepository  : CampRepository,
    private val connectionManager : ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampDetailUiState())
    val uiState: StateFlow<CampDetailUiState> = _uiState

    private var liveAllCamps: List<CampDetail> = emptyList()

    init {
        // 1. Collect Room → update UI
        campRepository.getAllCamps()
            .onEach { entities ->
                liveAllCamps = entities.map { it.toCampDetail() }
                applyFilter(_uiState.value.filter)
            }
            .launchIn(viewModelScope)

        // 2. Collect mesh campUpdate events → decode via CampPayload → upsert to Room
        viewModelScope.launch {
            AppEventBus.campUpdate.collect { packet ->
                runCatching {
                    // ── Decode via schema — no longer assumes payload == CampEntity JSON ──
                    val p = packet.decodeCamp()
                    val entity = CampEntity(
                        id        = p.campId,
                        name      = p.name,
                        type      = p.type,
                        capacity  = p.capacity,
                        currentCount = p.occupancy,
                        lat       = p.lat,
                        lng       = p.lng,
                        adminId   = "",          // not in payload; filled by auth layer
                        notes     = p.adminNotes,
                        updatedAt = System.currentTimeMillis()
                    )
                    campRepository.upsert(entity)
                }
            }
        }
    }

    fun loadCamp(campId: String) {
        viewModelScope.launch {
            val entity = campRepository.getById(campId)
            _uiState.value = _uiState.value.copy(camp = entity?.toCampDetail())
        }
    }

    fun setFilter(filter: CampFilter) { applyFilter(filter) }

    private fun applyFilter(filter: CampFilter) {
        val filtered = when (filter) {
            CampFilter.ALL    -> liveAllCamps
            CampFilter.ACTIVE -> liveAllCamps.filter {
                it.currentOccupancy.toFloat() / it.capacity.coerceAtLeast(1) < 0.9f
            }
            CampFilter.FULL   -> liveAllCamps.filter {
                it.currentOccupancy.toFloat() / it.capacity.coerceAtLeast(1) >= 0.9f
            }
            CampFilter.NEARBY -> liveAllCamps.take(2)
        }
        _uiState.value = _uiState.value.copy(filter = filter, filtered = filtered)
    }

    fun openBroadcastSheet()  = _uiState.value.let {
        _uiState.value = it.copy(showBroadcastSheet = true)
    }

    fun closeBroadcastSheet() {
        _uiState.value = _uiState.value.copy(showBroadcastSheet = false, broadcastMessage = "")
    }

    fun onBroadcastMessageChange(msg: String) {
        _uiState.value = _uiState.value.copy(broadcastMessage = msg)
    }

    fun sendBroadcast() {
        val message = _uiState.value.broadcastMessage.trim()
        if (message.isBlank()) return

        viewModelScope.launch {
            // ── Bulletin payload uses BulletinPayload schema ──
            val bulletinPayload = BulletinPayload(
                category = "GENERAL",
                text     = message
            )

            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.BULLETIN,
                senderId    = "",
                senderName  = "",
                senderPhone = "",
                payload     = bulletinPayload.encode(),
                ttl         = 5,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )
            connectionManager.broadcastPacket(packet)

            _uiState.value = _uiState.value.copy(
                showBroadcastSheet = false,
                broadcastMessage   = "",
                showSnackbar       = true,
                snackbarMessage    = "Update broadcast to mesh"
            )
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false, snackbarMessage = "")
    }

    // ── Broadcast own camp status over mesh ───────────────────────────────────

    /**
     * Call this whenever local camp data changes and should be propagated to peers.
     * Encodes via CampPayload — the standard schema for CAMP_UPDATE packets.
     */
    fun broadcastCampUpdate(camp: CampDetail) {
        viewModelScope.launch {
            val campPayload = CampPayload(
                campId     = camp.id,
                name       = camp.name,
                type       = camp.type,
                capacity   = camp.capacity,
                occupancy  = camp.currentOccupancy,
                lat        = camp.latitude,
                lng        = camp.longitude,
                adminNotes = camp.adminNotes
            )

            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.CAMP_UPDATE,
                senderId    = "",
                senderName  = "",
                senderPhone = "",
                payload     = campPayload.encode(),
                ttl         = 5,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )
            connectionManager.broadcastPacket(packet)
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun CampEntity.toCampDetail() = CampDetail(
        id               = id,
        name             = name,
        type             = type,
        currentOccupancy = currentCount,
        capacity         = capacity,
        established      = "",
        adminContact     = adminId,
        lastUpdated      = updatedAt.toRelativeLabel(),
        adminNotes       = notes,
        resources        = emptyList(),
        latitude         = lat,
        longitude        = lng
    )

    private fun Long.toRelativeLabel(): String {
        val diff = System.currentTimeMillis() - this
        val mins = diff / 60_000
        return when {
            mins < 1    -> "just now"
            mins < 60   -> "$mins mins ago"
            mins < 1440 -> "${mins / 60} hrs ago"
            else        -> "${mins / 1440} days ago"
        }
    }
}