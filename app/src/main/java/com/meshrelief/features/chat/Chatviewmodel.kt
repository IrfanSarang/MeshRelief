package com.meshrelief.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.data.db.entity.MessageEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.MessageRepository
import com.meshrelief.data.repository.PeerRepository
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val peerRepository: PeerRepository,
    private val connectionManager: ConnectionManager,
    private val userPreferences: UserPreferences,
    private val deviceIdentity: DeviceIdentity
) : ViewModel() {

    // ── Local identity (resolved once from DataStore) ─────────────────────────

    /**
     * Holds the current user's full device ID once it has been read from
     * DataStore. Empty string until the first emission arrives.
     */
    private val _myDeviceId = MutableStateFlow("")
    private val _myName     = MutableStateFlow("")
    private val _myPhone    = MutableStateFlow("")

    /** Convenience: last 4 chars of device ID, shown as sender suffix in the UI. */
    private val myIdSuffix get() = _myDeviceId.value.takeLast(4)

    // ── Group chat state ──────────────────────────────────────────────────────

    /**
     * Live group messages from Room, mapped to [ChatMessage].
     * Automatically updates whenever the database changes.
     */
    val groupMessages: StateFlow<List<ChatMessage>> = _myDeviceId
        .flatMapLatest { myId ->
            if (myId.isEmpty()) flowOf(emptyList())
            else messageRepository.getGroupMessages()
                .map { entities -> entities.map { it.toChatMessage(myId) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _groupInput = MutableStateFlow("")
    val groupInput: StateFlow<String> = _groupInput.asStateFlow()

    // ── P2P state ─────────────────────────────────────────────────────────────

    /**
     * Live peer list from Room, mapped to [ChatPeer].
     * Unread counts are overlaid from [_unreadCountMap].
     */
    private val _unreadCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())

    val peers: StateFlow<List<ChatPeer>> = combine(
        peerRepository.getAllPeers(),
        _unreadCountMap
    ) { entities, unreadMap ->
        entities.map { entity ->
            ChatPeer(
                deviceId    = entity.deviceId,
                name        = entity.name,
                idSuffix    = entity.phone4,
                triageColor = entity.triageStatus.lowercase(),
                hopCount    = entity.hopCount,
                unreadCount = unreadMap[entity.deviceId] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPeer = MutableStateFlow<ChatPeer?>(null)
    val selectedPeer: StateFlow<ChatPeer?> = _selectedPeer.asStateFlow()

    /**
     * Live P2P thread for the currently selected peer.
     * Switches automatically when [_selectedPeer] changes.
     */
    val p2pMessages: StateFlow<List<ChatMessage>> = combine(
        _selectedPeer,
        _myDeviceId
    ) { peer, myId -> Pair(peer, myId) }
        .flatMapLatest { (peer, myId) ->
            if (peer == null || myId.isEmpty()) flowOf(emptyList())
            else messageRepository.getP2pMessages(myId, peer.deviceId)
                .map { entities -> entities.map { it.toChatMessage(myId) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _p2pInput = MutableStateFlow("")
    val p2pInput: StateFlow<String> = _p2pInput.asStateFlow()

    // ── Unread badges ─────────────────────────────────────────────────────────

    private val _unreadGroup = MutableStateFlow(0)
    val unreadGroup: StateFlow<Int> = _unreadGroup.asStateFlow()

    /** Total unread P2P count = sum of all per-peer unread counts. */
    val unreadP2p: StateFlow<Int> = _unreadCountMap
        .map { map -> map.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // 1. Resolve local identity from DataStore
        viewModelScope.launch {
            userPreferences.userDeviceId.collect { id -> _myDeviceId.value = id }
        }
        viewModelScope.launch {
            userPreferences.userName.collect { name -> _myName.value = name }
        }
        viewModelScope.launch {
            userPreferences.userPhone.collect { phone -> _myPhone.value = phone }
        }

        // 2. Collect incoming messages from the event bus (Issue #3 — step 6)
        viewModelScope.launch {
            AppEventBus.incomingMessage.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onGroupInputChange(text: String) { _groupInput.value = text }

    fun onP2pInputChange(text: String) { _p2pInput.value = text }

    // ── Send group message (Issue #3 — step 4) ────────────────────────────────

    fun sendGroupMessage() {
        val text = _groupInput.value.trim()
        if (text.isBlank()) return

        val myId    = _myDeviceId.value
        val myName  = _myName.value
        val myPhone = _myPhone.value
        if (myId.isEmpty()) return   // identity not yet loaded — guard

        val msgId     = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        viewModelScope.launch {
            // 4a. Build the packet
            //     receiverId is encoded in the payload prefix "GROUP:" so all
            //     peers can filter it; MeshPacket itself has no receiverId field.
            val packet = MeshPacket(
                id          = msgId,
                type        = PacketType.TEXT_MESSAGE,
                senderId    = myId,
                senderName  = myName,
                senderPhone = myPhone,
                payload     = "GROUP:$text",
                ttl         = 7,           // standard mesh TTL hop budget
                timestamp   = timestamp,
                signature   = ""           // filled in by ConnectionManager.sendPacket()
            )

            // 4b+4c. Sign and broadcast over Wi-Fi Direct
            //        ConnectionManager.broadcastPacket() calls sign() internally
            connectionManager.broadcastPacket(packet)

            // 4d. Persist to Room so the UI Flow picks it up immediately
            messageRepository.save(
                MessageEntity(
                    id         = msgId,
                    senderId   = myId,
                    senderName = myName,
                    receiverId = GROUP_RECEIVER_ID,
                    content    = text,
                    type       = MSG_TYPE_TEXT,
                    timestamp  = timestamp,
                    hopCount   = 0,
                    isRead     = true    // own outgoing message is always read
                )
            )

            _groupInput.value = ""
        }
    }

    // ── Send P2P message (Issue #3 — step 5) ─────────────────────────────────

    fun sendP2pMessage() {
        val text = _p2pInput.value.trim()
        val peer = _selectedPeer.value
        if (text.isBlank() || peer == null) return

        val myId    = _myDeviceId.value
        val myName  = _myName.value
        val myPhone = _myPhone.value
        if (myId.isEmpty()) return

        val msgId     = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        viewModelScope.launch {
            // 5a. Build the packet
            //     receiverId is encoded in the payload prefix "P2P:<peerId>:" so
            //     SocketServer/router can filter on arrival; MeshPacket has no field for it.
            val packet = MeshPacket(
                id          = msgId,
                type        = PacketType.TEXT_MESSAGE,
                senderId    = myId,
                senderName  = myName,
                senderPhone = myPhone,
                payload     = "P2P:${peer.deviceId}:$text",
                ttl         = 7,
                timestamp   = timestamp,
                signature   = ""
            )

            // 5b. Send to the specific peer — ConnectionManager looks up the IP
            //     from WifiDirectManager's peerList by deviceId, or falls back
            //     to broadcastPacket if a direct IP is not resolvable.
            val peerIp = connectionManager.getPeerIp(peer.deviceId)
            if (peerIp != null) {
                connectionManager.sendPacket(packet, peerIp)
            } else {
                // Fallback: route via group owner (best-effort in mesh topology)
                connectionManager.broadcastPacket(packet)
            }

            // 5c. Persist to Room
            messageRepository.save(
                MessageEntity(
                    id         = msgId,
                    senderId   = myId,
                    senderName = myName,
                    receiverId = peer.deviceId,
                    content    = text,
                    type       = MSG_TYPE_TEXT,
                    timestamp  = timestamp,
                    hopCount   = 0,
                    isRead     = true
                )
            )

            _p2pInput.value = ""
        }
    }

    // ── Peer selection ────────────────────────────────────────────────────────

    fun selectPeer(peer: ChatPeer) {
        _selectedPeer.value = peer
        // Clear unread badge for this peer
        _unreadCountMap.value = _unreadCountMap.value.toMutableMap().apply {
            remove(peer.deviceId)
        }
    }

    fun clearSelectedPeer() {
        _selectedPeer.value = null
    }

    fun clearGroupUnread() {
        _unreadGroup.value = 0
    }

    // ── Incoming message handler (Issue #3 — step 6) ──────────────────────────

    /**
     * Called for every [MeshPacket] emitted on [AppEventBus.incomingMessage].
     *
     * a. Persists the message to Room (the relevant StateFlow updates automatically).
     * b. Increments the unread counter for the sender peer, unless the P2P
     *    conversation with that sender is currently open.
     */
    private suspend fun handleIncomingPacket(packet: MeshPacket) {
        // MeshPacket has no receiverId field — the routing target is encoded in
        // the payload as "GROUP:<text>" or "P2P:<deviceId>:<text>" by the sender.
        val isGroup = packet.payload.startsWith("GROUP:")

        // Strip the routing prefix to get the bare message text for storage
        val bareContent = when {
            packet.payload.startsWith("GROUP:") ->
                packet.payload.removePrefix("GROUP:")
            packet.payload.startsWith("P2P:") -> {
                // Format: "P2P:<receiverId>:<text>"
                val withoutPrefix = packet.payload.removePrefix("P2P:")
                val colonIndex = withoutPrefix.indexOf(':')
                if (colonIndex >= 0) withoutPrefix.substring(colonIndex + 1) else withoutPrefix
            }
            else -> packet.payload
        }

        // Derive the receiverId for Room storage from the payload prefix
        val receiverId = when {
            isGroup -> GROUP_RECEIVER_ID
            packet.payload.startsWith("P2P:") -> {
                val withoutPrefix = packet.payload.removePrefix("P2P:")
                val colonIndex = withoutPrefix.indexOf(':')
                if (colonIndex >= 0) withoutPrefix.substring(0, colonIndex) else _myDeviceId.value
            }
            else -> _myDeviceId.value
        }

        // 6a. Persist to Room — hopCount derived from TTL budget (7 - remaining ttl)
        messageRepository.save(
            MessageEntity(
                id         = packet.id,
                senderId   = packet.senderId,
                senderName = packet.senderName,
                receiverId = receiverId,
                content    = bareContent,
                type       = MSG_TYPE_TEXT,
                timestamp  = packet.timestamp,
                hopCount   = (7 - packet.ttl).coerceAtLeast(0),
                isRead     = false
            )
        )

        // 6b. Update unread counters
        if (isGroup) {
            _unreadGroup.value += 1
        } else {
            val openPeerId = _selectedPeer.value?.deviceId
            if (packet.senderId != openPeerId) {
                _unreadCountMap.value = _unreadCountMap.value.toMutableMap().apply {
                    put(packet.senderId, (getOrDefault(packet.senderId, 0)) + 1)
                }
            }
        }
    }
}