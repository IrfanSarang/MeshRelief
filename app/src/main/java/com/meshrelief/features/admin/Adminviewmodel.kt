package com.meshrelief.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.core.util.Constants
import com.meshrelief.data.db.entity.BulletinEntity
import com.meshrelief.data.repository.BulletinRepository
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.data.repository.PeerRepository
import com.meshrelief.data.repository.SOSRepository
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.core.crypto.DeviceIdentity
import kotlinx.coroutines.flow.first


// ── Data models ───────────────────────────────────────────────────────────────

data class AdminPeer(
    val id: String,
    val name: String,
    val triage: TriageStatus,
    val lastSeenMinutesAgo: Int,
    val verified: Boolean = false,
    val flagged: Boolean = false
)

data class AdminSosAlert(
    val id: String,
    val senderName: String,
    val triage: TriageStatus,
    val lat: Double,
    val lon: Double,
    val minutesAgo: Int,
    val acknowledged: Boolean = false
)

data class AdminCamp(
    val id: String,
    val name: String,
    val occupancy: Int,
    val capacity: Int
)

data class BulletinEntry(
    val id: String,
    val message: String,
    val timeLabel: String
)

data class AdminUiState(
    val adminDeviceName: String = "ADMIN-NODE-01",
    val peers: List<AdminPeer> = emptyList(),
    val sosAlerts: List<AdminSosAlert> = emptyList(),
    val camps: List<AdminCamp> = emptyList(),
    val bulletins: List<BulletinEntry> = emptyList(),
    val messagesToday: Int = 0,
    val isScanning: Boolean = false,
    val bulletinDraft: String = "",
    val headcountInProgress: Boolean = false,
    val headcountResponders: List<String> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val peerRepo: PeerRepository,
    private val sosRepo: SOSRepository,
    private val campRepo: CampRepository,
    private val bulletinRepo: BulletinRepository,
    private val connectionManager: ConnectionManager,
    private val appEventBus: AppEventBus,
    private val userPreferences: UserPreferences,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var headcountJob: Job? = null

    init {
        observeRoomData()
    }

    // ── 1 + 2. Collect all four repository flows ──────────────────────────

    private fun observeRoomData() {
        viewModelScope.launch {
            combine(
                peerRepo.getAllPeers(),
                sosRepo.getActiveAlerts(),
                campRepo.getAllCamps(),
                bulletinRepo.getAllBulletins()
            ) { peers, alerts, camps, bulletins ->

                val nowMs = System.currentTimeMillis()

                val adminPeers = peers.map { entity ->
                    AdminPeer(
                        id = entity.deviceId,
                        name = entity.name,
                        triage = triageFromString(entity.triageStatus),
                        lastSeenMinutesAgo = ((nowMs - entity.lastSeen) / 60_000L)
                            .coerceAtLeast(0L).toInt(),
                        verified = entity.verified,
                        flagged  = entity.flagged
                    )
                }

                val adminAlerts = alerts.map { entity ->
                    AdminSosAlert(
                        id = entity.id,
                        senderName = entity.senderName,
                        triage = triageFromString(entity.triageStatus),
                        lat = entity.lat,
                        lon = entity.lng,
                        minutesAgo = ((nowMs - entity.timestamp) / 60_000L)
                            .coerceAtLeast(0L).toInt(),
                        acknowledged = entity.resolved
                    )
                }

                val adminCamps = camps.map { entity ->
                    AdminCamp(
                        id = entity.id,
                        name = entity.name,
                        occupancy = entity.currentCount,
                        capacity = entity.capacity
                    )
                }

                val bulletinEntries = bulletins.map { entity ->
                    BulletinEntry(
                        id = entity.id,
                        message = entity.content,
                        timeLabel = formatTimestamp(entity.timestamp)
                    )
                }

                Triple(
                    adminPeers,
                    Triple(adminAlerts, adminCamps, bulletinEntries),
                    bulletinEntries.size
                )
            }.collect { (peers, middle, msgCount) ->
                val (alerts, camps, bulletinEntries) = middle
                _uiState.update { current ->
                    current.copy(
                        peers = peers,
                        sosAlerts = alerts,
                        camps = camps,
                        bulletins = bulletinEntries,
                        messagesToday = msgCount
                    )
                }
            }
        }
    }

    // ── 3. broadcastBulletin ──────────────────────────────────────────────

    fun broadcastBulletin() {
        val draft = _uiState.value.bulletinDraft.trim()
        if (draft.isBlank()) return

        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val nowMs = System.currentTimeMillis()

            val deviceId = userPreferences.userDeviceId.first()
            val name = userPreferences.userName.first()
            val phone = userPreferences.userPhone.first()

            val entity = BulletinEntity(
                id = id,
                senderId = deviceId,
                senderName = name,
                type = "ADMIN_BROADCAST",
                content = draft,
                timestamp = nowMs,
                relayCount = 0
            )
            bulletinRepo.save(entity)

            val packet = MeshPacket(
                id = id,
                type = PacketType.BULLETIN,
                senderId = deviceId,
                senderName = name,
                senderPhone = phone,
                payload = draft,
                ttl = Constants.DEFAULT_TTL,
                timestamp = nowMs,
                signature = ""
            )

            connectionManager.broadcastPacket(packet)

            _uiState.update { it.copy(bulletinDraft = "") }
        }
    }

    // ── 4. acknowledgeAlert ───────────────────────────────────────────────

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            sosRepo.markResolved(alertId)
        }
    }

    // ── 5. triggerHeadcount ───────────────────────────────────────────────

    fun triggerHeadcount() {
        if (_uiState.value.headcountInProgress) return

        headcountJob?.cancel()

        headcountJob = viewModelScope.launch {
            val senderName = _uiState.value.adminDeviceName
            val nowMs = System.currentTimeMillis()

            _uiState.update {
                it.copy(
                    headcountInProgress = true,
                    isScanning = true,
                    headcountResponders = emptyList()
                )
            }

            val pingPacket = MeshPacket(
                id = UUID.randomUUID().toString(),
                type = PacketType.HEADCOUNT_PING,
                senderId = senderName,
                senderName = senderName,
                senderPhone = "",
                payload = "",
                ttl = Constants.DEFAULT_TTL,
                timestamp = nowMs,
                signature = ""
            )
            connectionManager.broadcastPacket(pingPacket)

            withTimeoutOrNull(Constants.HEADCOUNT_RESPONSE_TIMEOUT_S * 1_000L) {
                appEventBus.headcountResponse.collect { responsePacket ->
                    _uiState.update { state ->
                        state.copy(
                            headcountResponders = state.headcountResponders + responsePacket.senderName
                        )
                    }
                }
            }

            _uiState.update { it.copy(headcountInProgress = false, isScanning = false) }
        }
    }

    // ── MISSING 4 FIX: Trust system actions ──────────────────────────────

    /**
     * Broadcasts a PEER_VERIFY packet mesh-wide so every node (including the
     * target) marks [peerId] as verified in their local Room DB.
     * payload = peerId so MeshForegroundService can call peerDao.setVerified().
     */
    fun verifyPeer(peerId: String) {
        viewModelScope.launch {
            val deviceId = userPreferences.userDeviceId.first()
            val name     = userPreferences.userName.first()
            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.PEER_VERIFY,
                senderId    = deviceId,
                senderName  = name,
                senderPhone = "",
                payload     = peerId,           // target peer's deviceId
                ttl         = Constants.DEFAULT_TTL,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )
            connectionManager.broadcastPacket(packet)
        }
    }

    /**
     * Broadcasts a PEER_FLAG packet mesh-wide so every node marks [peerId]
     * as flagged in their local Room DB.
     * payload = peerId so MeshForegroundService can call peerDao.setFlagged().
     */
    fun flagPeer(peerId: String) {
        viewModelScope.launch {
            val deviceId = userPreferences.userDeviceId.first()
            val name     = userPreferences.userName.first()
            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.PEER_FLAG,
                senderId    = deviceId,
                senderName  = name,
                senderPhone = "",
                payload     = peerId,           // target peer's deviceId
                ttl         = Constants.DEFAULT_TTL,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )
            connectionManager.broadcastPacket(packet)
        }
    }

    // ── Screen-facing aliases ─────────────────────────────────────────────

    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        triggerHeadcount()
    }

    fun acknowledgeSos(alertId: String) = acknowledgeAlert(alertId)

    // ── Draft helper ──────────────────────────────────────────────────────

    fun onBulletinDraftChange(text: String) {
        _uiState.update { it.copy(bulletinDraft = text) }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun triageFromString(raw: String): TriageStatus =
        runCatching { TriageStatus.valueOf(raw) }.getOrDefault(TriageStatus.UNKNOWN)

    private fun formatTimestamp(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        val h    = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m    = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (h < 12) "AM" else "PM"
        val hour = if (h % 12 == 0) 12 else h % 12
        return "%02d:%02d %s".format(hour, m, ampm)
    }
}