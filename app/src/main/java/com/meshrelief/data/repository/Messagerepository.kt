package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.MessageDao
import com.meshrelief.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(private val dao: MessageDao) {

    fun getAllMessages(): Flow<List<MessageEntity>> = dao.getAllMessages()

    // Issue #3 — group chat feed
    fun getGroupMessages(): Flow<List<MessageEntity>> = dao.getGroupMessages()

    // Issue #3 — P2P thread for a specific peer pair
    fun getP2pMessages(myDeviceId: String, peerId: String): Flow<List<MessageEntity>> =
        dao.getP2pMessages(myDeviceId, peerId)

    fun getMessagesWithPeer(peerId: String): Flow<List<MessageEntity>> =
        dao.getMessagesWithPeer(peerId)

    suspend fun save(message: MessageEntity) = dao.insert(message)

    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)
}