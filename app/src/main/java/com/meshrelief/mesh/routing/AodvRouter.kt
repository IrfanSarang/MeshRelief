package com.meshrelief.mesh.routing

import android.util.Log
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.wifi.ConnectionManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AODV routing per RFC 3561.
 *
 * Routing table entry: destId → (nextHopIp, hopCount, sequenceNumber, expiryMs)
 * RREQ payload format : "RREQ:<rreqId>:<srcId>:<destId>:<srcSeq>:<destSeq>:<hopCount>"
 * RREP payload format : "RREP:<rreqId>:<srcId>:<destId>:<destSeq>:<hopCount>"
 * RERR payload format : "RERR:<unreachableDestId>"
 */
@Singleton
class AodvRouter @Inject constructor(
    private val connectionManager: ConnectionManager
) {
    private val TAG = "AodvRouter"

    // destId → RouteEntry
    data class RouteEntry(
        val nextHopIp: String,
        val hopCount: Int,
        val sequenceNumber: Int,
        val expiryMs: Long
    )

    private val routingTable = ConcurrentHashMap<String, RouteEntry>()

    // rreqId → true  (seen-cache to suppress duplicates)
    private val seenRreqs = ConcurrentHashMap<String, Boolean>()

    // destId → list of buffered packets waiting for a route
    private val pendingPackets = ConcurrentHashMap<String, MutableList<Pair<MeshPacket, List<String>>>>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Try to forward a unicast packet. If a route exists, send directly to
     * the next hop; otherwise buffer the packet and flood an RREQ.
     */
    suspend fun forwardUnicast(
        packet: MeshPacket,
        myId: String,
        mySeq: Int,
        allPeerIps: List<String>
    ) {
        val dest = packet.receiverId
        val route = validRoute(dest)
        if (route != null) {
            connectionManager.sendPacket(packet, route.nextHopIp)
            Log.d(TAG, "Unicast id=${packet.id} → nextHop=${route.nextHopIp} (${route.hopCount} hops)")
        } else {
            Log.d(TAG, "No route to $dest — buffering id=${packet.id} and flooding RREQ")
            pendingPackets.getOrPut(dest) { mutableListOf() }.add(packet to allPeerIps)
            floodRreq(myId, dest, mySeq, 0, allPeerIps)
        }
    }

    /**
     * Handle an incoming AODV control packet (ROUTE_REQUEST / ROUTE_REPLY / ROUTE_ERROR).
     * Returns true if the packet was consumed here (no further broadcast needed).
     */
    suspend fun handleControl(
        packet: MeshPacket,
        fromIp: String,
        myId: String,
        mySeq: Int,
        allPeerIps: List<String>
    ): Boolean {
        return when (packet.type) {
            PacketType.ROUTE_REQUEST -> { handleRreq(packet, fromIp, myId, mySeq, allPeerIps); true }
            PacketType.ROUTE_REPLY   -> { handleRrep(packet, fromIp, myId, allPeerIps);        true }
            PacketType.ROUTE_ERROR   -> { handleRerr(packet, myId, allPeerIps);                true }
            else                     -> false
        }
    }

    /** Call when a link to a peer breaks (detected by ConnectionManager). */
    suspend fun handleLinkFailure(brokenNextHopIp: String, myId: String, allPeerIps: List<String>) {
        val affected = routingTable
            .filter { it.value.nextHopIp == brokenNextHopIp }
            .keys
        affected.forEach { dest ->
            routingTable.remove(dest)
            Log.w(TAG, "Route to $dest invalidated — next hop $brokenNextHopIp unreachable")
            sendRerr(dest, myId, allPeerIps)
        }
    }

    // ── RREQ ──────────────────────────────────────────────────────────────────

    private suspend fun floodRreq(
        srcId: String,
        destId: String,
        srcSeq: Int,
        destSeq: Int,
        peers: List<String>
    ) {
        val rreqId = UUID.randomUUID().toString().take(8)
        val payload = "RREQ:$rreqId:$srcId:$destId:$srcSeq:$destSeq:0"
        val rreq = MeshPacket(
            id             = UUID.randomUUID().toString(),
            type           = PacketType.ROUTE_REQUEST,
            senderId       = srcId,
            senderName     = "",
            senderPhone    = "",
            payload        = payload,
            ttl            = 6,
            timestamp      = System.currentTimeMillis(),
            signature      = "",
            receiverId     = "",   // broadcast
            sequenceNumber = srcSeq
        )
        seenRreqs[rreqId] = true
        peers.forEach { connectionManager.sendPacket(rreq, it) }
        Log.d(TAG, "RREQ flooded rreqId=$rreqId src=$srcId dest=$destId")
    }

    private suspend fun handleRreq(
        packet: MeshPacket,
        fromIp: String,
        myId: String,
        mySeq: Int,
        allPeerIps: List<String>
    ) {
        // RREQ:<rreqId>:<srcId>:<destId>:<srcSeq>:<destSeq>:<hopCount>
        val parts = packet.payload.split(":")
        if (parts.size < 7) return
        val rreqId   = parts[1]
        val srcId    = parts[2]
        val destId   = parts[3]
        val srcSeq   = parts[4].toIntOrNull() ?: 0
        val hopCount = parts[6].toIntOrNull() ?: 0

        if (seenRreqs.containsKey(rreqId)) {
            Log.v(TAG, "Dropping duplicate RREQ rreqId=$rreqId")
            return
        }
        seenRreqs[rreqId] = true

        // Reverse route back to source
        updateRoute(srcId, fromIp, hopCount + 1, srcSeq)

        if (destId == myId) {
            // I am the destination — unicast RREP back toward source
            sendRrep(rreqId, srcId, destId, mySeq, hopCount + 1, fromIp)
        } else {
            val existingRoute = validRoute(destId)
            if (existingRoute != null) {
                // Intermediate node with fresh route — send RREP on behalf of dest
                sendRrep(rreqId, srcId, destId, existingRoute.sequenceNumber,
                    existingRoute.hopCount, fromIp)
            } else {
                // Re-flood with incremented hop count (if TTL allows)
                if (packet.ttl > 1) {
                    val forwarded = packet.copy(
                        ttl     = packet.ttl - 1,
                        payload = parts.toMutableList()
                            .also { it[6] = (hopCount + 1).toString() }
                            .joinToString(":")
                    )
                    allPeerIps.filter { it != fromIp }.forEach {
                        connectionManager.sendPacket(forwarded, it)
                    }
                }
            }
        }
    }

    // ── RREP ──────────────────────────────────────────────────────────────────

    private suspend fun sendRrep(
        rreqId: String,
        srcId: String,
        destId: String,
        destSeq: Int,
        hopCount: Int,
        toIp: String
    ) {
        val payload = "RREP:$rreqId:$srcId:$destId:$destSeq:$hopCount"
        val rrep = MeshPacket(
            id             = UUID.randomUUID().toString(),
            type           = PacketType.ROUTE_REPLY,
            senderId       = destId,
            senderName     = "",
            senderPhone    = "",
            payload        = payload,
            ttl            = 6,
            timestamp      = System.currentTimeMillis(),
            signature      = "",
            receiverId     = srcId,
            sequenceNumber = destSeq
        )
        connectionManager.sendPacket(rrep, toIp)
        Log.d(TAG, "RREP sent rreqId=$rreqId dest=$destId → $toIp")
    }

    private suspend fun handleRrep(
        packet: MeshPacket,
        fromIp: String,
        myId: String,
        allPeerIps: List<String>
    ) {
        // RREP:<rreqId>:<srcId>:<destId>:<destSeq>:<hopCount>
        val parts = packet.payload.split(":")
        if (parts.size < 6) return
        val srcId    = parts[2]
        val destId   = parts[3]
        val destSeq  = parts[4].toIntOrNull() ?: 0
        val hopCount = parts[5].toIntOrNull() ?: 0

        updateRoute(destId, fromIp, hopCount + 1, destSeq)
        Log.d(TAG, "Route learned: $destId via $fromIp (${hopCount + 1} hops)")

        if (srcId == myId) {
            // I was the originator — flush buffered packets
            pendingPackets.remove(destId)?.forEach { (buffered, peers) ->
                forwardUnicast(buffered, myId, packet.sequenceNumber, peers)
            }
        } else {
            // Forward RREP toward the originator
            val route = validRoute(srcId)
            if (route != null) {
                connectionManager.sendPacket(packet.copy(ttl = packet.ttl - 1), route.nextHopIp)
            }
        }
    }

    // ── RERR ──────────────────────────────────────────────────────────────────

    private suspend fun sendRerr(
        unreachableDest: String,
        myId: String,
        peers: List<String>
    ) {
        val rerr = MeshPacket(
            id          = UUID.randomUUID().toString(),
            type        = PacketType.ROUTE_ERROR,
            senderId    = myId,
            senderName  = "",
            senderPhone = "",
            payload     = "RERR:$unreachableDest",
            ttl         = 3,
            timestamp   = System.currentTimeMillis(),
            signature   = "",
            receiverId  = "",
            sequenceNumber = 0
        )
        peers.forEach { connectionManager.sendPacket(rerr, it) }
        Log.w(TAG, "RERR sent for unreachable=$unreachableDest")
    }

    private suspend fun handleRerr(
        packet: MeshPacket,
        myId: String,
        allPeerIps: List<String>
    ) {
        val parts = packet.payload.split(":")
        if (parts.size < 2) return
        val unreachable = parts[1]
        if (routingTable.remove(unreachable) != null) {
            Log.w(TAG, "Route to $unreachable removed via RERR — propagating")
            sendRerr(unreachable, myId, allPeerIps)
        }
    }

    // ── Routing table helpers ─────────────────────────────────────────────────

    private fun updateRoute(destId: String, nextHopIp: String, hopCount: Int, seq: Int) {
        val existing = routingTable[destId]
        // Accept if: no existing route, or seq is fresher, or same seq with fewer hops
        if (existing == null ||
            seq > existing.sequenceNumber ||
            (seq == existing.sequenceNumber && hopCount < existing.hopCount)) {
            routingTable[destId] = RouteEntry(
                nextHopIp      = nextHopIp,
                hopCount       = hopCount,
                sequenceNumber = seq,
                expiryMs       = System.currentTimeMillis() + ROUTE_LIFETIME_MS
            )
        }
    }

    private fun validRoute(destId: String): RouteEntry? {
        val entry = routingTable[destId] ?: return null
        if (System.currentTimeMillis() > entry.expiryMs) {
            routingTable.remove(destId)
            return null
        }
        return entry
    }

    companion object {
        private const val ROUTE_LIFETIME_MS = 30_000L   // 30 s per RFC 3561 §10
    }
}