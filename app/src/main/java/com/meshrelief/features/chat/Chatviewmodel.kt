package com.meshrelief.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    // ── Group chat state ──────────────────────────────────────────────────────

    private val _groupMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val groupMessages: StateFlow<List<ChatMessage>> = _groupMessages.asStateFlow()

    private val _groupInput = MutableStateFlow("")
    val groupInput: StateFlow<String> = _groupInput.asStateFlow()

    // ── P2P state ─────────────────────────────────────────────────────────────

    private val _peers = MutableStateFlow<List<ChatPeer>>(emptyList())
    val peers: StateFlow<List<ChatPeer>> = _peers.asStateFlow()

    private val _selectedPeer = MutableStateFlow<ChatPeer?>(null)
    val selectedPeer: StateFlow<ChatPeer?> = _selectedPeer.asStateFlow()

    private val _p2pMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val p2pMessages: StateFlow<List<ChatMessage>> = _p2pMessages.asStateFlow()

    private val _p2pInput = MutableStateFlow("")
    val p2pInput: StateFlow<String> = _p2pInput.asStateFlow()

    // ── Unread badge ──────────────────────────────────────────────────────────

    private val _unreadGroup = MutableStateFlow(0)
    val unreadGroup: StateFlow<Int> = _unreadGroup.asStateFlow()

    private val _unreadP2p = MutableStateFlow(0)
    val unreadP2p: StateFlow<Int> = _unreadP2p.asStateFlow()

    init {
        loadSampleData()
    }

    // ── Input handlers ────────────────────────────────────────────────────────

    fun onGroupInputChange(text: String) {
        _groupInput.value = text
    }

    fun onP2pInputChange(text: String) {
        _p2pInput.value = text
    }

    fun sendGroupMessage() {
        val text = _groupInput.value.trim()
        if (text.isBlank()) return
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "Me",
            senderIdSuffix = "4210",
            text = text,
            timestampMs = System.currentTimeMillis(),
            hopCount = 0,
            isOutgoing = true
        )
        _groupMessages.value = _groupMessages.value + msg
        _groupInput.value = ""
    }

    fun sendP2pMessage() {
        val text = _p2pInput.value.trim()
        if (text.isBlank() || _selectedPeer.value == null) return
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "Me",
            senderIdSuffix = "4210",
            text = text,
            timestampMs = System.currentTimeMillis(),
            hopCount = 0,
            isOutgoing = true
        )
        _p2pMessages.value = _p2pMessages.value + msg
        _p2pInput.value = ""
    }

    fun selectPeer(peer: ChatPeer) {
        _selectedPeer.value = peer
        // Clear unread for this peer
        _peers.value = _peers.value.map {
            if (it.deviceId == peer.deviceId) it.copy(unreadCount = 0) else it
        }
        loadP2pSampleMessages(peer)
    }

    fun clearSelectedPeer() {
        _selectedPeer.value = null
        _p2pMessages.value = emptyList()
    }

    fun clearGroupUnread() {
        _unreadGroup.value = 0
    }

    // ── Sample / placeholder data (replaced when WiFi Direct is wired in) ─────

    private fun loadSampleData() {
        val now = System.currentTimeMillis()

        _groupMessages.value = listOf(
            ChatMessage(
                id = "g1",
                senderName = "Ravi K.",
                senderIdSuffix = "4401",
                text = "Everyone stay near the school gate. Admin is organising headcount.",
                timestampMs = now - 18 * 60_000,
                hopCount = 1,
                isOutgoing = false
            ),
            ChatMessage(
                id = "g2",
                senderName = "Priya M.",
                senderIdSuffix = "2210",
                text = "Water supply arrived at Camp B. Come before it runs out.",
                timestampMs = now - 12 * 60_000,
                hopCount = 2,
                isOutgoing = false
            ),
            ChatMessage(
                id = "sys1",
                senderName = "",
                senderIdSuffix = "",
                text = "Unknown (••••7821) joined the mesh",
                timestampMs = now - 8 * 60_000,
                hopCount = 0,
                isOutgoing = false,
                isSystemMessage = true
            ),
            ChatMessage(
                id = "g3",
                senderName = "Me",
                senderIdSuffix = "4210",
                text = "Acknowledged. Heading to Camp B now.",
                timestampMs = now - 5 * 60_000,
                hopCount = 0,
                isOutgoing = true
            ),
            ChatMessage(
                id = "g4",
                senderName = "Ravi K.",
                senderIdSuffix = "4401",
                text = "Stay safe. Signal is weak past the bridge.",
                timestampMs = now - 2 * 60_000,
                hopCount = 1,
                isOutgoing = false
            )
        )

        _peers.value = listOf(
            ChatPeer(
                deviceId = "dev_ravi",
                name = "Ravi K.",
                idSuffix = "4401",
                triageColor = "green",
                hopCount = 1,
                unreadCount = 2
            ),
            ChatPeer(
                deviceId = "dev_priya",
                name = "Priya M.",
                idSuffix = "2210",
                triageColor = "yellow",
                hopCount = 2,
                unreadCount = 0
            ),
            ChatPeer(
                deviceId = "dev_unknown",
                name = "Unknown",
                idSuffix = "7821",
                triageColor = "red",
                hopCount = 3,
                unreadCount = 1
            )
        )

        _unreadGroup.value = 1
        _unreadP2p.value = 3
    }

    private fun loadP2pSampleMessages(peer: ChatPeer) {
        val now = System.currentTimeMillis()
        _p2pMessages.value = listOf(
            ChatMessage(
                id = "p1",
                senderName = peer.name,
                senderIdSuffix = peer.idSuffix,
                text = "Hey, are you near the main road?",
                timestampMs = now - 25 * 60_000,
                hopCount = peer.hopCount,
                isOutgoing = false
            ),
            ChatMessage(
                id = "p2",
                senderName = "Me",
                senderIdSuffix = "4210",
                text = "Yes, about 200m from the crossroads.",
                timestampMs = now - 22 * 60_000,
                hopCount = 0,
                isOutgoing = true
            ),
            ChatMessage(
                id = "p3",
                senderName = peer.name,
                senderIdSuffix = peer.idSuffix,
                text = "Can you relay a bulletin to the camp? My signal doesn't reach.",
                timestampMs = now - 10 * 60_000,
                hopCount = peer.hopCount,
                isOutgoing = false
            )
        )
    }
}