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
 * which is then emitted to [incomingPackets].
 *
 * Lifecycle:
 *  - Call [start] once the P2P group is formed.
 *  - Call [stop] when the group is torn down or the app moves to background.
 */
@Singleton
class SocketServer @Inject constructor() {

    private val TAG = "SocketServer"

    // ── Coroutine scope for all socket work ───────────────────────────────
    // SupervisorJob: one failed accept() doesn't cancel the whole server loop.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    // ── JSON deserializer ─────────────────────────────────────────────────
    private val json = Json {
        ignoreUnknownKeys = true   // forward-compatible with future fields
        isLenient = true
    }

    // ── Incoming packet stream ────────────────────────────────────────────
    /**
     * Emits every [MeshPacket] received over the wire.
     * Collectors (e.g. a mesh routing ViewModel) subscribe here.
     * Buffer of 32 ensures fast senders don't block the accept loop.
     */
    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 32)
    val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    // ── Start ─────────────────────────────────────────────────────────────

    /**
     * Opens a [ServerSocket] and starts the accept loop in a background
     * coroutine. Safe to call multiple times — will no-op if already running.
     */
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
                    // Blocks until a client connects
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Accepted connection from ${client.inetAddress.hostAddress}")

                    // Handle each connection in its own child coroutine so the
                    // accept loop is never blocked by a slow/broken client.
                    launch {
                        handleClient(client)
                    }
                }
            } catch (e: SocketException) {
                // ServerSocket.close() throws SocketException — expected on stop()
                Log.d(TAG, "ServerSocket closed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in accept loop", e)
            }
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────

    /**
     * Closes the [ServerSocket], which unblocks [accept] and terminates
     * the accept loop. The coroutine scope itself is kept alive so [start]
     * can be called again later.
     */
    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "SocketServer stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ServerSocket: ${e.message}")
        }
        scope.launch { acceptJob?.cancelAndJoin() }
    }

    // ── Per-connection handler ────────────────────────────────────────────

    private suspend fun handleClient(client: java.net.Socket) {
        try {
            client.use { socket ->
                val clientIp = socket.inetAddress.hostAddress ?: return

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val rawJson = reader.readLine()

                if (rawJson.isNullOrBlank()) {
                    Log.w(TAG, "Received empty payload from $clientIp — discarding.")
                    return
                }

                val packet: MeshPacket = json.decodeFromString(rawJson)

                // ── Store IP as soon as we know the sender's deviceId ─────────
                _peerIpRegistry[packet.senderId] = clientIp
                Log.d(TAG, "Registered peer ${packet.senderId} → $clientIp")

                Log.d(TAG, "Received packet id=${packet.id} type=${packet.type} from $clientIp")
                _incomingPackets.emit(packet)
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.w(TAG, "Malformed MeshPacket JSON: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        }
    }

    // ── Peer IP registry ──────────────────────────────────────────────────────
    // deviceId → IP string, updated on every accepted connection.
    // ConcurrentHashMap keeps this safe across coroutines without locking.
    private val _peerIpRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Read-only snapshot for ConnectionManager to address specific peers. */
    val peerIpRegistry: Map<String, String> get() = _peerIpRegistry

    /** Manually register a deviceId↔IP pair (e.g. from a handshake packet). */
    fun registerPeer(deviceId: String, ip: String) {
        _peerIpRegistry[deviceId] = ip
        Log.d(TAG, "Peer registered: $deviceId → $ip")
    }

    /** Remove a peer when they disconnect. */
    fun unregisterPeer(deviceId: String) {
        _peerIpRegistry.remove(deviceId)
        Log.d(TAG, "Peer unregistered: $deviceId")
    }
}