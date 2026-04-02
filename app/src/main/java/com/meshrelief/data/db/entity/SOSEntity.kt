package com.meshrelief.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sos_alerts")
data class SOSEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val senderPhone4: String,
    val lat: Double,
    val lng: Double,
    val triageStatus: String,
    val message: String = "",
    val timestamp: Long,
    val resolved: Boolean = false,
    val hopCount: Int = 0
)