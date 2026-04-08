package com.meshrelief.mesh.routing

import android.util.Log
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshRouter @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val appEventBus: AppEventBus,
    private val deviceIdentity: DeviceIdentity          // Issue #15
) {

    private val TAG = "MeshRouter"

    // Bounded cache — evicts oldest entry once limit is reached
    private val seenPacketIds = ArrayDeque<String>(SEEN_PACKET_CACHE_SIZE)

    /**
     * @param knownPublicKeys  Map of senderId → X.509-encoded public key bytes.
     *                         Pass [emptyMap] if no peer keys are available yet.
     *                         Callers should populate this from PeerRepository
     *                         once a trust system is in place (Issue #15 follow-up).
     */
    suspend fun route(
        packet: MeshPacket,
        connectedPeerAddresses: List<String>,
        knownPublicKeys: Map<String, ByteArray> = emptyMap()
    ) {

        // a. Duplicate check — drop silently if already seen
        if (seenPacketIds.contains(packet.id)) {
            Log.v(TAG, "Dropping duplicate packet id=${packet.id}")
            return
        }

        // b. Mark as seen; evict oldest if over capacity
        seenPacketIds.addLast(packet.id)
        if (seenPacketIds.size > SEEN_PACKET_CACHE_SIZE) {
            seenPacketIds.removeFirst()
        }

        // c. Issue #15 — verify signature against sender's known public key
        //    Skip verification if the peer is unknown (trust system TBD).
        //    Log a warning on failure but still route — full rejection comes
        //    once the trust system is in place.
        val senderPublicKey: ByteArray? = knownPublicKeys[packet.senderId]
        if (senderPublicKey != null) {
            val signatureData = packet.id + packet.senderId + packet.payload
            val valid = deviceIdentity.verify(signatureData, packet.signature, senderPublicKey)
            if (!valid) {
                Log.w(
                    TAG,
                    "Signature verification FAILED for packet id=${packet.id} " +
                            "from sender=${packet.senderId} — routing anyway (Issue #15)"
                )
            } else {
                Log.d(TAG, "Signature verified OK for packet id=${packet.id}")
            }
        } else {
            Log.d(
                TAG,
                "Unknown sender=${packet.senderId} — skipping signature check for id=${packet.id}"
            )
        }

        // d. Dispatch to the correct local handler
        when (packet.type) {
            PacketType.SOS_ALERT -> {
                Log.d(TAG, "Dispatching SOS_ALERT id=${packet.id}")
                appEventBus.incomingSos.emit(packet)
            }
            PacketType.TEXT_MESSAGE -> {
                Log.d(TAG, "Dispatching TEXT_MESSAGE id=${packet.id}")
                appEventBus.incomingMessage.emit(packet)
            }
            PacketType.CAMP_UPDATE -> {
                Log.d(TAG, "Dispatching CAMP_UPDATE id=${packet.id}")
                appEventBus.campUpdate.emit(packet)
            }
            PacketType.BULLETIN -> {
                Log.d(TAG, "Dispatching BULLETIN id=${packet.id}")
                appEventBus.bulletin.emit(packet)
            }
            else -> {
                Log.i(TAG, "Unhandled packet type=${packet.type} id=${packet.id} — ignoring")
            }
        }

        // e. Re-broadcast with TTL decremented, only if TTL allows another hop
        if (packet.ttl > 1) {
            val forwarded = packet.copy(ttl = packet.ttl - 1)
            connectedPeerAddresses.forEach { addr ->
                connectionManager.sendPacket(forwarded, addr)
            }
            Log.d(
                TAG,
                "Re-broadcast id=${packet.id} ttl=${forwarded.ttl} to ${connectedPeerAddresses.size} peer(s)"
            )
        } else {
            Log.v(TAG, "TTL exhausted for packet id=${packet.id} — not forwarding")
        }
    }

    companion object {
        private const val SEEN_PACKET_CACHE_SIZE = 500
    }
}