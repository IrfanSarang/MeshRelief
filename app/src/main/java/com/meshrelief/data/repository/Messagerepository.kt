package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.MessageDao
import com.meshrelief.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(private val dao: MessageDao) {

    fun getAllMessages(): Flow<List<MessageEntity>> = dao.getAllMessages()

    fun getGroupMessages(): Flow<List<MessageEntity>> = dao.getGroupMessages()

    fun getP2pMessages(myDeviceId: String, peerId: String): Flow<List<MessageEntity>> =
        dao.getP2pMessages(myDeviceId, peerId)

    fun getMessagesWithPeer(peerId: String): Flow<List<MessageEntity>> =
        dao.getMessagesWithPeer(peerId)

    suspend fun save(message: MessageEntity) = dao.insert(message)

    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)

    // MISSING 3: called by ChatViewModel when an ACK packet arrives for a
    // previously sent message — flips isDelivered so the UI can show a tick.
    suspend fun markDelivered(messageId: String) = dao.markDelivered(messageId)
}