package com.meshrelief.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.core.util.Constants
import com.meshrelief.data.db.entity.BulletinEntity
import com.meshrelief.data.db.entity.SOSEntity
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

// ── Data models ──────────────────────────────────────────────────────────────

data class AdminPeer(
    val id: String,
    val name: String,
    val triage: TriageStatus,
    val lastSeenMinutesAgo: Int
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
    // ── Headcount ─────────────────────────────────────────────────────────
    val headcountInProgress: Boolean = false,
    val headcountResponders: List<String> = emptyList()   // senderName of each ACK
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val peerRepo: PeerRepository,
    private val sosRepo: SOSRepository,
    private val campRepo: CampRepository,
    private val bulletinRepo: BulletinRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    // Keeps track of an in-flight headcount collection job so we can cancel
    // early (e.g. if the admin navigates away) without leaking coroutines.
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
                            .coerceAtLeast(0L).toInt()
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

                // Return a plain data snapshot — not a lambda.
                // Fields that live outside Room (headcount, draft) are
                // merged in the collect block below using _uiState.update.
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

    /**
     * Persists the bulletin to Room and broadcasts a BULLETIN [MeshPacket]
     * to all currently-connected peers via [ConnectionManager].
     */
    fun broadcastBulletin() {
        val draft = _uiState.value.bulletinDraft.trim()
        if (draft.isBlank()) return

        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val nowMs = System.currentTimeMillis()
            val senderName = _uiState.value.adminDeviceName

            // 3a. Persist to Room first (source of truth; UI updates via flow)
            val entity = BulletinEntity(
                id = id,
                senderId = senderName,        // admin device acts as sender
                senderName = senderName,
                type = "ADMIN_BROADCAST",
                content = draft,
                timestamp = nowMs,
                relayCount = 0
            )
            bulletinRepo.save(entity)

            // 3b. Broadcast over the mesh
            // TODO: replace senderId / senderPhone with real DeviceIdentity
            //       values once Issue #15 wires them into the ViewModel.
            val packet = MeshPacket(
                id = id,
                type = PacketType.BULLETIN,
                senderId = senderName,
                senderName = senderName,
                senderPhone = "",
                payload = draft,
                ttl = Constants.DEFAULT_TTL,
                timestamp = nowMs,
                signature = ""              // ConnectionManager.sendPacket signs this
            )
            connectionManager.broadcastPacket(packet)

            // 3c. Clear the draft — Room flow will repopulate bulletins list
            _uiState.update { it.copy(bulletinDraft = "") }
        }
    }

    // ── 4. acknowledgeAlert ───────────────────────────────────────────────

    /**
     * Marks the SOS alert as resolved in Room.
     * The active-alerts flow (`getActiveAlerts`) will automatically remove
     * it from the UI on the next emission because the query filters
     * `WHERE resolved = 0`.
     */
    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            sosRepo.markResolved(alertId)
        }
    }

    // ── 5. triggerHeadcount ───────────────────────────────────────────────

    /**
     * Broadcasts a HEADCOUNT_PING packet, then collects HEADCOUNT_RESPONSE
     * packets from [AppEventBus.headcountResponse] for up to
     * [Constants.HEADCOUNT_RESPONSE_TIMEOUT_S] seconds.
     *
     * Each responding peer's name is appended to [AdminUiState.headcountResponders]
     * in real time so the UI can show a live count.
     *
     * If a headcount is already in progress the call is a no-op.
     */
    fun triggerHeadcount() {
        if (_uiState.value.headcountInProgress) return

        // Cancel any stale job (safety net — shouldn't normally be needed)
        headcountJob?.cancel()

        headcountJob = viewModelScope.launch {
            val senderName = _uiState.value.adminDeviceName
            val nowMs = System.currentTimeMillis()

            // 5a. Reset responder list and mark in-progress
            _uiState.update {
                it.copy(
                    headcountInProgress = true,
                    isScanning = true,
                    headcountResponders = emptyList()
                )
            }

            // 5b. Broadcast HEADCOUNT_PING
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

            // 5c. Collect HEADCOUNT_RESPONSE packets until the timeout expires.
            //     withTimeoutOrNull returns null when the block times out, which
            //     is the expected "normal" exit path — not an error.
            withTimeoutOrNull(Constants.HEADCOUNT_RESPONSE_TIMEOUT_S * 1_000L) {
                AppEventBus.headcountResponse.collect { responsePacket ->
                    _uiState.update { state ->
                        state.copy(
                            headcountResponders = state.headcountResponders + responsePacket.senderName
                        )
                    }
                }
            }

            // 5d. Timeout elapsed — mark complete
            _uiState.update { it.copy(headcountInProgress = false, isScanning = false) }
        }
    }

    // ── Screen-facing aliases / legacy names ──────────────────────────────

    /**
     * Called by AdminScreen's FAB. Reuses [triggerHeadcount] so the ping
     * broadcasts immediately and the FAB spinner plays for the full timeout.
     * [AdminUiState.isScanning] tracks the same in-progress window.
     */
    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        triggerHeadcount()
    }

    /** Alias kept for AdminScreen call site — delegates to [acknowledgeAlert]. */
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
        val h   = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m   = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (h < 12) "AM" else "PM"
        val hour = if (h % 12 == 0) 12 else h % 12
        return "%02d:%02d %s".format(hour, m, ampm)
    }
}