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
import android.util.Base64
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.data.preferences.UserPreferences
import java.util.UUID
import com.meshrelief.mesh.protocol.HandshakePayload
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.encode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Transport abstraction — implemented by WifiDirectManager (default) and
 * optionally by BluetoothMeshManager when used as fallback.
 */
interface MeshTransport {
    val connectedPeerIPs: StateFlow<List<String>>
    fun isGroupOwner(): Boolean
    fun getGroupOwnerAddress(): String?
}

/**
 * Result returned by [ConnectionManager.broadcastPacket].
 *
 * @param results       Per-peer IP → success flag.
 * @param goUnreachable True when this device is a client and the send to
 *                      the Group Owner failed, so MeshRouter can buffer
 *                      packets and trigger ReconnectManager.
 */
data class BroadcastResult(
    val results: Map<String, Boolean>,
    val goUnreachable: Boolean
)

/**
 * ConnectionManager
 *
 * Serializes a [MeshPacket] to JSON and writes it to a peer's TCP socket
 * at [Constants.WIFI_DIRECT_PORT] (8888).
 *
 * Transport is abstracted via [MeshTransport] so the same send/broadcast
 * logic works over Wi-Fi Direct or Bluetooth without modification.
 *
 * All socket operations run on [Dispatchers.IO].
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val transport       : MeshTransport,
    private val socketServer    : SocketServer,
    private val deviceIdentity  : DeviceIdentity,
    private val userPrefs       : UserPreferences,
    private val locationProvider: LocationProvider
) {

    private val TAG = "ConnectionManager"

    private val json = Json {
        encodeDefaults = true
    }

    // ── Send ──────────────────────────────────────────────────────────────

    /**
     * Serializes [packet] to a single line of JSON, signs it, and sends it
     * to [peerAddress]:[Constants.WIFI_DIRECT_PORT] over TCP.
     *
     * @return `true` if written successfully, `false` on any [IOException].
     */
    suspend fun sendPacket(packet: MeshPacket, peerAddress: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val signedPacket = packet.copy(
                    signature = deviceIdentity.sign(
                        packet.id + packet.senderId + packet.payload
                    )
                )

                Socket(peerAddress, Constants.WIFI_DIRECT_PORT).use { socket ->
                    socket.soTimeout = 5_000
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(json.encodeToString(signedPacket))
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

    // ── Broadcast ─────────────────────────────────────────────────────────

    /**
     * Sends [packet] to every currently-connected peer via [transport].
     *
     * Non-owners forward to the Group Owner only.
     * Group Owners fan out to every connected client.
     *
     * Returns [BroadcastResult] with per-peer success flags and a
     * [BroadcastResult.goUnreachable] flag for MeshRouter buffering.
     */
    suspend fun broadcastPacket(packet: MeshPacket): BroadcastResult =
        withContext(Dispatchers.IO) {
            val connectedIPs = transport.connectedPeerIPs.value
            if (connectedIPs.isEmpty()) {
                Log.d(TAG, "broadcastPacket: no connected peers.")
                return@withContext BroadcastResult(emptyMap(), goUnreachable = false)
            }

            val results = mutableMapOf<String, Boolean>()

            if (!transport.isGroupOwner()) {
                // ── Non-owner: forward to Group Owner only ────────────────
                val groupOwnerIp = transport.getGroupOwnerAddress()
                if (groupOwnerIp == null) {
                    Log.w(TAG, "broadcastPacket: group owner address unknown — skipping.")
                    return@withContext BroadcastResult(emptyMap(), goUnreachable = true)
                }
                val success = sendPacket(packet, groupOwnerIp)
                results[groupOwnerIp] = success

                if (!success) {
                    Log.w(TAG, "broadcastPacket: GO at $groupOwnerIp unreachable.")
                }

                return@withContext BroadcastResult(results, goUnreachable = !success)

            } else {
                // ── Group Owner: fan out to every connected client ─────────
                val sendTargets = (connectedIPs + socketServer.peerIpRegistry.values).distinct()
                for (ip in sendTargets) {
                    Log.d(TAG, "GO broadcasting to $ip")
                    results[ip] = sendPacket(packet, ip)
                }

                Log.d(TAG, "broadcastPacket done. Results: $results")
                return@withContext BroadcastResult(results, goUnreachable = false)
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────

    fun getPeerIp(deviceId: String): String? =
        socketServer.peerIpRegistry[deviceId]

    suspend fun sendHandshake(peerAddress: String) {
        val deviceId = userPrefs.userDeviceId.first()
        val name     = userPrefs.userName.first()
        val phone    = userPrefs.userPhone.first()
        val triage   = userPrefs.myTriageStatus.first()
        val loc      = locationProvider.getLastKnownLocation()

        val payload = HandshakePayload(
            deviceId        = deviceId,
            name            = name,
            phone4          = if (phone.length >= 4) phone.takeLast(4) else phone,
            publicKeyBase64 = Base64.encodeToString(
                deviceIdentity.getPublicKeyBytes(), Base64.NO_WRAP
            ),
            triage          = triage,
            battery         = 100,
            lat             = loc?.latitude  ?: 0.0,
            lng             = loc?.longitude ?: 0.0
        ).encode()

        val packet = MeshPacket(
            id          = UUID.randomUUID().toString(),
            type        = PacketType.PEER_HANDSHAKE,
            senderId    = deviceId,
            senderName  = name,
            senderPhone = phone,
            payload     = payload,
            ttl         = 1,
            timestamp   = System.currentTimeMillis(),
            signature   = ""
        )
        sendPacket(packet, peerAddress)
    }
}