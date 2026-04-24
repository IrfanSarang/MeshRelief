package com.meshrelief.mesh.wifi

import android.util.Log
import com.meshrelief.core.util.Constants
import com.meshrelief.mesh.protocol.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SocketServer
 *
 * Listens on [Constants.WIFI_DIRECT_PORT] (8888) for incoming TCP connections.
 * Each accepted connection is read as a single JSON-serialized [MeshPacket],
 * which is then emitted to [incomingPackets] as a [Pair] of the packet and the
 * source IP address. The source IP is required by [MeshRouter] to send ACK
 * replies back to the originating peer (MISSING 3).
 *
 * Lifecycle:
 *  - Call [start] once the P2P group is formed.
 *  - Call [stop] when the group is torn down or the app moves to background.
 */
@Singleton
class SocketServer @Inject constructor() {

    private val TAG = "SocketServer"

    // ── Coroutine scope for all socket work ───────────────────────────────
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    // ── JSON deserializer ─────────────────────────────────────────────────
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Incoming packet stream ────────────────────────────────────────────
    /**
     * Emits every [MeshPacket] received over the wire paired with the source
     * IP address of the sending peer.
     *
     * MISSING 3: Exposing the source IP alongside the packet allows MeshRouter
     * to address ACK replies directly back to the sender without a separate
     * registry lookup — particularly important for peers not yet in the
     * full peerIpRegistry (e.g. first message before handshake completes).
     */
    private val _incomingPackets = MutableSharedFlow<Pair<MeshPacket, String>>(extraBufferCapacity = 32)
    val incomingPackets: SharedFlow<Pair<MeshPacket, String>> = _incomingPackets.asSharedFlow()

    // ── Start ─────────────────────────────────────────────────────────────

    fun start() {
        if (acceptJob?.isActive == true) {
            Log.d(TAG, "Server already running — ignoring start().")
            return
        }

        acceptJob = scope.launch {
            try {
                serverSocket = ServerSocket(Constants.WIFI_DIRECT_PORT)
                Log.d(TAG, "Listening on port ${Constants.WIFI_DIRECT_PORT}")

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Accepted connection from ${client.inetAddress.hostAddress}")
                    launch { handleClient(client) }
                }
            } catch (e: SocketException) {
                Log.d(TAG, "ServerSocket closed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in accept loop", e)
            }
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────

    fun stop() {
        supervisorJob.cancel()
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "SocketServer stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ServerSocket: ${e.message}")
        }
    }

    // ── Per-connection handler ────────────────────────────────────────────

    private suspend fun handleClient(client: java.net.Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 5_000

                val clientIp = socket.inetAddress.hostAddress ?: return

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val rawJson = reader.readLine()

                if (rawJson.isNullOrBlank()) {
                    Log.w(TAG, "Received empty payload from $clientIp — discarding.")
                    return
                }

                val packet: MeshPacket = json.decodeFromString(rawJson)

                _peerIpRegistry[packet.senderId] = clientIp
                Log.d(TAG, "Registered peer ${packet.senderId} → $clientIp")
                Log.d(TAG, "Received packet id=${packet.id} type=${packet.type} from $clientIp")

                // MISSING 3: emit packet WITH source IP so MeshRouter can ACK
                _incomingPackets.emit(Pair(packet, clientIp))
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.w(TAG, "Malformed MeshPacket JSON: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        }
    }

    // ── Peer IP registry ──────────────────────────────────────────────────────
    private val _peerIpRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    val peerIpRegistry: Map<String, String> get() = _peerIpRegistry

    fun registerPeer(deviceId: String, ip: String) {
        _peerIpRegistry[deviceId] = ip
        Log.d(TAG, "Peer registered: $deviceId → $ip")
    }

    fun unregisterPeer(deviceId: String) {
        _peerIpRegistry.remove(deviceId)
        Log.d(TAG, "Peer unregistered: $deviceId")
    }
}