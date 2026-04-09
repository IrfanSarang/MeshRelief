package com.meshrelief.features.chat

import com.meshrelief.data.db.entity.MessageEntity

// ── Constants ─────────────────────────────────────────────────────────────────

/** receiverId stored in Room for group messages */
const val GROUP_RECEIVER_ID = "GROUP"

/** type tag stored in Room for text messages */
const val MSG_TYPE_TEXT = "TEXT_MESSAGE"

/** type tag stored in Room for system/join events */
const val MSG_TYPE_SYSTEM = "SYSTEM"

// ── MessageEntity → ChatMessage ───────────────────────────────────────────────

/**
 * Maps a Room [MessageEntity] to the UI model [ChatMessage].
 *
 * @param myDeviceId  The local device's full device ID — used to mark
 *                    outgoing messages as [ChatMessage.isOutgoing].
 */
fun MessageEntity.toChatMessage(myDeviceId: String): ChatMessage = ChatMessage(
    id            = id,
    senderName    = senderName,
    // last 4 chars of senderId acts as the visible suffix shown in the UI
    senderIdSuffix = senderId.takeLast(4),
    text          = content,
    timestampMs   = timestamp,
    hopCount      = hopCount,
    isOutgoing    = senderId == myDeviceId,
    isSystemMessage = type == MSG_TYPE_SYSTEM
)

// ── ChatMessage → MessageEntity ───────────────────────────────────────────────

/**
 * Converts a [ChatMessage] into a [MessageEntity] ready for Room insertion.
 *
 * @param senderId   Full device ID of the sender.
 * @param receiverId Full device ID of the receiver, or [GROUP_RECEIVER_ID].
 * @param type       [MSG_TYPE_TEXT] or [MSG_TYPE_SYSTEM].
 */
fun ChatMessage.toEntity(
    senderId: String,
    receiverId: String,
    type: String = MSG_TYPE_TEXT
): MessageEntity = MessageEntity(
    id         = id,
    senderId   = senderId,
    senderName = senderName,
    receiverId = receiverId,
    content    = text,
    type       = type,
    timestamp  = timestampMs,
    hopCount   = hopCount,
    isRead     = isOutgoing   // outgoing messages are implicitly "read"
)