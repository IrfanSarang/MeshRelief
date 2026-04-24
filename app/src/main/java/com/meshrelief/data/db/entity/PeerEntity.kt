package com.meshrelief.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val phone4: String,
    val verified: Boolean = false,
    val flagged: Boolean = false,
    val triageStatus: String = "NONE",
    val battery: Int = 100,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val lastSeen: Long = 0L,
    val hopCount: Int = 0,
    val linkQuality: Int = 0,
    val publicKeyBytes: ByteArray = byteArrayOf()
)