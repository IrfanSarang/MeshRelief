package com.meshrelief.mesh.routing

import android.util.Log
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import com.meshrelief.core.util.Constants
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class MeshRouter @Inject constructor(
    private val connectionManager : ConnectionManager,
    private val appEventBus       : AppEventBus,
    private val deviceIdentity    : DeviceIdentity,
    private val userPreferences   : UserPreferences,
    private val aodvRouter        : AodvRouter
) {

    private val TAG = "MeshRouter"

    // ── Rec 4: O(1) LRU cache — replaces ArrayDeque ───────────────────────
    // accessOrder=true → LRU eviction is automatic, no manual removeFirst().
    // containsKey() is O(1) vs the old contains() which was O(N).
    private val seenPacketIds: LinkedHashMap<String, Unit> =
        object : LinkedHashMap<String, Unit>(SEEN_PACKET_CACHE_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Unit>): Boolean =
                size > SEEN_PACKET_CACHE_SIZE
        }

    suspend fun route(
        packet                : MeshPacket,
        fromIp                : String,
        connectedPeerAddresses: List<String>,
        knownPublicKeys       : Map<String, ByteArray> = emptyMap()
    ) {

        // a. Duplicate check — O(1) containsKey vs old O(N) contains()
        if (seenPacketIds.containsKey(packet.id)) {
            Log.v(TAG, "Dropping duplicate packet id=${packet.id}")
            return
        }

        // b. Mark as seen — eviction handled automatically by LinkedHashMap
        seenPacketIds[packet.id] = Unit

        val myDeviceId = userPreferences.userDeviceId.first()

        // c. Signature verification
        val senderPublicKey: ByteArray? = knownPublicKeys[packet.senderId]
        if (senderPublicKey != null) {
            val signatureData = packet.id + packet.senderId + packet.payload
            val valid = deviceIdentity.verify(signatureData, packet.signature, senderPublicKey)
            if (!valid) {
                Log.w(TAG, "Signature FAILED id=${packet.id}")
            } else {
                Log.d(TAG, "Signature OK id=${packet.id}")
            }
        }

        // hop count
        val hopCount = (Constants.DEFAULT_TTL - packet.ttl).coerceAtLeast(0)

        // ───────────── AODV UNICAST FORWARD ─────────────
        if (
            packet.receiverId.isNotEmpty() &&
            packet.receiverId != myDeviceId &&
            packet.type !in listOf(
                PacketType.ROUTE_REQUEST,
                PacketType.ROUTE_REPLY,
                PacketType.ROUTE_ERROR
            )
        ) {
            Log.d(TAG, "Forwarding unicast id=${packet.id} to ${packet.receiverId}")
            aodvRouter.forwardUnicast(
                packet,
                myDeviceId,
                packet.sequenceNumber,
                connectedPeerAddresses
            )
            return
        }

        // d. Dispatch
        when (packet.type) {

            // ───────────── AODV CONTROL ─────────────
            PacketType.ROUTE_REQUEST,
            PacketType.ROUTE_REPLY,
            PacketType.ROUTE_ERROR -> {
                Log.d(TAG, "Handling AODV control type=${packet.type} id=${packet.id}")
                aodvRouter.handleControl(
                    packet,
                    fromIp,
                    myDeviceId,
                    packet.sequenceNumber,
                    connectedPeerAddresses
                )
                return
            }

            PacketType.SOS_ALERT -> {
                appEventBus.incomingSos.emit(packet)
                sendAck(packet, fromIp)
            }

            PacketType.SOS_CANCEL -> {
                appEventBus.sosCancel.emit(packet)
                sendAck(packet, fromIp)
            }

            PacketType.TEXT_MESSAGE -> {
                val p2pTarget = if (packet.payload.startsWith("P2P:")) {
                    packet.payload.removePrefix("P2P:").substringBefore(":")
                } else null

                val isForMe = packet.payload.startsWith("GROUP:") ||
                        p2pTarget == null ||
                        p2pTarget == myDeviceId

                if (isForMe) {
                    appEventBus.incomingMessage.emit(packet)
                }
                sendAck(packet, fromIp)
            }

            PacketType.CAMP_UPDATE -> {
                appEventBus.campUpdate.emit(packet)
            }

            PacketType.BULLETIN -> {
                appEventBus.bulletin.emit(packet)
            }

            PacketType.EVACUATION_ROUTE -> {
                appEventBus.evacuationRoute.emit(packet)
            }

            PacketType.DEVICE_STATUS -> {
                appEventBus.deviceStatus.emit(packet)
                appEventBus.peerHopCount.emit(packet.senderId to hopCount)
            }

            PacketType.HEADCOUNT_PING -> {
                appEventBus.headcountPing.emit(packet)
            }

            PacketType.HEADCOUNT_RESPONSE -> {
                appEventBus.headcountResponse.tryEmit(packet)
            }

            PacketType.PEER_HANDSHAKE -> {
                appEventBus.peerHandshake.emit(packet)
                appEventBus.peerHopCount.emit(packet.senderId to hopCount)
                return
            }

            PacketType.ACK -> {
                appEventBus.ack.emit(packet)
                return
            }

            PacketType.PEER_VERIFY -> {
                appEventBus.peerVerify.emit(packet)
            }

            PacketType.PEER_FLAG -> {
                appEventBus.peerFlag.emit(packet)
            }

            else -> {
                Log.i(TAG, "Unhandled type=${packet.type}")
            }
        }

        // e. Forward — Rec 3 spray-and-wait for SOS, standard TTL flood for all else
        forwardPacket(packet, connectedPeerAddresses)
    }

    // ── Rec 3: Spray-and-Wait forward decision ─────────────────────────────
    /**
     * SOS_ALERT packets use binary-halving spray-and-wait:
     *   - sprayCount > 1  → spray phase: forward with sprayCount halved
     *   - sprayCount == 1 → wait phase:  hold, do not re-broadcast
     *   - sprayCount == 0 → legacy packet (no spray field set): fall through
     *     to standard TTL flood so older mesh nodes stay compatible
     *
     * All other packet types use the original TTL-decrement broadcast.
     */
    private suspend fun forwardPacket(
        packet                : MeshPacket,
        connectedPeerAddresses: List<String>
    ) {
        if (packet.ttl <= 1) {
            Log.v(TAG, "TTL exhausted id=${packet.id}")
            return
        }

        val forwarded: MeshPacket? = when {
            packet.type == PacketType.SOS_ALERT && packet.sprayCount > 1 -> {
                // Spray phase: halve the spray budget
                packet.copy(
                    ttl        = packet.ttl - 1,
                    sprayCount = packet.sprayCount / 2
                )
            }
            packet.type == PacketType.SOS_ALERT && packet.sprayCount == 1 -> {
                // Wait phase: this node holds the packet, no further broadcast
                Log.d(TAG, "SOS wait-phase, holding id=${packet.id}")
                null
            }
            else -> {
                // Standard TTL-decrement flood (non-SOS or legacy sprayCount=0)
                packet.copy(ttl = packet.ttl - 1)
            }
        }

        forwarded?.let { pkt ->
            connectedPeerAddresses.forEach { addr ->
                connectionManager.sendPacket(pkt, addr)
            }
            Log.d(TAG, "Forwarded id=${pkt.id} ttl=${pkt.ttl} spray=${pkt.sprayCount}")
        }
    }

    // ── ACK ────────────────────────────────────────────────────────────────

    private suspend fun sendAck(original: MeshPacket, fromIp: String) {
        try {
            val myDeviceId = userPreferences.userDeviceId.first()

            val ack = MeshPacket(
                id             = UUID.randomUUID().toString(),
                type           = PacketType.ACK,
                senderId       = myDeviceId,
                senderName     = "",
                senderPhone    = "",
                payload        = original.id,
                ttl            = 1,
                timestamp      = System.currentTimeMillis(),
                signature      = "",
                receiverId     = original.senderId,
                sequenceNumber = 0
            )

            connectionManager.sendPacket(ack, fromIp)

        } catch (e: Exception) {
            Log.w(TAG, "ACK failed: ${e.message}")
        }
    }

    companion object {
        // 512 covers ~8 min of dense traffic at 1 pkt/sec across 64 nodes.
        // Raised from 500 to a clean power-of-two-friendly value.
        private const val SEEN_PACKET_CACHE_SIZE = 512
    }
}