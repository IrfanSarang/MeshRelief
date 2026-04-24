package com.meshrelief.core.event

import com.meshrelief.mesh.protocol.MeshPacket
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight in-memory event bus for app-wide one-shot events.
 * Injected as a Hilt @Singleton — never accessed statically.
 */
@Singleton
class AppEventBus @Inject constructor() {

    // ── Existing ──────────────────────────────────────────────────────────
    val incomingSos = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)

    // ── Added by Issue #10 ────────────────────────────────────────────────
    val incomingMessage = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 32)
    val campUpdate      = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)
    val bulletin        = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)

    // ── Added by Issue #14 ────────────────────────────────────────────────
    val headcountResponse = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)

    // ── Added by Bug #10 ─────────────────────────────────────────────────
    val headcountPing     = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 4)

    // ── Added by Bug #7 ──────────────────────────────────────────────────
    val evacuationRoute = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)

    // ── Added by Bug #9 ──────────────────────────────────────────────────
    val deviceStatus = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 32)

    val peerHandshake = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 16)

    // ── Added by MISSING 3 ───────────────────────────────────────────────
    val ack = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)

    // ── Added by MISSING 4 ───────────────────────────────────────────────
    // Emitted by MeshRouter when PEER_VERIFY / PEER_FLAG packets arrive.
    // Collected by MeshForegroundService to persist verified/flagged state.
    val peerVerify = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)
    val peerFlag   = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)

    val sosCancel  = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)

    val peerHopCount = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 32)
}

/** Minimal SOS payload. Expand fields when WiFi Direct lands (Issue #9). */
data class SosPacket(
    val senderName: String,
    val message   : String,
    val latitude  : Double? = null,
    val longitude : Double? = null
)