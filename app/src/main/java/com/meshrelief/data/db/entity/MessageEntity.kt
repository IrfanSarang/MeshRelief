package com.meshrelief.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val content: String,
    val type: String,
    val timestamp: Long,
    val hopCount: Int = 0,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false   // MISSING 3: flipped to true when ACK arrives
)