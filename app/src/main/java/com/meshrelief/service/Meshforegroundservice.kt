package com.meshrelief.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshrelief.MainActivity
import com.meshrelief.R
import com.meshrelief.mesh.routing.MeshRouter
import com.meshrelief.mesh.wifi.SocketServer
import com.meshrelief.mesh.wifi.WifiDirectManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MeshForegroundService : Service() {

    @Inject lateinit var wifiDirectManager: WifiDirectManager
    @Inject lateinit var socketServer: SocketServer
    @Inject lateinit var meshRouter: MeshRouter

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val TAG = "MeshForegroundService"

    override fun onCreate() {
        super.onCreate()
        wifiDirectManager.initialize()

        serviceScope.launch {
            socketServer.start()
        }

        // ── ISSUE #1 FIX: Wire SocketServer → MeshRouter pipeline ────────
        //
        // Collect every packet that arrives on the TCP socket and hand it
        // to MeshRouter, which will:
        //   a) deduplicate (seenPacketIds cache)
        //   b) verify signature if sender is known (Issue #15)
        //   c) dispatch to AppEventBus (local UI consumers)
        //   d) re-broadcast with TTL-1 to all connected peers
        //
        // connectedPeerIPs is read at route()-time so it always reflects
        // the current live peer list — no stale captures.
        serviceScope.launch {
            socketServer.incomingPackets.collect { packet ->
                Log.d(TAG, "Routing packet id=${packet.id} type=${packet.type}")
                meshRouter.route(
                    packet                = packet,
                    connectedPeerAddresses = wifiDirectManager.connectedPeerIPs.value,
                    knownPublicKeys       = emptyMap() // Phase 2: populate from PeerRepository
                )
            }
        }
        // ─────────────────────────────────────────────────────────────────

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socketServer.stop()
        wifiDirectManager.shutdown()
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
        const val CHANNEL_ID = "mesh_channel"
        private const val NOTIFICATION_ID = 1001
    }
}