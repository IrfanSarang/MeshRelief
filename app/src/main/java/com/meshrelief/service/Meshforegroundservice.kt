package com.meshrelief.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        wifiDirectManager.initialize()

        serviceScope.launch {
            socketServer.start()
        }

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