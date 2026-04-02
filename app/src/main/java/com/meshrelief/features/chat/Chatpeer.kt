package com.meshrelief.features.chat

data class ChatPeer(
    val deviceId: String,
    val name: String,
    val idSuffix: String,         // last 4 digits
    val triageColor: String,      // "green" | "yellow" | "red" | "black"
    val hopCount: Int,
    val unreadCount: Int = 0
)