package com.meshrelief.mesh.wifi

import android.util.Log
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.core.util.Constants
import com.meshrelief.mesh.protocol.MeshPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConnectionManager
 *
 * Responsible for serializing a [MeshPacket] to JSON and writing it to a
 * peer's TCP socket at [Constants.WIFI_DIRECT_PORT] (8888).
 *
 * All socket operations run on [Dispatchers.IO].
 * Returns `true` on success, `false` on any [IOException].
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val socketServer: SocketServer,
    private val deviceIdentity: DeviceIdentity          // Issue #15
) {

    private val TAG = "ConnectionManager"

    // ── JSON serializer ───────────────────────────────────────────────────
    private val json = Json {
        encodeDefaults = true   // include fields with default values
    }

    // ── Send ──────────────────────────────────────────────────────────────

    /**
     * Serializes [packet] to a single line of JSON and sends it to
     * [peerAddress]:[Constants.WIFI_DIRECT_PORT] over TCP.
     *
     * Issue #15: signs packet before sending so the receiver can verify
     * the packet was not tampered with in transit.
     *
     * @param packet       The [MeshPacket] to transmit.
     * @param peerAddress  IPv4 address of the destination peer (e.g. "192.168.49.1").
     * @return `true` if the packet was written successfully, `false` otherwise.
     */
    suspend fun sendPacket(packet: MeshPacket, peerAddress: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Issue #15 — sign before sending
                val signedPacket = packet.copy(
                    signature = deviceIdentity.sign(
                        packet.id + packet.senderId + packet.payload
                    )
                )

                Socket(peerAddress, Constants.WIFI_DIRECT_PORT).use { socket ->
                    // Set a reasonable write timeout — avoids hanging indefinitely
                    socket.soTimeout = 5_000

                    val writer = PrintWriter(socket.getOutputStream(), /* autoFlush= */ true)
                    val rawJson = json.encodeToString(signedPacket)

                    // SocketServer.handleClient reads one line — must end with \n
                    writer.println(rawJson)

                    Log.d(TAG, "Sent signed packet id=${signedPacket.id} to $peerAddress")
                    true
                }
            } catch (e: IOException) {
                Log.e(TAG, "sendPacket failed to $peerAddress: ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending packet to $peerAddress", e)
                false
            }
        }

    // ── Convenience broadcast ─────────────────────────────────────────────

    /**
     * Sends [packet] to every currently-discovered peer in parallel.
     * Collects and returns the per-peer success flags.
     *
     * Note: this is a best-effort broadcast — partial failures are logged
     * but do not throw. The TTL / routing logic in higher layers handles
     * retransmission policy.
     */
    suspend fun broadcastPacket(packet: MeshPacket): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val peers = wifiDirectManager.peerList.value
            if (peers.isEmpty()) {
                Log.d(TAG, "broadcastPacket: no peers in range.")
                return@withContext emptyMap()
            }

            val results = mutableMapOf<String, Boolean>()

            if (!wifiDirectManager.isGroupOwner()) {
                // ── Non-owner: forward to group owner only ────────────────────
                val groupOwnerIp = wifiDirectManager.getGroupOwnerAddress()
                if (groupOwnerIp == null) {
                    Log.w(TAG, "broadcastPacket: group owner address unknown — skipping.")
                    return@withContext emptyMap()
                }
                results[groupOwnerIp] = sendPacket(packet, groupOwnerIp)

            } else {
                // ── Group Owner: send to every connected client individually ──
                val registry = socketServer.peerIpRegistry
                if (registry.isEmpty()) {
                    Log.w(TAG, "broadcastPacket: GO has no peer IPs in registry yet.")
                    return@withContext emptyMap()
                }
                for ((deviceId, ip) in registry) {
                    Log.d(TAG, "GO broadcasting to $deviceId @ $ip")
                    results[ip] = sendPacket(packet, ip)
                }
            }

            Log.d(TAG, "broadcastPacket done. Results: $results")
            results
        }

    fun getPeerIp(deviceId: String): String? {
        return socketServer.peerIpRegistry[deviceId]
    }
}