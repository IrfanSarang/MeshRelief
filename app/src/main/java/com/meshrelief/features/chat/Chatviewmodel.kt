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
    private val deviceIdentity: DeviceIdentity,
    private val appEventBus: AppEventBus
) : ViewModel() {

    // ── Local identity ────────────────────────────────────────────────────────

    private val _myDeviceId = MutableStateFlow("")
    private val _myName     = MutableStateFlow("")
    private val _myPhone    = MutableStateFlow("")

    private val myIdSuffix get() = _myDeviceId.value.takeLast(4)

    // ── Group chat state ──────────────────────────────────────────────────────

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

    private val _p2pSendError = MutableStateFlow<String?>(null)
    val p2pSendError: StateFlow<String?> = _p2pSendError.asStateFlow()

    // ── Unread badges ─────────────────────────────────────────────────────────

    private val _unreadGroup = MutableStateFlow(0)
    val unreadGroup: StateFlow<Int> = _unreadGroup.asStateFlow()

    val unreadP2p: StateFlow<Int> = _unreadCountMap
        .map { map -> map.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            userPreferences.userDeviceId.collect { id -> _myDeviceId.value = id }
        }
        viewModelScope.launch {
            userPreferences.userName.collect { name -> _myName.value = name }
        }
        viewModelScope.launch {
            userPreferences.userPhone.collect { phone -> _myPhone.value = phone }
        }

        viewModelScope.launch {
            appEventBus.incomingMessage.collect { packet ->
                handleIncomingPacket(packet)
            }
        }

        viewModelScope.launch {
            appEventBus.ack.collect { ackPacket ->
                val originalMessageId = ackPacket.payload
                try {
                    messageRepository.markDelivered(originalMessageId)
                } catch (e: Exception) {
                    android.util.Log.w(
                        "ChatViewModel",
                        "Failed to mark message $originalMessageId as delivered: ${e.message}"
                    )
                }
            }
        }
    }

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onGroupInputChange(text: String) { _groupInput.value = text }

    fun onP2pInputChange(text: String)   { _p2pInput.value  = text }

    // ── Send group message ────────────────────────────────────────────────────

    fun sendGroupMessage() {
        val text = _groupInput.value.trim()
        if (text.isBlank()) return

        val myId    = _myDeviceId.value
        val myName  = _myName.value
        val myPhone = _myPhone.value
        if (myId.isEmpty()) return

        val msgId     = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        viewModelScope.launch {
            val packet = MeshPacket(
                id          = msgId,
                type        = PacketType.TEXT_MESSAGE,
                senderId    = myId,
                senderName  = myName,
                senderPhone = myPhone,
                payload     = "GROUP:$text",
                ttl         = 7,
                timestamp   = timestamp,
                signature   = "",
                receiverId  = ""          // broadcast — no specific receiver
            )

            connectionManager.broadcastPacket(packet)

            messageRepository.save(
                MessageEntity(
                    id          = msgId,
                    senderId    = myId,
                    senderName  = myName,
                    receiverId  = GROUP_RECEIVER_ID,
                    content     = text,
                    type        = MSG_TYPE_TEXT,
                    timestamp   = timestamp,
                    hopCount    = 0,
                    isRead      = true,
                    isDelivered = false
                )
            )

            _groupInput.value = ""
        }
    }

    // ── Send P2P message ──────────────────────────────────────────────────────

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
            val packet = MeshPacket(
                id          = msgId,
                type        = PacketType.TEXT_MESSAGE,
                senderId    = myId,
                senderName  = myName,
                senderPhone = myPhone,
                payload     = "P2P:${peer.deviceId}:$text",
                ttl         = 7,
                timestamp   = timestamp,
                signature   = "",
                receiverId  = peer.deviceId   // ← Rec 7: true unicast routing
            )

            val peerIp = connectionManager.getPeerIp(peer.deviceId)
            if (peerIp != null) {
                connectionManager.sendPacket(packet, peerIp)
            } else {
                _p2pSendError.value = "Peer ${peer.name} is not reachable"
                return@launch
            }

            messageRepository.save(
                MessageEntity(
                    id          = msgId,
                    senderId    = myId,
                    senderName  = myName,
                    receiverId  = peer.deviceId,
                    content     = text,
                    type        = MSG_TYPE_TEXT,
                    timestamp   = timestamp,
                    hopCount    = 0,
                    isRead      = true,
                    isDelivered = false
                )
            )

            _p2pInput.value    = ""
            _p2pSendError.value = null
        }
    }

    // ── Peer selection ────────────────────────────────────────────────────────

    fun selectPeer(peer: ChatPeer) {
        _selectedPeer.value = peer
        _unreadCountMap.value = _unreadCountMap.value.toMutableMap().apply {
            remove(peer.deviceId)
        }
    }

    fun clearSelectedPeer()  { _selectedPeer.value   = null }
    fun clearGroupUnread()   { _unreadGroup.value     = 0    }
    fun clearP2pSendError()  { _p2pSendError.value    = null }

    // ── Incoming message handler ──────────────────────────────────────────────

    private suspend fun handleIncomingPacket(packet: MeshPacket) {
        val isGroup = packet.payload.startsWith("GROUP:")

        val bareContent = when {
            packet.payload.startsWith("GROUP:") ->
                packet.payload.removePrefix("GROUP:")
            packet.payload.startsWith("P2P:") -> {
                val withoutPrefix = packet.payload.removePrefix("P2P:")
                val colonIndex    = withoutPrefix.indexOf(':')
                if (colonIndex >= 0) withoutPrefix.substring(colonIndex + 1) else withoutPrefix
            }
            else -> packet.payload
        }

        val receiverId = when {
            isGroup -> GROUP_RECEIVER_ID
            packet.payload.startsWith("P2P:") -> {
                val withoutPrefix = packet.payload.removePrefix("P2P:")
                val colonIndex    = withoutPrefix.indexOf(':')
                if (colonIndex >= 0) withoutPrefix.substring(0, colonIndex) else _myDeviceId.value
            }
            else -> _myDeviceId.value
        }

        messageRepository.save(
            MessageEntity(
                id          = packet.id,
                senderId    = packet.senderId,
                senderName  = packet.senderName,
                receiverId  = receiverId,
                content     = bareContent,
                type        = MSG_TYPE_TEXT,
                timestamp   = packet.timestamp,
                hopCount    = (7 - packet.ttl).coerceAtLeast(0),
                isRead      = false,
                isDelivered = false
            )
        )

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