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
}

/** Minimal SOS payload. Expand fields when WiFi Direct lands (Issue #9). */
data class SosPacket(
    val senderName: String,
    val message: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)