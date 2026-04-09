package com.meshrelief.core.event

import com.meshrelief.mesh.protocol.MeshPacket
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Lightweight in-memory event bus for app-wide one-shot events.
 * No Android lifecycle dependency — safe as a plain object.
 */
object AppEventBus {

    // ── Existing ──────────────────────────────────────────────────────────
    val incomingSos = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)

    // ── Added by Issue #10 ────────────────────────────────────────────────
    val incomingMessage = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 32)
    val campUpdate      = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)
    val bulletin        = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)

    // ── Added by Issue #14 ────────────────────────────────────────────────
    /**
     * Peers emit a [MeshPacket] of type HEADCOUNT_RESPONSE here when they
     * receive a HEADCOUNT_PING broadcast from the admin.
     * The packet receiver (SocketServer / PacketRouter) must call
     *   AppEventBus.headcountResponse.tryEmit(packet)
     * for every incoming HEADCOUNT_RESPONSE packet.
     *
     * Buffer capacity matches HEADCOUNT_RESPONSE_TIMEOUT_S window: a mesh
     * of up to 64 peers responding near-simultaneously won't drop events.
     */
    val headcountResponse = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
}

/** Minimal SOS payload. Expand fields when WiFi Direct lands (Issue #9). */
data class SosPacket(
    val senderName: String,
    val message: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)