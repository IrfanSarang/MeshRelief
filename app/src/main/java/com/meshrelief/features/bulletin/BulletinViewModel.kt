package com.meshrelief.features.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.data.db.entity.BulletinEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.BulletinRepository
import com.meshrelief.mesh.protocol.BulletinPayload
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.decodeBulletin
import com.meshrelief.mesh.protocol.encode
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ─── Data models ──────────────────────────────────────────────────────────────

enum class BulletinCategory(val label: String) {
    EVACUATION("Evacuation"),
    MEDICAL("Medical"),
    RESOURCES("Resources"),
    GENERAL("General")
}

data class BulletinItem(
    val id              : String,
    val senderName      : String,
    val category        : BulletinCategory,
    val message         : String,
    val timestampMillis : Long,
    val isRelayed       : Boolean
)

data class BulletinUiState(
    val bulletins        : List<BulletinItem>  = emptyList(),
    val selectedFilter   : BulletinCategory?   = null,
    val isSheetOpen      : Boolean             = false,
    val composeCategory  : BulletinCategory    = BulletinCategory.GENERAL,
    val composeText      : String              = ""
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class BulletinViewModel @Inject constructor(
    private val repository        : BulletinRepository,
    private val connectionManager : ConnectionManager,
    private val userPreferences   : UserPreferences,
    private val appEventBus       : AppEventBus       // BUG 1 FIX: injected, not static
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulletinUiState())
    val uiState: StateFlow<BulletinUiState> = _uiState.asStateFlow()

    init {
        // 1. Stream Room → UI
        viewModelScope.launch {
            repository.getAllBulletins()
                .map { entities -> entities.map { it.toBulletinItem() } }
                .collect { items -> _uiState.update { it.copy(bulletins = items) } }
        }

        // 2. Collect incoming bulletin packets from the mesh
        // BUG 1 FIX: use injected appEventBus, not static AppEventBus object
        viewModelScope.launch {
            appEventBus.bulletin.collect { packet ->
                val entity = packet.toBulletinEntity(isRelayed = true)
                repository.save(entity)
            }
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    fun setFilter(category: BulletinCategory?) {
        _uiState.update { it.copy(selectedFilter = category) }
    }

    fun filteredBulletins(): List<BulletinItem> {
        val state = _uiState.value
        val base = if (state.selectedFilter == null) {
            state.bulletins
        } else {
            state.bulletins.filter { it.category == state.selectedFilter }
        }
        return base.sortedWith(
            compareByDescending<BulletinItem> { it.category == BulletinCategory.EVACUATION }
                .thenByDescending { it.timestampMillis }
        )
    }

    // ── Compose sheet ─────────────────────────────────────────────────────────

    fun openSheet()  = _uiState.update { it.copy(isSheetOpen = true) }

    fun closeSheet() = _uiState.update {
        it.copy(isSheetOpen = false, composeText = "", composeCategory = BulletinCategory.GENERAL)
    }

    fun setComposeCategory(cat: BulletinCategory) =
        _uiState.update { it.copy(composeCategory = cat) }

    fun setComposeText(text: String) {
        if (text.length <= 280) _uiState.update { it.copy(composeText = text) }
    }

    fun broadcast() {
        val state   = _uiState.value
        val trimmed = state.composeText.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            val senderName = userPreferences.userName.first().ifBlank { "Unknown" }
            val senderId   = userPreferences.userDeviceId.first().ifBlank { UUID.randomUUID().toString() }

            val packetId  = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            val bulletinPayload = BulletinPayload(
                category = state.composeCategory.name,
                text     = trimmed
            )

            val packet = MeshPacket(
                id          = packetId,
                type        = PacketType.BULLETIN,
                senderId    = senderId,
                senderName  = senderName,
                senderPhone = "",
                payload     = bulletinPayload.encode(),
                ttl         = 5,
                timestamp   = timestamp,
                signature   = ""
            )
            connectionManager.broadcastPacket(packet)

            val entity = BulletinEntity(
                id         = packetId,
                senderId   = senderId,
                senderName = senderName,
                type       = state.composeCategory.name,
                content    = trimmed,
                timestamp  = timestamp,
                relayCount = 0
            )
            repository.save(entity)

            closeSheet()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun relativeTime(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        val mins = (diff / 60_000).toInt()
        return when {
            mins < 1   -> "just now"
            mins == 1  -> "1 min ago"
            mins < 60  -> "$mins mins ago"
            mins < 120 -> "1 hr ago"
            else       -> "${mins / 60} hrs ago"
        }
    }
}

// ─── Mapping extensions ───────────────────────────────────────────────────────

private fun BulletinEntity.toBulletinItem(): BulletinItem =
    BulletinItem(
        id              = id,
        senderName      = senderName,
        category        = runCatching { BulletinCategory.valueOf(type) }
            .getOrDefault(BulletinCategory.GENERAL),
        message         = content,
        timestampMillis = timestamp,
        isRelayed       = relayCount > 0
    )

private fun MeshPacket.toBulletinEntity(isRelayed: Boolean): BulletinEntity {
    val (category, text) = runCatching {
        val p = decodeBulletin()
        p.category to p.text
    }.getOrElse {
        val parts = payload.split("|", limit = 2)
        val cat   = if (parts.size == 2)
            runCatching { BulletinCategory.valueOf(parts[0]) }.getOrNull()?.name
                ?: BulletinCategory.GENERAL.name
        else BulletinCategory.GENERAL.name
        val txt = if (parts.size == 2) parts[1] else payload
        cat to txt
    }

    return BulletinEntity(
        id         = id,
        senderId   = senderId,
        senderName = senderName,
        type       = category,
        content    = text,
        timestamp  = timestamp,
        relayCount = if (isRelayed) 1 else 0
    )
}