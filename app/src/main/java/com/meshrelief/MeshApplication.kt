package com.meshrelief

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.meshrelief.service.MeshForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val intent = Intent(this, MeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "mesh_channel",
                "Mesh Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the mesh network alive in the background."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}