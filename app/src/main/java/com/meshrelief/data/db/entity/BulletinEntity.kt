package com.meshrelief.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bulletins")
data class BulletinEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val type: String,
    val content: String,
    val timestamp: Long,
    val relayCount: Int = 0
)