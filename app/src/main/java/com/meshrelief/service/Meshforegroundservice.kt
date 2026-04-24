package com.meshrelief.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.meshrelief.MainActivity
import com.meshrelief.R
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.core.location.LocationProvider
import com.meshrelief.core.util.Constants
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.PeerRepository
import com.meshrelief.data.repository.SOSRepository
import com.meshrelief.mesh.bluetooth.BluetoothMeshManager
import com.meshrelief.mesh.protocol.HeadcountResponsePayload
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.StatusPayload
import com.meshrelief.mesh.protocol.decodeHandshake
import com.meshrelief.mesh.protocol.decodeStatus
import com.meshrelief.mesh.protocol.encode
import com.meshrelief.mesh.routing.MeshRouter
import com.meshrelief.mesh.wifi.ConnectionManager
import com.meshrelief.mesh.wifi.SocketServer
import com.meshrelief.mesh.wifi.WifiDirectManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MeshForegroundService : Service() {

    @Inject lateinit var sosRepository       : SOSRepository
    @Inject lateinit var wifiDirectManager   : WifiDirectManager
    @Inject lateinit var socketServer        : SocketServer
    @Inject lateinit var meshRouter          : MeshRouter
    @Inject lateinit var appEventBus         : AppEventBus
    @Inject lateinit var peerRepository      : PeerRepository
    @Inject lateinit var bluetoothMeshManager: BluetoothMeshManager
    @Inject lateinit var userPrefs           : UserPreferences
    @Inject lateinit var locationProvider    : LocationProvider
    @Inject lateinit var connectionManager   : ConnectionManager

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var currentStatusBroadcastJob: Job? = null

    private val TAG = "MeshForegroundService"

    // ── Permission helper ─────────────────────────────────────────────────

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // Wi-Fi Direct is always safe to initialize (no dangerous runtime permission needed)
        wifiDirectManager.initialize()

        // Only initialize BT if the user has granted the permissions
        if (hasBluetoothPermissions()) {
            bluetoothMeshManager.initialize()
        } else {
            Log.w(TAG, "Bluetooth permissions not granted — BT mesh disabled for this session.")
        }

        // ── BT fallback: kick in after 30s of no Wi-Fi Direct peers ──────
        serviceScope.launch {
            var emptyStart = 0L
            wifiDirectManager.connectedPeerIPs.collect { ips ->
                if (ips.isEmpty()) {
                    if (emptyStart == 0L) emptyStart = System.currentTimeMillis()
                    val waitedMs = System.currentTimeMillis() - emptyStart
                    if (waitedMs > 30_000L && hasBluetoothPermissions()) {
                        Log.w(TAG, "Wi-Fi Direct empty for ${waitedMs}ms — starting BT fallback.")
                        bluetoothMeshManager.discoverPeers()
                    }
                } else {
                    emptyStart = 0L
                }
            }
        }

        // ── Socket server startup ─────────────────────────────────────────
        serviceScope.launch {
            socketServer.start()
        }

        // ── Inbound packet routing ────────────────────────────────────────
        serviceScope.launch {
            socketServer.incomingPackets.collect { (packet, fromIp) ->
                Log.d(TAG, "Routing packet id=${packet.id} type=${packet.type} from=$fromIp")
                val keyMap = peerRepository.getAllPeersSnapshot()
                    .associate { it.deviceId to it.publicKeyBytes }
                meshRouter.route(
                    packet                 = packet,
                    fromIp                 = fromIp,
                    connectedPeerAddresses = wifiDirectManager.connectedPeerIPs.value,
                    knownPublicKeys        = keyMap
                )
            }
        }

        // ── Periodic DEVICE_STATUS broadcast (battery-saver aware) ───────
        serviceScope.launch {
            userPrefs.batterySaverMode.collect { saverOn ->
                val interval = if (saverOn) {
                    Constants.SAVER_MODE_DISCOVERY_INTERVAL_S * 1000L
                } else {
                    Constants.STATUS_BROADCAST_INTERVAL_MS
                }
                Log.d(TAG, "Battery saver=$saverOn → status broadcast interval=${interval}ms")

                // Cancel previous broadcast loop and restart with new interval
                currentStatusBroadcastJob?.cancel()
                currentStatusBroadcastJob = serviceScope.launch {
                    while (isActive) {
                        delay(interval)
                        broadcastMyStatus()
                    }
                }

                // Also tell WifiDirectManager to throttle discovery when saver is on
                val discoveryInterval = if (saverOn) {
                    Constants.SAVER_MODE_DISCOVERY_INTERVAL_S * 1000L
                } else {
                    0L // 0 = revert to default one-shot discovery
                }
                wifiDirectManager.setDiscoveryInterval(discoveryInterval)
            }
        }

        // ── DEVICE_STATUS collector ───────────────────────────────────────
        serviceScope.launch {
            appEventBus.deviceStatus.collect { packet ->
                try {
                    val status = packet.decodeStatus()
                    peerRepository.updateStatusFromPacket(
                        deviceId = packet.senderId,
                        triage   = status.triage,
                        battery  = status.battery,
                        lastSeen = packet.timestamp
                    )
                    Log.d(TAG, "DEVICE_STATUS applied: sender=${packet.senderId} " +
                            "triage=${status.triage} battery=${status.battery}% " +
                            "lastSeen=${packet.timestamp}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode DEVICE_STATUS id=${packet.id}: ${e.message}", e)
                }
            }
        }

        // ── HEADCOUNT_PING collector ──────────────────────────────────────
        serviceScope.launch {
            appEventBus.headcountPing.collect { pingPacket ->
                try {
                    val deviceId = userPrefs.userDeviceId.first()
                    val name     = userPrefs.userName.first()
                    val triage   = userPrefs.myTriageStatus.first()
                    val loc      = locationProvider.getLastKnownLocation()

                    val responsePayload = HeadcountResponsePayload(
                        deviceId = deviceId,
                        name     = name,
                        triage   = triage,
                        lat      = loc?.latitude  ?: 0.0,
                        lng      = loc?.longitude ?: 0.0
                    ).encode()

                    val response = MeshPacket(
                        id          = UUID.randomUUID().toString(),
                        type        = PacketType.HEADCOUNT_RESPONSE,
                        senderId    = deviceId,
                        senderName  = name,
                        senderPhone = "",
                        payload     = responsePayload,
                        ttl         = Constants.DEFAULT_TTL,
                        timestamp   = System.currentTimeMillis(),
                        signature   = ""
                    )

                    connectionManager.broadcastPacket(response)
                    Log.d(TAG, "HEADCOUNT_RESPONSE sent by deviceId=$deviceId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle HEADCOUNT_PING: ${e.message}", e)
                }
            }
        }

        // ── PEER_HANDSHAKE collector ──────────────────────────────────────
        serviceScope.launch {
            appEventBus.peerHandshake.collect { packet ->
                try {
                    val handshake = packet.decodeHandshake()
                    peerRepository.upsert(PeerEntity(
                        deviceId       = handshake.deviceId,
                        name           = handshake.name,
                        phone4         = handshake.phone4,
                        publicKeyBytes = Base64.decode(handshake.publicKeyBase64, Base64.NO_WRAP),
                        triageStatus   = handshake.triage,
                        battery        = handshake.battery,
                        lat            = handshake.lat,
                        lng            = handshake.lng,
                        lastSeen       = System.currentTimeMillis()
                    ))
                    Log.d(TAG, "PEER_HANDSHAKE upserted: ${handshake.deviceId} name=${handshake.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle PEER_HANDSHAKE id=${packet.id}: ${e.message}", e)
                }
            }
        }

        // ── PEER_VERIFY collector ─────────────────────────────────────────
        serviceScope.launch {
            appEventBus.peerVerify.collect { packet ->
                try {
                    peerRepository.setVerified(packet.payload, true)
                    Log.d(TAG, "PEER_VERIFY applied: deviceId=${packet.payload} marked verified=true")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle PEER_VERIFY id=${packet.id}: ${e.message}", e)
                }
            }
        }

        // ── PEER_FLAG collector ───────────────────────────────────────────
        serviceScope.launch {
            appEventBus.peerFlag.collect { packet ->
                try {
                    peerRepository.setFlagged(packet.payload, true)
                    Log.d(TAG, "PEER_FLAG applied: deviceId=${packet.payload} marked flagged=true")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle PEER_FLAG id=${packet.id}: ${e.message}", e)
                }
            }
        }

        // ── SOS_CANCEL collector ──────────────────────────────────────────
        serviceScope.launch {
            appEventBus.sosCancel.collect { packet ->
                try {
                    sosRepository.markResolved(packet.payload)
                    Log.d(TAG, "SOS_CANCEL applied: resolved sosId=${packet.payload}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle SOS_CANCEL id=${packet.id}: ${e.message}", e)
                }
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    // ── Periodic status broadcast ─────────────────────────────────────────

    private suspend fun broadcastMyStatus() {
        try {
            val deviceId = userPrefs.userDeviceId.first()
            val name     = userPrefs.userName.first()
            val triage   = userPrefs.myTriageStatus.first()
            val battery  = userPrefs.myBatteryLevel.first()

            val payload = StatusPayload(
                triage  = triage,
                battery = battery,
                message = ""
            ).encode()

            val packet = MeshPacket(
                id          = UUID.randomUUID().toString(),
                type        = PacketType.DEVICE_STATUS,
                senderId    = deviceId,
                senderName  = name,
                senderPhone = "",
                payload     = payload,
                ttl         = Constants.DEFAULT_TTL,
                timestamp   = System.currentTimeMillis(),
                signature   = ""
            )

            connectionManager.broadcastPacket(packet)
            Log.d(TAG, "Periodic DEVICE_STATUS broadcast: deviceId=$deviceId triage=$triage battery=$battery%")
        } catch (e: Exception) {
            Log.e(TAG, "broadcastMyStatus failed: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        currentStatusBroadcastJob?.cancel()
        socketServer.stop()
        wifiDirectManager.shutdown()
        bluetoothMeshManager.shutdown()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mesh Active")
            .setContentText("MeshRelief is maintaining your mesh network connection.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID              = "mesh_channel"
        private const val NOTIFICATION_ID = 1001
    }
}