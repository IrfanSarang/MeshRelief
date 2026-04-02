package com.meshrelief.features.chat

data class ChatMessage(
    val id: String,
    val senderName: String,
    val senderIdSuffix: String,   // last 4 digits of device ID
    val text: String,
    val timestampMs: Long,
    val hopCount: Int,
    val isOutgoing: Boolean,
    val isSystemMessage: Boolean = false
)