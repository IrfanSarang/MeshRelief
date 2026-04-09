package com.meshrelief.features.camps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class CampResource(
    val emoji: String,
    val name: String,
    val status: ResourceStatus
)

enum class ResourceStatus { AVAILABLE, LOW, OUT }

data class CampDetail(
    val id: String,
    val name: String,
    val type: String,
    val currentOccupancy: Int,
    val capacity: Int,
    val established: String,
    val adminContact: String,
    val lastUpdated: String,
    val adminNotes: String,
    val resources: List<CampResource>,
    val latitude: Double,
    val longitude: Double
)

data class CampDetailUiState(
    val camp: CampDetail? = null,
    val showBroadcastSheet: Boolean = false,
    val broadcastMessage: String = "",
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = "",
    val filter: CampFilter = CampFilter.ALL,
    val filtered: List<CampDetail> = emptyList()
)

@HiltViewModel
class CampDetailViewModel @Inject constructor(
    private val campRepository: CampRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampDetailUiState())
    val uiState: StateFlow<CampDetailUiState> = _uiState

    // Live Room-backed list, updated whenever DB changes or a mesh event arrives
    private var liveAllCamps: List<CampDetail> = emptyList()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        // 1. Collect Room → update UI
        campRepository.getAllCamps()
            .onEach { entities ->
                liveAllCamps = entities.map { it.toCampDetail() }
                applyFilter(_uiState.value.filter)
            }
            .launchIn(viewModelScope)

        // 2. Collect mesh campUpdate events → upsert to Room
        viewModelScope.launch {
            AppEventBus.campUpdate.collect { packet ->
                runCatching {
                    val entity = json.decodeFromString<CampEntity>(packet.payload)
                    campRepository.upsert(entity)
                }
            }
        }
    }

    fun loadCamp(campId: String) {
        viewModelScope.launch {
            val entity = campRepository.getById(campId)
            val detail = entity?.toCampDetail()
            _uiState.value = _uiState.value.copy(camp = detail)
        }
    }

    fun setFilter(filter: CampFilter) {
        applyFilter(filter)
    }

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

    fun openBroadcastSheet() {
        _uiState.value = _uiState.value.copy(showBroadcastSheet = true)
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
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                type = PacketType.BULLETIN,
                senderId = "",          // filled by DeviceIdentity layer on sign
                senderName = "",
                senderPhone = "",
                payload = message,
                ttl = 5,
                timestamp = System.currentTimeMillis(),
                signature = ""          // signed inside ConnectionManager.sendPacket
            )
            connectionManager.broadcastPacket(packet)

            _uiState.value = _uiState.value.copy(
                showBroadcastSheet = false,
                broadcastMessage = "",
                showSnackbar = true,
                snackbarMessage = "Update broadcast to mesh"
            )
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false, snackbarMessage = "")
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private fun CampEntity.toCampDetail() = CampDetail(
        id = id,
        name = name,
        type = type,
        currentOccupancy = currentCount,
        capacity = capacity,
        established = "",           // not stored in entity; keep blank or add field later
        adminContact = adminId,
        lastUpdated = updatedAt.toRelativeLabel(),
        adminNotes = notes,
        resources = emptyList(),    // resource rows not in entity yet; add when schema grows
        latitude = lat,
        longitude = lng
    )

    private fun Long.toRelativeLabel(): String {
        val diff = System.currentTimeMillis() - this
        val mins = diff / 60_000
        return when {
            mins < 1   -> "just now"
            mins < 60  -> "$mins mins ago"
            mins < 1440 -> "${mins / 60} hrs ago"
            else        -> "${mins / 1440} days ago"
        }
    }
}