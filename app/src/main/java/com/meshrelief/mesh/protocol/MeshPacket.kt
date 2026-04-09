package com.meshrelief.mesh.protocol

import kotlinx.serialization.Serializable

@Serializable
data class MeshPacket(
    val id: String,
    val type: PacketType,
    val senderId: String,
    val senderName: String,
    val senderPhone: String,
    val payload: String,
    val ttl: Int,
    val timestamp: Long,
    val signature: String,
)